package com.wuweiqiu.moneylog;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * 项目启动类 —— 整个程序的"总开关"。
 *
 * @SpringBootApplication 是三个注解的合体：
 *   1) @SpringBootConfiguration 声明这是一个配置类
 *   2) @EnableAutoConfiguration 自动装配：看到 classpath 里有 web 依赖，就自动配好 Tomcat
 *   3) @ComponentScan           扫描"本包及其所有子包"里的 @Controller / @Service 等注解，
 *                               把它们注册成 Spring 容器里的 Bean（对象）
 *
 * 【极重要】正因为第 3 条，这个类必须放在最外层的"根包"。
 *          其他所有类都必须放在 com.wuweiqiu.moneylog 下面，
 *          否则 Spring 扫不到，你会遇到"No qualifying bean"这类找不到对象的报错。
 */
@SpringBootApplication
public class MoneylogApplication {

    public static void main(String[] args) {
        // 这一行 = 按下总开关：装配所有零件 + 启动内嵌 Tomcat
        SpringApplication.run(MoneylogApplication.class, args);
    }
}
