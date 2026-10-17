package com.wuweiqiu.moneylog.dto;

/**
 * 登录请求 —— 只接两个字段：用户名 + 密码。
 *
 * 为什么单独建一个类，而不是复用 RegisterRequest？
 *   因为两者的字段不一样：注册需要 nickname，登录不需要。
 *   如果为了省事共用一个类，就会出现"登录时也能传昵称"这种莫名其妙的情况。
 *
 *   【一个通用判断】dto 的数量，应该跟"前端不同的提交场景"一一对应：
 *     注册表单   ->  RegisterRequest
 *     登录表单   ->  LoginRequest
 *     改密码表单 ->  ChangePasswordRequest（以后要加）
 *   每个类都小而明确，比一个"大而全"的类好维护得多。
 */
public class LoginRequest {

    private String username;
    private String password;

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public String getPassword() {
        return password;
    }

    public void setPassword(String password) {
        this.password = password;
    }
}
