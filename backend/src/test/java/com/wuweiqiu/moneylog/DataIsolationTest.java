package com.wuweiqiu.moneylog;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.wuweiqiu.moneylog.dto.LoginRequest;
import com.wuweiqiu.moneylog.dto.RegisterRequest;
import com.wuweiqiu.moneylog.service.UserService;
import com.wuweiqiu.moneylog.vo.LoginResult;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * ============================================================
 *  数据隔离测试（Day11）—— 证明"张三看不到李四的账"
 * ============================================================
 *
 *  【为什么必须写成测试，不能"手动点一下看看"？】
 *  因为越权漏洞的特点是：**正常操作时完全看不出来**。
 *  你用 A 登录、看到自己的账户、一切正常 —— 这不能证明隔离生效了。
 *  真正的验证必须是"**用 B 的身份，去访问 A 的数据**"，
 *  而这在页面上根本点不出来（页面压根不显示别人的账户）。
 *
 *  所以这件事**只能靠测试**：绕过界面，直接用 B 的 token 去调接口。
 *  这也再次印证 Day4 那句话：接口是可以被直接调用的。
 *
 *  【这个测试覆盖的 6 件事】
 *    ① B 看不到 A 的账户        （查询隔离）
 *    ② B 改不了 A 的账户        （更新隔离）
 *    ③ B 删不掉 A 的账户        （删除隔离）
 *    ④ B 不能把账记到 A 的账户上 （跨账户越权）
 *    ⑤ 伪造 userId 无效          （Mass Assignment 防护）★ 最重要
 *    ⑥ B 的统计里看不到 A 的账   （统计隔离 —— 最容易漏的地方）
 * ============================================================
 */
@SpringBootTest
@AutoConfigureMockMvc
class DataIsolationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserService userService;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private ObjectMapper objectMapper;

    private static final String USER_A = "iso_user_a";
    private static final String USER_B = "iso_user_b";
    private static final String PWD = "abc123456";
    private static final String ACC_A = "A的微信钱包";
    private static final String ACC_B = "B的支付宝";

    // 测试要自己收拾现场。
    // 【注意删除顺序】bill 和 account 都有外键指向 user，
    //   所以必须先删子表（bill → account），最后才能删 user。
    //   顺序错了会被外键拦住（这就是 Day6 讲的外键约束在起作用）。
    @BeforeEach
    void 清掉残留数据() {
        清理();
    }

    @AfterEach
    void 收拾现场() {
        清理();
    }

    private void 清理() {
        jdbcTemplate.update(
                "DELETE FROM bill WHERE user_id IN (SELECT id FROM `user` WHERE username IN (?, ?))",
                USER_A, USER_B);
        jdbcTemplate.update(
                "DELETE FROM account WHERE user_id IN (SELECT id FROM `user` WHERE username IN (?, ?))",
                USER_A, USER_B);
        jdbcTemplate.update("DELETE FROM `user` WHERE username IN (?, ?)", USER_A, USER_B);
    }

    @Test
    void 数据隔离的六个方面() throws Exception {

        // ============================================================
        //  准备：注册两个用户，各自拿到 token
        // ============================================================
        String tokenA = 注册并登录(USER_A);
        String tokenB = 注册并登录(USER_B);
        Long idA = jdbcTemplate.queryForObject(
                "SELECT id FROM `user` WHERE username = ?", Long.class, USER_A);
        Long idB = jdbcTemplate.queryForObject(
                "SELECT id FROM `user` WHERE username = ?", Long.class, USER_B);
        System.out.println("\n[测试] A(id=" + idA + ") 和 B(id=" + idB + ") 已就绪");

        // A 建一个账户
        建账户(tokenA, ACC_A, 1000);
        Long accIdA = jdbcTemplate.queryForObject(
                "SELECT id FROM account WHERE name = ?", Long.class, ACC_A);
        System.out.println("[测试] A 创建了账户「" + ACC_A + "」，id=" + accIdA);

        // ============================================================
        //  ① 查询隔离：B 看不到 A 的账户
        // ============================================================
        String listA = 取账户列表(tokenA);
        String listB = 取账户列表(tokenB);
        assertTrue(listA.contains(ACC_A), "A 应该能查到自己的账户");
        assertTrue(!listB.contains(ACC_A), "❌ B 竟然看到了 A 的账户！");
        System.out.println("[测试] ✓ ① 查询隔离：B 的列表里没有 A 的账户");

        // ============================================================
        //  ② 更新隔离：B 改不了 A 的账户
        // ============================================================
        mockMvc.perform(put("/api/account/" + accIdA)
                        .header("Authorization", "Bearer " + tokenB)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"被B改掉了\",\"balance\":0}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(1));      // 业务失败
        String nameAfter = jdbcTemplate.queryForObject(
                "SELECT name FROM account WHERE id = ?", String.class, accIdA);
        assertEquals(ACC_A, nameAfter, "❌ A 的账户竟然被 B 改了！");
        System.out.println("[测试] ✓ ② 更新隔离：B 改不动 A 的账户（名字还是「" + nameAfter + "」）");

        // ============================================================
        //  ③ 删除隔离：B 删不掉 A 的账户
        // ============================================================
        mockMvc.perform(delete("/api/account/" + accIdA)
                        .header("Authorization", "Bearer " + tokenB))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(1));
        Integer stillThere = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM account WHERE id = ?", Integer.class, accIdA);
        assertEquals(1, stillThere, "❌ A 的账户竟然被 B 删了！");
        System.out.println("[测试] ✓ ③ 删除隔离：B 删不掉 A 的账户");

        // ============================================================
        //  ④ 跨账户越权：B 不能把账记到 A 的账户上
        // ============================================================
        //  这是很隐蔽的一类攻击：B 只要有 A 的账户 id，
        //  就能让自己的支出从 A 的余额里扣。
        mockMvc.perform(post("/api/bill/create")
                        .header("Authorization", "Bearer " + tokenB)
                        .contentType(MediaType.APPLICATION_JSON)
                        // accountId 用的是 A 的账户
                        .content("{\"accountId\":" + accIdA + ",\"type\":1,\"amount\":9999,"
                                + "\"category\":\"餐饮\",\"billDate\":\"2026-10-15\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(1))
                .andExpect(jsonPath("$.msg").value("所选账户不存在，请重新选择"));
        System.out.println("[测试] ✓ ④ 跨账户越权被拦：B 不能把账记到 A 的账户上");

        // ============================================================
        //  ⑤【最重要】伪造 userId 无效 —— Mass Assignment 防护
        // ============================================================
        //  攻击思路：B 建账户时，在 JSON 里塞一个 "userId"，想让它归属 A。
        //      POST /api/account/create
        //      {"name":"B的伪装账户", "balance":1, "userId": <A的id>}
        //
        //  Account 现在有 userId 字段和 setter，Jackson 确实会把它绑进去！
        //  但 Service 里 `account.setUserId(当前登录用户)` 会强制覆盖掉。
        //
        //  【所以这个测试同时在验证两件事】
        //    · 如果没做强制覆盖 → 这个账户会归到 A 名下（测试挂掉）
        //    · 做了覆盖 → 归到 B 名下（测试通过）
        mockMvc.perform(post("/api/account/create")
                        .header("Authorization", "Bearer " + tokenB)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"B的伪装账户\",\"balance\":1,\"userId\":" + idA + "}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0));

        Long ownerOfFake = jdbcTemplate.queryForObject(
                "SELECT user_id FROM account WHERE name = ?", Long.class, "B的伪装账户");
        assertNotEquals(idA, ownerOfFake, "❌ 攻击成功：账户被写到 A 名下了！");
        assertEquals(idB, ownerOfFake, "归属应该是 B（当前登录用户），强制覆盖没生效？");
        System.out.println("[测试] ✓ ⑤ 伪造 userId 无效：请求体里写 userId=" + idA
                + "，实际归属仍然是 B(id=" + ownerOfFake + ")  ★最关键的一条");

        // ============================================================
        //  ⑥ 统计隔离：B 的统计里看不到 A 的账
        // ============================================================
        //  先让 A 记一笔支出（在自己的账户上，合法）
        记一笔账(tokenA, accIdA, 500);
        String summaryA = 取汇总(tokenA);
        String summaryB = 取汇总(tokenB);
        System.out.println("[测试]   A 的汇总：" + summaryA);
        System.out.println("[测试]   B 的汇总：" + summaryB);

        double expA = objectMapper.readTree(summaryA).get("data").get("totalExpense").asDouble();
        double expB = objectMapper.readTree(summaryB).get("data").get("totalExpense").asDouble();
        assertTrue(expA >= 500, "A 的支出统计里应该有那 500 元");
        assertEquals(0.0, expB, 0.001,
                "❌ B 的统计里出现了别人的支出！统计隔离被漏掉了");
        System.out.println("[测试] ✓ ⑥ 统计隔离：A 的支出 " + expA
                + " 元没有混进 B 的统计（B 是 " + expB + " 元）");

        System.out.println("\n[测试] 六个方面全部通过 ✅\n");
    }

    // ---------------------------------------------------------------
    //  下面是一些小工具方法，避免测试主体里全是重复代码
    // ---------------------------------------------------------------

    private String 注册并登录(String username) {
        RegisterRequest reg = new RegisterRequest();
        reg.setUsername(username);
        reg.setPassword(PWD);
        reg.setNickname(username);
        userService.register(reg);

        LoginRequest login = new LoginRequest();
        login.setUsername(username);
        login.setPassword(PWD);
        LoginResult lr = userService.login(login);
        return lr.getToken();
    }

    private void 建账户(String token, String name, int balance) throws Exception {
        mockMvc.perform(post("/api/account/create")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"" + name + "\",\"balance\":" + balance + "}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0));
    }

    private void 记一笔账(String token, Long accountId, int amount) throws Exception {
        mockMvc.perform(post("/api/bill/create")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"accountId\":" + accountId + ",\"type\":1,\"amount\":" + amount
                                + ",\"category\":\"餐饮\",\"billDate\":\"2026-10-15\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0));
    }

    private String 取账户列表(String token) throws Exception {
        MvcResult result = mockMvc.perform(get("/api/account/list")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andReturn();
        return result.getResponse().getContentAsString();
    }

    private String 取汇总(String token) throws Exception {
        MvcResult result = mockMvc.perform(get("/api/stats/summary")
                        .param("month", "2026-10")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andReturn();
        return result.getResponse().getContentAsString();
    }
}
