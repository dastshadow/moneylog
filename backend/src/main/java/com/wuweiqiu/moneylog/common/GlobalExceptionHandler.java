package com.wuweiqiu.moneylog.common;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

/**
 * 全局异常处理器 —— 整个项目的"兜底网"。
 *
 * ============================================================
 *  为什么需要它？先说清楚它解决什么问题
 * ============================================================
 *
 *  今天我们发现了两件事：
 *
 *  ① 后端不校验 → 脏数据能进库
 *     → 已经在 AccountService 里加了校验（抛 IllegalArgumentException）
 *
 *  ② 但抛出异常后，返回给前端的格式"变了样"
 *     正常成功时我们返回的是：
 *         {"code":0, "msg":"ok", "data":null}          ← 我们自己定义的统一响应体
 *     但异常发生时，Spring 默认返回的是：
 *         {"timestamp":"...","status":500,"error":"Internal Server Error","path":"..."}
 *     两个格式完全不一样！后果是：
 *         · 前端按 {code,msg,data} 写的代码**读不懂**这个格式
 *         · 只能笼统显示"网络错误"，用户不知道到底哪错了
 *         · 还泄露了技术细节（时间戳、路径），给攻击者提供信息
 *
 *  【这个类的作用】把"异常"统一翻译成"我们自己格式的错误响应"。
 *   于是前端永远只需要处理一种格式：{code, msg, data}。
 *   code=0 成功，code≠0 失败，msg 里是人话提示。
 *
 * ============================================================
 *  三个注解拆开讲
 * ============================================================
 *
 *  @RestControllerAdvice
 *    这是一个"组合注解"，等于三件事：
 *      · @ControllerAdvice  —— 声明"我要给所有 Controller 当管家"。
 *                              加了它之后，**任何一个** Controller 抛出的异常，
 *                              都会先送到这里来，而不是直接返回给前端。
 *      · @ResponseBody      —— 这里方法的返回值要转成 JSON 写回响应体
 *                              （跟 @RestController 里那个 @ResponseBody 是同一件事）
 *
 *    好处：写一次，全项目所有接口都生效。**不用在每个 Controller 里写 try-catch。**
 *    如果你以前写过"每个方法都包一层 try-catch"，就会懂这个注解有多省事。
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    /**
     * 日志对象。用来把异常记到控制台和日志文件里。
     *
     * 为什么有了返回值还要记日志？
     *   因为返回给前端的 msg 是"给用户看的"（要简洁、人话）；
     *   而日志是"给开发看的"（要详细、能看到调用栈）。
     *   两者用途不同，都需要。
     *
     * LoggerFactory.getLogger(Xxx.class) 是固定写法，
     * 括号里传当前类的 class，日志里就能看出是哪个类打的。
     */
    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    /**
     * 处理"参数不合法"类异常 —— 也就是我们在 Service 里主动抛出的那种。
     *
     * ============================================================
     *  @ExceptionHandler 是怎么工作的？
     * ============================================================
     *  括号里写"我负责处理哪种异常"。
     *  Spring 的匹配规则是"就近原则"：**谁最具体，就用谁**。
     *
     *  比如异常是 IllegalArgumentException：
     *    · 如果同时存在处理 IllegalArgumentException 和处理 Exception 的方法
     *      → 会用 IllegalArgumentException 那个（更具体）
     *    · 如果只有处理 Exception 的
     *      → 就用它兜住（Exception 是所有异常的爹）
     *
     *  所以下面这个方法负责"业务校验失败"，
     *  后面那个 handleException 负责"所有没被前面接住的意外"。
     *
     * ============================================================
     *  为什么返回时 HTTP 状态码是 200（而不是 400/500）？
     * ============================================================
     *  这是个常见的设计选择，我们选了"HTTP 永远 200，业务错误靠 code 区分"。
     *
     *  两种风格各有道理：
     *    · 风格A（我们用的）：HTTP 200 + code≠0
     *        好处：前端只需要判断一处（code），不用同时处理 HTTP 状态和 code，
     *              逻辑简单不容易漏。国内项目多数这么做。
     *    · 风格B：HTTP 400 + code≠0
     *        好处：符合 HTTP 语义，浏览器/网关/监控工具能自动识别为"失败请求"。
     *
     *  这里先按风格A做，等以后你需要接监控、报警系统时，
     *  再把校验失败改成 HTTP 400 —— 那时候你会明白为什么有些团队选 B。
     *
     *  【返回类型为什么是 Result<Void>？】
     *    因为出错时没有业务数据可返回，data 是空的。
     *    用 Void 明确表达"这个接口不返回数据"。
     */
    @ExceptionHandler(IllegalArgumentException.class)
    public Result<Void> handleIllegalArgument(IllegalArgumentException e) {
        // 异常对象 e 自带我们抛出去时说的人话，比如"账户名称不能为空"
        // e.getMessage() 就是把它取出来
        String msg = e.getMessage();

        // 记日志：级别用 WARN（警告）而不是 ERROR。
        //   为什么？因为这类错误是"用户输入不对"，属于预期内的情况，
        //   不是系统故障。如果全用 ERROR，真正的系统问题会被淹没在噪音里。
        log.warn("业务校验未通过：{}", msg);

        // 翻译成我们自己格式的错误响应：code=1 表示失败
        return Result.fail(msg);
    }

    /**
     * 处理"请求体格式不对"的异常。
     *
     * ============================================================
     *  【今天（Day6）遇到的，跟 Day5 是同一类问题的翻版】
     * ============================================================
     *  做账单功能时，我把日期写成斜杠格式试了一下：
     *      POST /api/bill/create
     *      {"billDate":"2026/10/10"}          ← 用了斜杠
     *
     *  结果返回：
     *      {"code":1,"msg":"系统繁忙，请稍后再试"}     ← 提示错了
     *  日志里是：
     *      ERROR ... 系统异常
     *      HttpMessageNotReadableException: JSON parse error:
     *      Cannot deserialize value of type `java.time.LocalDate`
     *      from String "2026/10/10"
     *
     *  【又是同样的三个毛病】（跟 Day5 的 MethodArgumentTypeMismatchException 一模一样）
     *    ① 提示错："你日期格式写错了"跟"服务器坏了"完全是两回事
     *    ② 日志级别错：客户端传错数据，该用 WARN
     *    ③ 状态码错：请求体格式不对该返回 400，不该伪装成 200
     *
     *  ============================================================
     *  【这个现象本身值得记住】
     *  ============================================================
     *  Day5 修完参数类型错之后，我以为"该修的异常都修完了"。
     *  结果今天一换数据类型（LocalDate），同样的坑又出现一次。
     *
     *  这说明一件重要的事：
     *    **兜底方法（@ExceptionHandler(Exception.class)）是"垃圾桶"，
     *      它不是解决方案，只是最后一道保险。**
     *    每当你发现某类异常**经常出现、且有明确含义**，
     *    就该把它从垃圾桶里捞出来，单独配一个处理方法。
     *
     *  所以这个类会随着项目推进不断变长 —— 这是**正常的、健康的演进**，
     *  不是设计缺陷。真实项目里的全局异常处理器往往有十几个方法。
     *
     *  ============================================================
     *  【Jackson 为什么认不了 "2026/10/10"？】
     *  ============================================================
     *  它默认只认国际标准格式（ISO-8601）：
     *      "2026-10-10"   ← 用短横线 ✓
     *      "2026/10/10"   ← 用斜杠   ✗
     *
     *  这也是前端必须遵守的约定：日期一律传 "年-月-日"。
     *  （如果实在要支持斜杠，可以给字段加 @JsonFormat 注解指定格式，
     *    但更推荐统一用标准格式 —— 少一个特例，少一处出错的地方。）
     */
    @ExceptionHandler(HttpMessageNotReadableException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public Result<Void> handleNotReadable(HttpMessageNotReadableException e) {
        log.warn("请求体格式错误：{}", e.getMessage());
        return Result.fail("请求数据格式不正确，请检查后重试");
    }

    /**
     * 处理"路径参数格式不对"的异常。
     *
     * ============================================================
     *  【这个是 Day5 新发现的问题，值得完整讲一遍】
     * ============================================================
     *
     *  Day5 做删除接口时，我故意试了一下：
     *      DELETE /api/account/abc        ← id 位置传了个字母
     *
     *  结果接口返回：
     *      {"code":1,"msg":"系统繁忙，请稍后再试"}
     *  日志里记的是：
     *      ERROR ... 系统异常
     *      MethodArgumentTypeMismatchException: Failed to convert value of type
     *      'java.lang.String' to required type 'java.lang.Long'; For input string: "abc"
     *
     *  【三个问题，全都是错的】
     *
     *   ① 提示错得离谱
     *      这是"你地址栏里 id 写错了"，不是"服务器坏了"。
     *      用户看到"系统繁忙"会以为是我们服务出故障，然后白等重试。
     *
     *   ② 日志级别用错了
     *      按 Day4 讲的规则：**用户输入错误应该用 WARN，只有系统故障才用 ERROR**。
     *      用 ERROR 记这种错，日志里就会堆满"用户传错参数"的噪音。
     *      真出大事时（数据库挂了），你在一堆噪音里根本找不到它。
     *
     *   ③ 职责边界没划清
     *      这个异常是 Spring 在"把 URL 里的字符串转成 Long"时抛的，
     *      压根还没进到我们的 Controller 方法里。
     *      它属于"框架层的参数绑定失败"，跟"业务规则不通过"是两回事，
     *      应该分开处理、给出不同的提示。
     *
     *  ============================================================
     *  【Day6 补充：我把这里的状态码改了】
     *  ============================================================
     *  Day5 写这个方法时我没加 @ResponseStatus，所以它返回的是 HTTP 200。
     *  今天做账单时又碰上同类的"格式错误"，回头再看，发现规则不统一：
     *
     *      404 找不到地址      → 用了真实状态码 ✓
     *      参数类型转换失败     → 却用了 200        ✗ 不一致！
     *
     *  两者其实**是同一类问题**：都是"请求本身就有毛病"，
     *  跟我们的业务规则没关系。所以现在统一加上了 400。
     *
     *  【统一后的完整规则，这是最终版】
     *    ┌─────────────────────────────────────────────┐
     *    │ HTTP 层面的问题（请求本身有毛病）            │
     *    │   · 地址不存在          → 404                │
     *    │   · 请求方法用错        → 405（Spring 默认） │
     *    │   · 路径参数类型不对    → 400  ← 本次调整    │
     *    │   · 请求体格式不对      → 400  ← 今天新增    │
     *    ├─────────────────────────────────────────────┤
     *    │ 业务层面的问题（请求没问题，只是规则不允许） │
     *    │   · 账户名称为空        → 200 + code=1       │
     *    │   · 记录不存在          → 200 + code=1       │
     *    │   · 金额为负数          → 200 + code=1       │
     *    └─────────────────────────────────────────────┘
     *
     *  【为什么要这样分？】
     *    前者是"客户端把请求写错了"，是调用方的问题 → 用 4xx 让监控能统计出来
     *    后者是"业务上不允许"，是正常的业务失败 → 用 200，因为这次网络交互本身是成功的
     *
     *  一句话：**"请求有毛病"用 4xx，"业务被拒绝"用 200 + code。**
     */
    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public Result<Void> handleTypeMismatch(MethodArgumentTypeMismatchException e) {
        // e.getName() 能取出出错的那个参数名，这里是 "id"
        // 拼成"参数 id 格式不正确"这样更有指向性
        String msg = "参数 " + e.getName() + " 格式不正确";

        // 【关键】这里用 WARN 而不是 ERROR —— 这是客户端传错了，不是我们系统坏了
        log.warn("参数格式错误：{}", e.getMessage());

        return Result.fail(msg);
    }

    /**
     * 处理"访问了不存在的地址"。
     *
     * ============================================================
     *  又发现一个问题：404 被我们改成了 200
     * ============================================================
     *  今天访问一个根本不存在的接口时：
     *      DELETE /api/nothing/1
     *
     *  发现返回的竟然是 **HTTP 200**，响应体是"系统繁忙，请稍后再试"。
     *  为什么？因为 Spring 找不到这个路由时抛了 NoResourceFoundException，
     *  而它被我们的兜底方法（@ExceptionHandler(Exception.class)）接住了，
     *  于是"404 错误"被翻译成了"200 + 业务失败"。
     *
     *  【这为什么是问题？】
     *    · 浏览器、网关、监控系统、日志分析工具都靠 HTTP 状态码判断请求是否正常
     *    · 你以后接入监控报警，配置"统计 4xx/5xx 数量"时会发现全都是 0，
     *      因为所有错误都被你伪装成了 200 —— 出了问题你根本察觉不到
     *    · 这是**掩盖问题**，比报错更危险
     *
     *  ============================================================
     *  【那 Day4 不是说"我们选 HTTP 200 + 业务 code"吗？矛盾吗？】
     *  ============================================================
     *  不矛盾，这里要区分两类不同的"失败"：
     *
     *    ① HTTP 层面的失败 —— 请求压根没找到能处理它的接口
     *       · 地址写错了（404）
     *       · 方法用错了，比如对只收 GET 的地址发 POST（405）
     *       → 这类**应该用真实的 HTTP 状态码**，因为它是"HTTP 协议层面"的事，
     *         跟我们的业务毫无关系。
     *
     *    ② 业务层面的失败 —— 请求找到了接口，但业务规则不允许
     *       · 账户名称为空
     *       · 账户不存在
     *       → 这类用 **HTTP 200 + code≠0**，因为"HTTP 层面这次交互是成功的，
     *         只是业务上没通过"。
     *
     *  一句话记：**"找不到门"要用 404，"进门后被拒绝"用 200+code。**
     *
     *  ============================================================
     *  @ResponseStatus 是干什么的？
     *  ============================================================
     *  不加它，Spring 默认给成功的响应码（200）。
     *  加上 @ResponseStatus(HttpStatus.NOT_FOUND)，这个方法的响应就会是 404。
     *
     *  同时响应体依然是我们的 Result 格式的 JSON ——
     *  **既有正确的状态码，又有统一的响应结构**，两全其美。
     */
    @ExceptionHandler(NoResourceFoundException.class)
    @ResponseStatus(HttpStatus.NOT_FOUND)
    public Result<Void> handleNotFound(NoResourceFoundException e) {
        log.warn("访问了不存在的地址：{}", e.getMessage());
        return Result.fail("请求的地址不存在");
    }

    /**
     * 处理"未登录 / 通行证无效" —— Day10 新增，由 AuthInterceptor 抛出。
     *
     * ============================================================
     *  【为什么这条用 401，而不是像业务错误那样用 200 + code=1？】
     * ============================================================
     *  因为"没登录"根本不是业务问题，它属于**更前面的一道关**：
     *  请对照 Day5、Day6 我们反复打磨出来的那张规则表 —— 今天它又要长一行了：
     *
     *    ┌────────────────────────────────────────────────────────────┐
     *    │ 第 0 层：身份层（连"你是谁"都不知道）                       │
     *    │   · 没带 token / token 过期 / 签名被改   → 401  ← 今天新增  │
     *    ├────────────────────────────────────────────────────────────┤
     *    │ 第 1 层：HTTP 层（请求本身有毛病）                          │
     *    │   · 地址不存在                           → 404              │
     *    │   · 请求方法用错                         → 405              │
     *    │   · 参数格式错 / 请求体格式错            → 400              │
     *    ├────────────────────────────────────────────────────────────┤
     *    │ 第 2 层：业务层（请求没毛病，只是规则不允许）               │
     *    │   · 账户名称为空 / 余额不足 / 记录不存在 → 200 + code≠0     │
     *    └────────────────────────────────────────────────────────────┘
     *
     *  【为什么必须区分出来？】
     *    401 是 HTTP 标准里专为"未认证"预留的状态码。
     *    用它，前端就能写一处**统一逻辑**：
     *        "只要收到 401，就清掉本地 token 并跳回登录页"
     *    而不用去读 msg 里到底写了什么字。
     *    （你看，这就是"标准状态码"的价值 —— 它让处理逻辑可以不依赖文案。）
     *
     *    另一个好处：网关、监控、日志分析都认识 401。
     *    如果把它伪装成 200，你就永远统计不出"有多少人没登录就在访问接口"。
     *
     *  【响应体依然是 Result 格式】
     *    状态码是 401，但 body 还是 {"code":1,"msg":"请先登录","data":null}。
     *    **既有正确的 HTTP 语义，又保持了前端统一处理的结构** —— 两全其美。
     *
     * ============================================================
     *  【一个必须知道的技术细节：拦截器里抛的异常，这里接得住吗？】
     * ============================================================
     *  接得住。这是个值得记住的结论：
     *
     *    Spring MVC 的 DispatcherServlet 在调用拦截器的 preHandle 时，
     *    是**包在 try-catch 里**的。一旦 preHandle 抛异常，
     *    它会走和 Controller 抛异常**完全相同**的流程 ——
     *    交给 @RestControllerAdvice 去处理。
     *
     *  所以我们在拦截器里可以放心 throw，不用自己去 response 里手写 JSON。
     *  这也是为什么拦截器里的提示语能保持和业务错误一样的格式 ——
     *  因为它们走的是同一个出口。
     *
     *  【反过来说，另一种做法是可行的但不推荐】
     *    preHandle 里 return false，然后自己用 response.getWriter() 写 JSON。
     *    能跑通，但那样响应体格式就得手写、容易跟别处不一致。
     *    **能交给统一出口的，就别自己造。**
     */
    @ExceptionHandler(UnauthorizedException.class)
    @ResponseStatus(HttpStatus.UNAUTHORIZED)
    public Result<Void> handleUnauthorized(UnauthorizedException e) {
        // 【日志用 WARN】
        //   这是"用户没登录/凭证过期"，属于预期内的正常情况，不是系统故障。
        //   想象一下 token 过期时用户刚好在操作 —— 一天可能出现几百次，
        //   全记成 ERROR 的话，真正的故障就被埋在噪音里了。
        //   （这条规则从 Day4 一直用到今天，是它的第 N 次应用。）
        log.warn("未通过身份校验：{}", e.getMessage());
        return Result.fail(e.getMessage());
    }

    /**
     * 兜底方法 —— 处理所有"没人认领"的异常。
     *
     * 【这个方法存在的意义：防止内部细节泄露到前端】
     *
     *   如果不写它，任何意外异常（比如空指针、数据库连不上）都会
     *   把完整的 Java 堆栈吐给前端。攻击者从堆栈里能看出：
     *     · 你用了什么框架、什么版本（可以查已知漏洞）
     *     · 你的类名、包名、方法名（可以猜代码结构）
     *     · 你的数据库表名、SQL 语句（可以构造更精准的攻击）
     *   这在安全上叫"信息泄露"，是很典型的漏洞。
     *
     *   所以兜底的做法是：**详细内容只写日志，前端只给一句笼统的话。**
     *
     * 【Exception.class 是最宽的范围】
     *   它匹配几乎所有异常（Exception 是运行时异常的总父类）。
     *   因为它最"不具体"，所以只有前面那些具体方法都匹配不上时，才轮到它。
     */
    @ExceptionHandler(Exception.class)
    public Result<Void> handleException(Exception e) {
        // 【重点】日志里记完整的异常（含堆栈），方便我们排查
        //   log.error(描述, 异常对象) 这个重载会自动打印完整堆栈
        log.error("系统异常", e);

        // 但返回给前端的只有一句笼统的话，**不暴露任何技术细节**
        return Result.fail("系统繁忙，请稍后再试");
    }
}
