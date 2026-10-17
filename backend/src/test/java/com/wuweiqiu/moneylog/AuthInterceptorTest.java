package com.wuweiqiu.moneylog;

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

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * ============================================================
 *  拦截器行为测试（Day10）
 * ============================================================
 *
 *  【为什么要用测试来验证拦截器，而不是用 curl？】
 *   因为拦截器要验证的是一张"行为矩阵"，格子很多：
 *
 *                        不带 token   token 无效   token 有效   是否放行
 *      受保护的接口           401         401         200         -
 *      白名单（登录/注册）    放行        放行        放行         -
 *
 *   用 curl 一个个试，要试 6 次以上，还容易漏；
 *   写成测试，一条命令全跑一遍，而且**以后改代码不会悄悄弄坏它**。
 *
 *  ============================================================
 *  【MockMvc 是什么？】
 *  ============================================================
 *   它是 Spring 提供的"假的 HTTP 客户端 + 假的服务端"。
 *   测试时它不真的开端口、不真的走网络，而是**直接在内存里**把请求
 *   交给 DispatcherServlet 跑一遍完整流程 —— 包括拦截器。
 *   所以它能测出真实的拦截器行为，速度又快，还不占用端口。
 *
 *   @AutoConfigureMockMvc 就是"请帮我把 MockMvc 准备好"。
 *
 *   【关键点】MockMvc 是从 Spring 容器里建的，所以我们在 WebConfig 里
 *   注册的那个拦截器**会真正生效** —— 这正是我们要测的东西。
 * ============================================================
 */
@SpringBootTest
@AutoConfigureMockMvc
class AuthInterceptorTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserService userService;

    /**
     * 用一个固定的测试账号，这样反复跑测试不会在数据库里堆一堆垃圾账号。
     * （第一次跑会注册成功；之后每次跑"注册"都会报"已被占用"，
     *   我们在代码里忽略这个异常，直接登录即可。）
     *
     * ⚠️ 【踩过的坑，值得记一笔】
     *   这里最初写的是 "interceptor_test_user"，一共 21 个字符 ——
     *   而我们的用户名规则是"3-20 位"，所以**注册直接被校验拦下了**。
     *
     *   更糟的是：下面的 catch 一开始把所有 IllegalArgumentException
     *   都当成"用户名已存在"忽略掉了，于是真正的错误（用户名太长）
     *   被悄悄吞掉，最后报出来的是登录时的"用户名或密码错误" ——
     *   一个**完全不相干**的错误信息，排查起来会绕远路。
     *
     *   【两条经验】
     *     ① catch 要"精确"：只吞你预期的那一种失败，别的一律往上抛。
     *        否则异常会被"吃掉"，现场彻底丢失。
     *     ② 报错信息要看到"第一现场"，别被后续的连锁错误带偏。
     */
    private static final String NAME = "itc_test_user";   // 13 个字符，符合"3-20 位"规则
    private static final String PWD = "abc123456";

    @Autowired
    private JdbcTemplate jdbcTemplate;

    // 测试要自己收拾现场：跑之前清一次、跑完再清一次，保证可以无限次重复执行
    @BeforeEach
    void 清掉可能残留的测试账号() {
        jdbcTemplate.update("DELETE FROM `user` WHERE username = ?", NAME);
    }

    @AfterEach
    void 收拾现场() {
        jdbcTemplate.update("DELETE FROM `user` WHERE username = ?", NAME);
    }

    @Test
    void 拦截器行为矩阵() throws Exception {

        // ============================================================
        //  第一组：受保护的接口，没证不让进
        // ============================================================
        System.out.println("\n[测试] ===== 第一组：受保护接口 =====");

        // ---- 1.1 完全不带头 → 401 ----
        mockMvc.perform(get("/api/account/list"))
                .andExpect(status().isUnauthorized())                 // HTTP 401
                .andExpect(jsonPath("$.code").value(1))               // 响应体依然是统一格式
                .andExpect(jsonPath("$.msg").value("请先登录"));
        System.out.println("[测试] ✓ 不带 token 访问 /api/account/list → 401 请先登录");

        // ---- 1.2 带了头，但 token 是乱写的 → 401 ----
        mockMvc.perform(get("/api/account/list").header("Authorization", "Bearer this.is.garbage"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.msg").value("登录已失效，请重新登录"));
        System.out.println("[测试] ✓ 乱写的 token → 401 登录已失效");

        // ---- 1.3 忘了写 "Bearer " 前缀 → 也一样被拦 ----
        //   （真实世界里的高频事故：前端忘了加前缀，或者手动 curl 时漏了）
        mockMvc.perform(get("/api/account/list").header("Authorization", "eyJhbGciOiJIUzI1NiJ9.fake.fake"))
                .andExpect(status().isUnauthorized());
        System.out.println("[测试] ✓ 缺 Bearer 前缀 → 401");

        // ---- 1.4 账单、统计接口同样受保护 ----
        mockMvc.perform(get("/api/bill/list")).andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/stats/monthly")).andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/user/me")).andExpect(status().isUnauthorized());
        System.out.println("[测试] ✓ 账单 / 统计 / me 接口全部受保护");

        // ---- 1.5 写操作（POST/DELETE）也受保护 ----
        mockMvc.perform(post("/api/account/create")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"不该被创建\",\"balance\":1}"))
                .andExpect(status().isUnauthorized());
        System.out.println("[测试] ✓ POST 写操作同样被拦（脏数据进不来）");

        // ============================================================
        //  第二组：白名单 —— 登录之前就要用的接口，绝不能拦
        // ============================================================
        System.out.println("\n[测试] ===== 第二组：白名单放行 =====");

        // ---- 2.1 登录接口：故意不带 token，看它是不是被拦了 ----
        //   如果被拦截，会返回 401；这里我们期望 200 ——
        //   因为拦截器放行了它，请求进到了 Service，被业务校验挡下（200 + code=1）。
        mockMvc.perform(post("/api/user/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isOk())                            // ← 不是 401，说明放行了
                .andExpect(jsonPath("$.code").value(1));
        System.out.println("[测试] ✓ /api/user/login 在放行名单里（返回业务错误而非 401）");

        // ---- 2.2 探活接口 ping ----
        mockMvc.perform(get("/api/ping"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0));
        System.out.println("[测试] ✓ /api/ping 在放行名单里");

        // ============================================================
        //  第三组：拿到真 token 之后，一路畅通
        // ============================================================
        System.out.println("\n[测试] ===== 第三组：持有效通行证 =====");

        // ---- 3.1 准备一个账号 ----
        //   【注意】这里现在是"直接注册"，没有 try-catch 了。
        //   Day10 第一版我写过一个 try-catch 想忽略"用户名已存在"，
        //   结果它把"用户名太长"这个真正的错误也吞掉了，排查时绕了远路（详见类顶部注释的 ⚠️）。
        //   现在改成用 @BeforeEach 先把残留账号清掉，注册必然成功，
        //   于是**不需要 catch** —— 这也印证了那句话：
        //   很多时候"写 catch 兜住"不是解法，把前面的状态弄干净才是。
        RegisterRequest reg = new RegisterRequest();
        reg.setUsername(NAME);
        reg.setPassword(PWD);
        reg.setNickname("拦截器测试账号");
        userService.register(reg);
        System.out.println("[测试] 测试账号已注册：" + NAME);

        // ---- 3.2 登录拿 token ----
        LoginRequest login = new LoginRequest();
        login.setUsername(NAME);
        login.setPassword(PWD);
        LoginResult lr = userService.login(login);
        assertNotNull(lr.getToken());
        String token = lr.getToken();
        System.out.println("[测试] 已取得 token，长度 " + token.length());

        // ---- 3.3 带有效 token → 全部放行 ----
        mockMvc.perform(get("/api/account/list").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0));
        System.out.println("[测试] ✓ 带 token 访问 /api/account/list → 200");

        mockMvc.perform(get("/api/bill/list").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0));
        System.out.println("[测试] ✓ 带 token 访问 /api/bill/list → 200");

        // ---- 3.4 /me：验证 @RequestAttribute 真的把 userId 传到了 Controller ----
        //   这是今天最需要在测试里确认的一点：
        //   拦截器 request.setAttribute("userId", xxx) → Controller @RequestAttribute 收到
        //   如果属性名写错（比如一边写 "userId"、一边写 "userid"），
        //   这里会报 400（参数缺失）或拿到 null —— 肉眼很难发现，但测试一跑就露馅。
        mockMvc.perform(get("/api/user/me").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.username").value(NAME))         // ← 正是这个账号
                .andExpect(jsonPath("$.data.nickname").value("拦截器测试账号"))
                .andExpect(jsonPath("$.data.password").doesNotExist());     // ← 密码绝不能出现
        System.out.println("[测试] ✓ /api/user/me 正确返回身份（@RequestAttribute 传值成功）");
        System.out.println("[测试] ✓ 返回体里确认没有 password 字段");

        System.out.println("\n[测试] 全部通过 ✅\n");
    }
}
