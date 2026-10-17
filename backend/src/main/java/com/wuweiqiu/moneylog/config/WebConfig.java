package com.wuweiqiu.moneylog.config;

import com.wuweiqiu.moneylog.common.AuthInterceptor;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Web 配置类 —— 今天只干一件事：把登录检查拦截器"装"上去，并配好放行名单。
 *
 * ============================================================
 *  【@Configuration 是什么？】
 * ============================================================
 *  标记"这是一个配置类"。启动时 Spring 会扫描到它，
 *  发现它实现了 WebMvcConfigurer，就调用下面的 addInterceptors 方法，
 *  把我们的拦截器注册进去。
 *
 *  【为什么不写 @Component 而要写 @Configuration？】
 *    @Component 也能被扫描到、也能生效，但语义不准确 ——
 *    "配置类"应该用 @Configuration，让人一眼看出"这个类是在做配置"。
 *    命名和注解都是给别人（以及半年后的自己）看的。
 *
 *  ============================================================
 *  【WebMvcConfigurer 是什么？】
 *  ============================================================
 *  它是 Spring MVC 提供的一个"扩展点"接口。里面全是 default 方法，
 *  你**想改哪块就覆盖哪个方法**，不用管其他的。
 *  常用的几个：
 *     · addInterceptors       —— 注册拦截器        ← 今天用的
 *     · addCorsMappings       —— 配置跨域规则
 *     · addResourceHandlers   —— 配置静态资源映射
 *     · addFormatters         —— 注册自定义类型转换器
 *
 *  【这种设计叫"钩子（Hook）"】
 *  框架留出一些空方法，你按需实现其中几个，就把自己的逻辑插进了框架的流程里。
 *  学框架的本质就是：**找到它留了哪些钩子，然后在合适的钩子上挂自己的代码。**
 * ============================================================
 */
@Configuration
public class WebConfig implements WebMvcConfigurer {

    private final AuthInterceptor authInterceptor;

    public WebConfig(AuthInterceptor authInterceptor) {
        this.authInterceptor = authInterceptor;
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(authInterceptor)
                // 要拦哪些路径
                //   "/api/**" 表示 /api/ 下面**任意层级**的所有地址
                //   两个星号是关键：一个星号只匹配一层，两个才能匹配 /api/user/me 这种深层路径
                .addPathPatterns("/api/**")

                // 放行名单（白名单）—— 这些路径**不**检查登录
                // ┌──────────────────────────────────────────────────────────┐
                // │ 【这里是今天最容易想漏的地方，也是最有意思的一处】        │
                // └──────────────────────────────────────────────────────────┘
                //
                // 为什么"登录"接口必须放行？
                //   → 因为你**登录之前手上还没有 token**！
                //     如果登录接口也要求带 token，那你永远也登不进去 ——
                //     这是个死循环（先有鸡还是先有蛋）。
                //   注册同理：新用户肯定还没登录。
                //
                // 为什么 /api/ping 要放行？
                //   → 它是 Day1 留下的"探活"接口，用来测"服务到底起来了没"。
                //     部署时运维/监控系统会去请求它，那些请求当然不会带 token。
                //     如果把探活接口也保护起来，就等于"医院的门卫要求病人先挂完号才能进门挂号"。
                //
                //  【以后加接口时一定要过一遍脑子】
                //   "这个接口在用户登录之前会不会被调用？"
                //   会 → 加进放行名单；不会 → 让它被保护（默认被保护才是安全的）
                .excludePathPatterns(
                        "/api/user/register",
                        "/api/user/login",
                        "/api/ping"
                );

        // 【想一想：哪些是"不用管也安全"的？】
        //   前端页面（/、/index.html）、Vite 的资源……它们的路径不以 /api 开头，
        //   因为上面 addPathPatterns 限定了 "/api/**"，所以压根不会被拦。
        //   这就是"只拦该拦的"——不要把拦截范围设得太宽，
        //   否则连页面都打不开，会以为自己写错了。
    }
}
