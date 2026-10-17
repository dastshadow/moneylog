package com.wuweiqiu.moneylog.vo;

/**
 * 登录成功后返回给前端的东西。
 *
 * ============================================================
 *  【为什么不能直接返回 User 对象？】
 * ============================================================
 *  因为 User 里有 password 字段（哪怕存的是密文）。
 *  一旦 return user，Jackson 会扫描所有 getter，
 *  把 getPassword() 的结果也塞进 JSON —— 密文就跟着发出去了。
 *
 *  密文虽然不能直接反推，但泄露它仍然很糟：
 *    · 攻击者可以拿去做离线暴力破解（本地慢慢试，不用被服务器限流）
 *    · 可以拿去做"撞库"（很多人在别处用同样的密码，且哈希相同 → 能判断出相同）
 *
 *  【所以这是后端的一条规矩】
 *      "返回给前端的对象里，永远不要包含密码字段"
 *  做法有两种：
 *    ① 定义一个专门的 vo（就是这个类）—— 只有该给的字段
 *    ② 在实体上给 getPassword() 加 @JsonIgnore —— 全局生效
 *  我们选了 ①，因为它是**显式**的：你一眼能看到返回了什么，更安全。
 * ============================================================
 */
public class LoginResult {

    /**
     * 通行证（JWT token）。
     * 前端拿到它之后要存起来（localStorage），以后每次请求都带上，
     * 后端靠它认出"你是谁"。见 UserController 里的 /api/user/me。
     */
    private String token;

    /** 用户 id */
    private Long userId;

    /** 用户名 */
    private String username;

    /** 昵称（页面右上角显示的"你好，xxx"用的就是它） */
    private String nickname;

    public LoginResult() {
    }

    public LoginResult(String token, Long userId, String username, String nickname) {
        this.token = token;
        this.userId = userId;
        this.username = username;
        this.nickname = nickname;
    }

    public String getToken() {
        return token;
    }

    public void setToken(String token) {
        this.token = token;
    }

    public Long getUserId() {
        return userId;
    }

    public void setUserId(Long userId) {
        this.userId = userId;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public String getNickname() {
        return nickname;
    }

    public void setNickname(String nickname) {
        this.nickname = nickname;
    }
}
