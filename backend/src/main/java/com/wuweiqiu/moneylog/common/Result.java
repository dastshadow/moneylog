package com.wuweiqiu.moneylog.common;

/**
 * 统一响应体 —— 所有接口都返回这个形状，前端处理起来才统一。
 *
 * 为什么要统一？想象一下：如果有的接口返回字符串、有的返回对象、有的报错返回一堆 HTML，
 * 前端就得写无数种判断。统一成 { code, msg, data } 之后：
 *   code = 0    表示成功，前端取 data
 *   code != 0   表示失败，前端把 msg 弹个提示框
 *
 * 泛型 <T> 的作用：data 可以是字符串、对象、列表……由调用方决定，但外层结构永远不变。
 */
public class Result<T> {

    private int code;      // 业务状态码：0 = 成功
    private String msg;    // 提示信息
    private T data;        // 真正的业务数据

    // 私有构造：强制外部通过下面两个静态方法来创建，保证 code/msg 的用法一致
    private Result(int code, String msg, T data) {
        this.code = code;
        this.msg = msg;
        this.data = data;
    }

    /** 成功，不带数据（比如"删除成功"这种不需要返回内容的场景） */
    public static <T> Result<T> ok() {
        return new Result<>(0, "ok", null);
    }

    /** 成功，带数据 */
    public static <T> Result<T> ok(T data) {
        return new Result<>(0, "ok", data);
    }

    /** 失败 */
    public static <T> Result<T> fail(String msg) {
        return new Result<>(1, msg, null);
    }

    // ---------- 以下 getter 是必须的：Spring 靠它们把对象转成 JSON ----------
    // Spring Boot 默认用 Jackson 把返回值序列化成 JSON。
    // Jackson 不认识"私有字段"，它只认 getter 方法。
    // 如果没有 getCode()，返回的 JSON 里就只剩一个空对象 {}。这是新手必踩的坑。

    public int getCode() {
        return code;
    }

    public String getMsg() {
        return msg;
    }

    public T getData() {
        return data;
    }
}
