package com.wuweiqiu.moneylog.common;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

/**
 * 登录检查拦截器 —— 站在所有接口的门口，检查"有没有出示通行证"。
 *
 * ============================================================
 *  【先搞清概念：拦截器是什么？站在流程的哪个位置？】
 * ============================================================
 *  Day1 画过那条主链，现在它前面多了一个"门卫"：
 *
 *      HTTP 请求
 *         ↓
 *      ★ 拦截器 preHandle  ← 今天新增的环节，**在 Controller 之前**执行
 *         ↓（通过了才继续往下走）
 *      DispatcherServlet 找到对应的 Controller
 *         ↓
 *      Controller → Service → Mapper → MySQL
 *
 *  ============================================================
 *  【HandlerInterceptor 的三个方法，什么时候执行？】
 *  ============================================================
 *    ① preHandle    —— Controller **之前**执行。今天只用这一个。
 *                      返回值是 boolean：**返回 false 就等于"这个请求到此为止"**，
 *                      Controller 不会被调用（这是"拦截"两个字的由来）。
 *
 *    ② postHandle   —— Controller **之后**、视图渲染之前执行。
 *                      注意：如果 Controller 抛了异常，这个方法**不会**被调用。
 *
 *    ③ afterCompletion —— 无论成功还是失败，**最后一定会执行**。
 *                      适合做"收尾/清理"（比如记录耗时、清理 ThreadLocal）。
 *                      【记法】preHandle 和 afterCompletion 是"一定成对"的，
 *                      类似 try-finally 的关系。
 *
 *  ============================================================
 *  【为什么不直接在 Controller 里写检查？】
 *  ============================================================
 *  你想想：现在有 20 个接口。如果在每个方法开头都写一遍
 *  "取 token → 验签 → 查用户"，会出现三个问题：
 *    ① 重复 20 遍，改一次要改 20 处
 *    ② 总有人会漏写（漏掉的那个接口就是漏洞）
 *    ③ Controller 里塞满了跟业务无关的代码
 *
 *  拦截器的价值就是：**检查逻辑只写一次，配好用哪些路径之后，
 *  所有接口自动生效，而且新加接口也自动被保护**（默认拦截）。
 *  这就是"配置式"比"手写式"强的地方。
 *
 *  【今天和 Day9 的关系】
 *    Day9 在 UserController./me 里，我们手写了这三步：
 *        取 token → 验签 → 查用户
 *    今天把它**抽出来**放进这里，/me 就只剩下"取结果、返回"两行了。
 *    先手动做一次、再自动化 —— 这样你才真的明白拦截器替你做了什么。
 * ============================================================
 */
@Component
public class AuthInterceptor implements HandlerInterceptor {

    /**
     * 我们把 userId 存到 request 的这个属性名上。
     *
     * 为什么定义成常量？
     *   因为它会被两个地方用到：这里 put、Controller 里 get。
     *   常量能保证"存"和"取"用的是同一个字符串 —— 手写两遍字符串容易拼错，
     *   而且拼错了编译器不报错、只在运行时报 null，很难查。
     */
    public static final String ATTR_USER_ID = "userId";

    private final JwtUtil jwtUtil;

    public AuthInterceptor(JwtUtil jwtUtil) {
        this.jwtUtil = jwtUtil;
    }

    /**
     * Controller 之前执行。返回 true = 放行，抛异常 = 拦下。
     *
     * @param request  本次请求（可以读请求头、也能往上挂东西）
     * @param response 本次响应（一般不用动它，见下面说明）
     * @param handler  即将执行的目标（通常是 Controller 里的某个方法）
     */
    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {

        // ---------------------------------------------------------
        // 0. 放行"预检请求"（OPTIONS）
        // ---------------------------------------------------------
        //  浏览器在发"跨域 + 自定义请求头"之前，会先自动发一个 OPTIONS 请求
        //  问一句"我能不能带这个头来访问你？"（这叫 CORS 预检 / preflight）。
        //
        //  【这个请求是浏览器自己发的，不会带 Authorization 头】——
        //  如果不放行，它就会被我们拦下、返回 401，
        //  浏览器一看预检失败，**真正的请求就永远发不出去**，
        //  报的错还是一句看不懂的 CORS 错误。这是个非常经典的坑。
        //
        //  我们现在的项目用 Vite 代理（前端 5173 → 后端 8080），
        //  浏览器看到的是"同源"，所以**不会**发预检。
        //  但万一以后前后端分开部署（真实上线时大概率会），这行就是救命的。
        //  提前写好，成本极低。
        if ("OPTIONS".equalsIgnoreCase(request.getMethod())) {
            return true;
        }

        // ---------------------------------------------------------
        // 1. 从请求头里取通行证
        // ---------------------------------------------------------
        String authorization = request.getHeader("Authorization");
        if (authorization == null || !authorization.startsWith("Bearer ")) {
            // 注意这里抛的是我们自己的 UnauthorizedException，
            // 它会被 GlobalExceptionHandler 翻译成 HTTP 401 + Result 格式的 JSON。
            // 提示语故意写得含糊 —— 不告诉对方"你是没带 token 还是 token 写错了"，
            // 减少给攻击者的信息量。
            throw new UnauthorizedException("请先登录");
        }

        // "Bearer " 这 7 个字符是行业约定（RFC 6750）的固定前缀，不是 token 的一部分，必须去掉
        String token = authorization.substring("Bearer ".length()).trim();

        // ---------------------------------------------------------
        // 2. 验签 —— 判断"这张证是不是我签的、有没有被改、过期没"
        // ---------------------------------------------------------
        //  用"不抛异常"的版本：三种失败（签名错、过期、格式乱）统一返回 null，
        //  我们就统一给一句提示，不用在拦截器里分别处理。
        Long userId = jwtUtil.parseUserIdQuietly(token);
        if (userId == null) {
            throw new UnauthorizedException("登录已失效，请重新登录");
        }

        // ---------------------------------------------------------
        // 3. 【关键】把 userId 挂到这次请求上，供后面的 Controller 取用
        // ---------------------------------------------------------
        //  为什么需要这一步？因为拦截器验完之后，Controller 也得知道"我是谁"
        //  （否则查询自己的数据时，不知道该过滤哪个 userId）。
        //  但拦截器和 Controller 是两个独立的对象，没有直接的传参渠道，
        //  所以借助 request 这个"本次请求的公共背包"来传递。
        //
        //  【Controller 那边怎么取？】
        //      public Result<?> me(@RequestAttribute("userId") Long userId)
        //  这就是取参数的第五个注解 —— 跟 @RequestParam / @RequestHeader 是一家人，
        //  区别只是"从哪儿取"：这次是从 request 的属性里取（值是我们自己放进去的）。
        //
        //  【真实项目里更常见的做法是 ThreadLocal】
        //  比如建一个 UserContext，在里面用 ThreadLocal<Long> 存 userId，
        //  因为同一个线程处理整个请求，Controller 直接从 ThreadLocal 里拿，
        //  不用在方法签名里加参数。
        //  但它有代价：**必须在 afterCompletion 里 remove()**，否则线程池复用线程时
        //  会把上一个用户的 id 泄漏给下一个请求 —— 是很严重的安全 bug。
        //  今天我们先用更直观的 request 属性，等以后有需要再上 ThreadLocal。
        request.setAttribute(ATTR_USER_ID, userId);

        // 返回 true = 检查通过，请求继续往下走（进入 Controller）
        return true;
    }
}
