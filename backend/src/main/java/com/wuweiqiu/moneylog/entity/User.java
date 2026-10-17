package com.wuweiqiu.moneylog.entity;

import java.time.LocalDateTime;

/**
 * 用户实体类 —— Java 世界里对 user 表一行的映射。
 *
 * 【和 Account/Bill 是同一个套路】表的每一行 <-> 这个类的一个对象。
 * 前面 8 天你已经很熟了，所以这里只讲"新的东西"。
 *
 * ============================================================
 *  【今天最重要的一个认知：password 字段存的是什么？】
 * ============================================================
 *  这个 password 属性在数据库里存的**不是**用户的原始密码，
 *  而是 BCrypt 加密后的**密文**，长这样：
 *      $2a$10$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LJZdL17lhWy
 *
 *  为什么要这样？因为数据库是可能被拖库的（被黑客整库导走）。
 *  如果存明文，用户在所有网站的密码就全泄露了——因为很多人到处用同一个密码。
 *  加密后，就算库被拖走，黑客拿到也基本反推不出原始密码。
 *
 *  注意：这是**单向**加密（也叫"哈希"），只能加密、不能解密。
 *  所以我们**无法**从密文还原出密码。那登录怎么核对？见 UserService.login()。
 *  ============================================================
 *
 * 【为什么类名可以叫 User，不会跟别的冲突？】
 *  因为它在我们自己的包 com.wuweiqiu.moneylog.entity 下，
 *  全限定名是 com.wuweiqiu.moneylog.entity.User，不会撞车。
 */
public class User {

    /** 主键，对应表里的 BIGINT id */
    private Long id;

    /** 登录用户名。数据库里有唯一索引 uk_username，重名插不进去 */
    private String username;

    /** 密码（**密文**，不是明文！加密发生在 UserService 里） */
    private String password;

    /** 昵称，仅用于页面显示，可以为空 */
    private String nickname;

    /** 注册时间 */
    private LocalDateTime createdAt;

    // ---------- getter / setter ----------
    // 为什么必须写？复习一下（前 8 天讲过三次了，这次你应该能自己说出来）：
    //   1) MyBatis 从数据库取出数据 → 靠 setter 塞进对象
    //   2) Jackson 把对象转成 JSON → 靠 getter 读取
    //   3) Jackson 把前端传来的 JSON 转成对象 → 靠 setter（这就是 @RequestBody 干的事）
    //
    // 【特别注意 password 的 getter】
    //   前端**永远不该**收到密码（哪怕是密文）。现在我们没有把 User 直接返回给前端，
    //   所以暂时安全。但这是个隐患 —— 以后如果哪次不小心 return user，
    //   密码密文就会跟着 JSON 一起发出去。真实项目里会加 @JsonIgnore 解决：
    //       @JsonIgnore
    //       public String getPassword() { return password; }
    //   今天我们故意先不加，等讲"数据隔离"时再让你亲眼看到这个坑。

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

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

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }
}
