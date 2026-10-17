package com.wuweiqiu.moneylog.controller;

import com.wuweiqiu.moneylog.common.AuthInterceptor;
import com.wuweiqiu.moneylog.common.Result;
import com.wuweiqiu.moneylog.dto.LoginRequest;
import com.wuweiqiu.moneylog.dto.RegisterRequest;
import com.wuweiqiu.moneylog.entity.User;
import com.wuweiqiu.moneylog.service.UserService;
import com.wuweiqiu.moneylog.vo.LoginResult;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 用户接口 —— 今天新增三个。
 *
 *   POST /api/user/register   注册
 *   POST /api/user/login      登录，成功返回 token
 *   GET  /api/user/me         拿 token 换回自己的身份信息（验证 token 有效）
 *
 * 【为什么注册和登录都用 POST 而不是 GET？】
 *   Day3 讲过那条原则：**会改数据的用 POST**。
 *   注册明显是"写"操作。
 *   登录虽然在数据库里没写东西，但它携带**密码**，放 URL 里会被浏览器历史、
 *   服务器日志、代理记录全部留下来 —— 绝对不能。所以必须放请求体里，用 POST。
 */
@RestController
@RequestMapping("/api/user")
public class UserController {

    private final UserService userService;

    // 【Day10 改动】原来这里还有一个 JwtUtil，今天删掉了。
    //   为什么？因为"取 token、验签"这些活已经搬进 AuthInterceptor，
    //   Controller 不再需要自己解析 token。
    //
    //   【顺手记一条经验：删掉不再需要的依赖，和加依赖同样重要。】
    //   留着不用的字段会误导后来的人："这里为啥要 JwtUtil？是不是还有隐式用途？"
    //   代码里"没有多余的东西"，才能让人一眼看明白。
    @Autowired
    public UserController(UserService userService) {
        this.userService = userService;
    }

    /**
     * 注册。
     * 注意参数类型是 RegisterRequest（dto），不是 User —— 这样客户端就算
     * 多传 id、createdAt 也会被忽略（见 RegisterRequest 里的说明）。
     */
    @PostMapping("/register")
    public Result<Void> register(@RequestBody RegisterRequest req) {
        userService.register(req);
        return Result.ok();
    }

    /**
     * 登录。成功时返回 { token, userId, username, nickname }。
     */
    @PostMapping("/login")
    public Result<LoginResult> login(@RequestBody LoginRequest req) {
        return Result.ok(userService.login(req));
    }

    /**
     * 用 token 换回自己的身份信息。
     *
     * ============================================================
     *  【Day10：这个接口瘦身了 —— 这就是拦截器的价值】
     * ============================================================
     *  昨天（Day9）这个方法里手写了三步：①取 token ②验签 ③查用户。
     *  今天这三步被搬进了 AuthInterceptor，于是这里只剩下"取结果、返回"。
     *
     *      昨天：30 多行（其中绝大部分在干"检查身份"的活）
     *      今天：6 行（检查的事根本不用写了）
     *
     *  【但比"少写代码"更重要的是】
     *    从今天起，**所有 /api/** 接口都自动享受这份检查** ——
     *    不需要每个 Controller 都抄一遍，新加的接口也默认被保护。
     *    这才是拦截器真正解决的痛点：不是省几行，而是**不会漏**。
     *
     *  ============================================================
     *  【新注解 @RequestAttribute】—— 取参数家族的第 5 个成员
     *  ============================================================
     *  把五个凑齐了，一眼看清它们的区别：
     *
     *     @PathVariable     从 URL 路径里取         /api/account/6
     *     @RequestParam     从 URL 问号后取         ?month=2026-10
     *     @RequestBody      从请求体里取            {"username":"xxx"}
     *     @RequestHeader    从请求头里取            Authorization: Bearer xxx
     *     @RequestAttribute 从 request 的**属性**里取   ← 今天这个
     *
     *  【最后这个和前四个有本质区别，是今天最该记住的一点：】
     *    · 前四个的值都来自**客户端发来的请求** —— 也就是说，**用户想改就能改**
     *    · @RequestAttribute 的值来自**服务器自己** ——
     *      它是 AuthInterceptor 验签成功后 `request.setAttribute("userId", 1L)` 放进去的
     *
     *  所以：**Controller 里可以直接信任这个 userId。**
     *  如果这里是从请求里让客户端传的（比如 /me?userId=2），
     *  那用户随手改成 2 就能看别人的数据了 —— 这种漏洞叫**越权（水平越权）**，
     *  是 Web 系统里最常见、危害最大的漏洞类型之一。
     *
     *  【Day11 预告】
     *    今天解决的是"你是谁"（认证 / Authentication）。
     *    明天解决"你能不能看这条数据"（授权 / Authorization）——
     *    那时我们会把这个 userId 当成查询条件塞进 SQL：
     *          WHERE user_id = #{userId}
     *    这样一来，A 用户哪怕猜到了 B 用户的账单 id，也什么都查不出来。
     */
    @GetMapping("/me")
    public Result<Map<String, Object>> me(@RequestAttribute(AuthInterceptor.ATTR_USER_ID) Long userId) {

        // 能走到这一行，说明拦截器已经验签通过了，userId 是"服务器认证过的真实身份"。
        // 【对比 Day9】昨天这两句之前有 4 个 if 检查，今天一个都不需要了。
        User user = userService.findById(userId);
        if (user == null) {
            //  极端情况：token 还没过期，但这个用户已经被删了。
            //  这里用普通业务异常（200 + code=1）而不是 401 ——
            //  因为"身份验证"那一关他其实是过了的，只是对应的记录没了。
            throw new IllegalArgumentException("用户不存在");
        }

        // ---------- 只返回该给前端看的字段 ----------
        //  【为什么这里用了 Map 而不是新建一个 vo？】
        //   因为它只有 3 个字段、且绝对不会碰到 password，用 Map 图个快。
        //   但请注意：**正式项目里应该定义 vo**（跟 LoginResult 一样）。
        //   好处是字段一目了然，不会手滑写错 key 名，
        //   而且将来接口文档、类型校验都能用上。这里只是演示"Map 也能当 data"。
        //
        //  用 LinkedHashMap 是为了保证 JSON 字段顺序跟 put 顺序一致（好看，不影响功能）。
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("userId", user.getId());
        data.put("username", user.getUsername());
        data.put("nickname", user.getNickname());
        return Result.ok(data);
    }
}
