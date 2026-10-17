package com.wuweiqiu.moneylog.dto;

/**
 * 注册请求 —— 专门用来"接前端传过来的参数"的类。
 *
 * ============================================================
 *  【今天的新概念：dto 包是干什么的？】
 * ============================================================
 *  Day8 我们认识了 vo（View Object）—— "**返回**给前端的形状"。
 *  今天补上它的孪生兄弟 dto（Data Transfer Object）—— "**接收**前端参数"的形状。
 *
 *      前端 → 后端    用 dto （本类）
 *      后端 → 前端    用 vo  （如 LoginResult）
 *      数据库 ↔ Java  用 entity（如 User / Account / Bill）
 *
 *  ============================================================
 *  【那为什么注册不直接用 User 来接参数？】
 *  ============================================================
 *  这是个很重要的安全问题。假设我们写：
 *      public Result<Void> register(@RequestBody User user) { ... }
 *
 *  那么前端（或者任何用 curl 的人）就能发这样的 JSON：
 *      {"username":"hacker","password":"123456","id":1,"createdAt":"2000-01-01T00:00:00"}
 *
 *  注意 id 和 createdAt —— **这两个字段本该由后端/数据库决定，不该由客户端指定**。
 *  但如果直接用 User 接，Jackson 会老老实实把它们填进去，
 *  万一你的代码里又用了 user.getId()，就会被"越权"。
 *
 *  【这叫"参数污染 / Mass Assignment"漏洞】，是很经典的一类安全问题。
 *
 *  解决它的标准做法就是：**只定义"客户端有权传的字段"**，也就是这个 dto。
 *  你看下面——只有 username / password / nickname 三个，
 *  客户端就算传 id 过来，也会被 Jackson 直接忽略（因为类里没有这个字段）。
 * ============================================================
 */
public class RegisterRequest {

    /** 用户名 */
    private String username;

    /** 密码（明文，只在网络传输中存在，落到数据库前就会被加密成密文） */
    private String password;

    /** 昵称，可以不传，不传就默认用用户名 */
    private String nickname;

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

    public String getNickname() {
        return nickname;
    }

    public void setNickname(String nickname) {
        this.nickname = nickname;
    }
}
