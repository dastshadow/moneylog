package com.wuweiqiu.moneylog.controller;

import com.wuweiqiu.moneylog.common.Result;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 健康检查接口 —— 第一个接口，用来验证"Web 层通了"。
 * 它不碰数据库，所以能单独测出"是不是 Web 层的问题"。
 *
 * 三个注解各管什么：
 *   @RestController = @Controller + @ResponseBody
 *       @Controller       把这个类注册成 Spring 的 Bean（@ComponentScan 负责扫到它）
 *       @ResponseBody     方法的返回值直接转成 JSON 写回响应体（而不是去找一个网页模板）
 *   @RequestMapping("/api")   给类里所有接口加统一前缀，所以完整路径 = /api + /ping
 *   @GetMapping("/ping")      只接受 GET 请求，映射到 /api/ping
 */
@RestController
@RequestMapping("/api")
public class PingController {

    /**
     * 访问方式：浏览器打开 http://localhost:8080/api/ping
     * 期望返回：{"code":0,"msg":"ok","data":"pong"}
     */
    @GetMapping("/ping")
    public Result<String> ping() {
        return Result.ok("pong");
    }
}
