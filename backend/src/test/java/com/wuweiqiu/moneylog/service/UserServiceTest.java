package com.wuweiqiu.moneylog.service;

import com.wuweiqiu.moneylog.common.JwtUtil;
import com.wuweiqiu.moneylog.dto.LoginRequest;
import com.wuweiqiu.moneylog.dto.RegisterRequest;
import com.wuweiqiu.moneylog.vo.LoginResult;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * ============================================================
 *  用户模块的自动化测试 —— 这是这个项目的**第一个测试类**
 * ============================================================
 *
 *  【为什么要写测试？用 curl 手动试不行吗？】
 *   手动试的问题：试过一次就忘了。明天改了点代码，你不知道有没有弄坏旧功能。
 *   测试的好处：把"应该发生什么"用代码写下来，以后一条命令就能全跑一遍。
 *   这叫"回归测试" —— 保证新改动没有破坏老功能。
 *
 *  【@SpringBootTest 是什么？】
 *   它让测试启动一个真实的 Spring 容器（跟我们 mvn spring-boot:run 是同一套东西），
 *   所以 @Autowired 能正常工作，数据库也是真的。
 *   （默认不会占用 8080 端口，所以可以跟正在运行的后端同时跑，不冲突。）
 *
 *  【运行方式】
 *   在 backend 目录下执行：mvn test -Dtest=UserServiceTest
 */
@SpringBootTest
class UserServiceTest {

    @Autowired
    private UserService userService;

    @Autowired
    private JwtUtil jwtUtil;

    // JdbcTemplate 是 Spring 提供的"直接执行 SQL"的小工具。
    // 测试里需要直接清理数据时用它最方便 —— 不用为了测试专门写一个 Mapper 方法。
    // （它是 mybatis-spring-boot-starter 的传递依赖带来的，配置都不用改）
    @Autowired
    private JdbcTemplate jdbcTemplate;

    /**
     * 固定的测试用户名。
     *
     * ⚠️【Day10 改进：这是一次真实的教训】
     *   最初的写法是 `"junit_" + System.currentTimeMillis()`，用时间戳生成随机用户名，
     *   本意是"避免重名导致第二次跑测试失败"。
     *
     *   听起来合理，但代价是：**每跑一次测试，就在数据库里永久留下一个垃圾账号**。
     *   跑十次就是十个账号。我昨天跑了几轮，数据库里就多出了 `junit_3441164`、
     *   `junit_3495751` 这种记录 —— 而它们**永远不会有人去清理**。
     *
     *   【正确做法：测试要自己收拾现场】
     *     · 跑之前先清一次（防止上次异常退出留下的残留）
     *     · 跑完再清一次（见下面的 @BeforeEach / @AfterEach）
     *
     *   这样测试可以无限次重复执行，数据库始终干净 ——
     *   这叫"测试的幂等性"，是自动化测试的基本要求。
     */
    private static final String NAME = "usvc_test_user";   // 13 个字符，符合"3-20 位"规则

    private static final String SECRET = "abc123456";

    @BeforeEach
    void 清掉可能残留的测试账号() {
        jdbcTemplate.update("DELETE FROM `user` WHERE username = ?", NAME);
    }

    @AfterEach
    void 收拾现场() {
        jdbcTemplate.update("DELETE FROM `user` WHERE username = ?", NAME);
    }

    @Test
    void 完整走一遍_注册_登录_验签_以及各种异常() {
        // ---------- 1. 注册 ----------
        RegisterRequest reg = new RegisterRequest();
        reg.setUsername(NAME);
        reg.setPassword(SECRET);
        reg.setNickname("自动化测试用户");
        userService.register(reg);
        System.out.println("[测试] 注册成功：" + NAME);

        // ---------- 2. 同一用户名再注册一次 → 必须被拒绝 ----------
        IllegalArgumentException dup = assertThrows(IllegalArgumentException.class,
                () -> userService.register(reg));
        System.out.println("[测试] 重复注册被拒绝：" + dup.getMessage());
        assertTrue(dup.getMessage().contains("已被占用"));

        // ---------- 3. 用正确凭据登录 → 必须拿到 token ----------
        LoginResult ok = userService.login(loginReq(NAME, SECRET));
        assertNotNull(ok.getToken(), "登录成功时 token 不该是 null");
        assertEquals(NAME, ok.getUsername());
        assertEquals("自动化测试用户", ok.getNickname());
        assertNotNull(ok.getUserId());
        System.out.println("[测试] 登录成功，userId=" + ok.getUserId());
        System.out.println("===JWT-TOKEN-BEGIN===");
        System.out.println(ok.getToken());
        System.out.println("===JWT-TOKEN-END===");

        // ---------- 4. 验签：拿 token 反解出 userId，应该跟登录返回的一致 ----------
        Long parsed = jwtUtil.parseUserId(ok.getToken());
        assertEquals(ok.getUserId(), parsed, "token 里解出的 userId 应该等于登录返回的 userId");
        System.out.println("[测试] 验签通过，从 token 里解出 userId=" + parsed);

        // ---------- 5. 换个密码登录 → 必须被拒绝 ----------
        IllegalArgumentException bad = assertThrows(IllegalArgumentException.class,
                () -> userService.login(loginReq(NAME, SECRET + "-x")));
        assertEquals("用户名或密码错误", bad.getMessage());
        System.out.println("[测试] 错误凭据被拒绝：" + bad.getMessage());

        // ---------- 6. 不存在的用户名 → 提示必须与第 5 步**完全相同** ----------
        IllegalArgumentException nobody = assertThrows(IllegalArgumentException.class,
                () -> userService.login(loginReq("no_such_user_here", SECRET)));
        assertEquals(bad.getMessage(), nobody.getMessage(),
                "两种情况必须返回同一句提示，否则会泄露'哪些用户名存在'");
        System.out.println("[测试] 不存在的用户提示一致（防用户名枚举）");

        // ---------- 7. 篡改过的 token → 验签必须失败（返回 null）----------
        String tampered = ok.getToken().substring(0, ok.getToken().length() - 5) + "AAAAA";
        assertEquals(null, jwtUtil.parseUserIdQuietly(tampered),
                "签名被改过的 token 必须验不过");
        System.out.println("[测试] 篡改过的 token 被识破");
    }

    private LoginRequest loginReq(String username, String pwd) {
        LoginRequest r = new LoginRequest();
        r.setUsername(username);
        r.setPassword(pwd);
        return r;
    }
}
