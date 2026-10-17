package com.wuweiqiu.moneylog.common;

/**
 * "未登录 / 通行证无效" 异常。
 *
 * ============================================================
 *  【今天为什么要新建一个异常类？】
 * ============================================================
 *  我们已经有 IllegalArgumentException 用来表示"业务校验不通过"（返回 200 + code=1）。
 *  但"没登录"这件事**性质完全不同**，它应该返回 HTTP 401。
 *
 *  如果偷懒复用 IllegalArgumentException，会发生什么？
 *    · 全局处理器会把它当成"业务错误"，返回 HTTP 200
 *    · 前端 / 网关 / 监控都看不出"这是没登录"，只能靠读 msg 去猜
 *    · 跟 Day5、Day6 反复强调的那条规则冲突了：
 *        "HTTP 层面的问题用真实状态码，业务层面的问题用 200 + code"
 *
 *  ============================================================
 *  【自定义异常的意义：给"一类错误"起个名字】
 *  ============================================================
 *  异常类的名字本身就是一种"分类标签"。有了它：
 *    · 代码里一眼能看出 `throw new UnauthorizedException("请先登录")` 的含义
 *    · 全局处理器能针对它单独配一条规则（→ 401）
 *    · 以后想加"权限不足"（403）时，再建一个 ForbiddenException 就行，
 *      各自独立、互不干扰
 *
 *  【为什么继承 RuntimeException 而不是 Exception？】
 *    Java 里异常分两类：
 *      · Exception（受检异常）：必须 try-catch 或往上层抛声明，否则编译不过
 *      · RuntimeException（非受检异常）：可以一路往上抛，不用声明
 *
 *    我们希望的正是后者 —— 在拦截器里抛出去，
 *    **一路穿过框架、直达全局异常处理器**，中间不用每一层都写声明。
 *    Spring 的事务回滚、异常处理，默认也是围绕着 RuntimeException 设计的。
 *
 *  【一个真实的坑，提前知道】
 *    @Transactional 默认**只对 RuntimeException 回滚**。
 *    如果抛的是受检异常，事务不会回滚 —— 这是生产环境事故的常见原因之一。
 *    （Day7 的事务课可以回头对照着看这条。）
 */
public class UnauthorizedException extends RuntimeException {

    public UnauthorizedException(String message) {
        super(message);
    }
}
