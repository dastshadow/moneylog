package com.wuweiqiu.moneylog.entity;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 账户实体类 —— Java 世界里对 account 表一行的"映射"。
 *
 * 对照关系：表的每一行  <->  这个类的一个对象
 *          表的一列    <->  类的一个属性
 *
 * 【为什么属性名是驼峰、表字段是下划线？】
 *   表字段：created_at（下划线风格，数据库惯例）
 *   Java属性：createdAt（驼峰风格，Java 惯例）
 *   两者靠 application.yml 里的 map-underscore-to-camel-case: true 自动对应。
 *   这就是为什么必须开那个配置——不开的话 createdAt 会一直是 null！
 *
 * 【为什么 balance 用 BigDecimal 而不是 double？】
 *   double 是浮点数，算钱会丢精度（0.1 + 0.2 = 0.30000000000000004）。
 *   BigDecimal 是定点数，精确表示小数。数据库的 DECIMAL(12,2) 就该对应 BigDecimal。
 *   这是财务类系统的一条铁律：钱不能用 double。
 */
public class Account {

    /** 主键。BigDecimal 对应表的 BIGINT（数据库 bigint 比 Java int 范围大得多） */
    private Long id;

    /**
     * 【Day11 新增】所属用户 id —— 这条账户是"谁的"。
     *
     * ============================================================
     *  有了它，才谈得上"数据隔离"
     * ============================================================
     *  在这之前，account 表里每一行都是"无主"的，所以任何人登录后
     *  都能看到**所有人**的账户。现在每一行都带上了主人，
     *  查询时加上 `WHERE user_id = 当前登录用户`，就谁也看不到谁的。
     *
     * ============================================================
     *  ⚠️【这个字段带来一个安全上的坑，必须记住】
     * ============================================================
     *  加了 userId 字段之后，前端发来的 JSON 里**也能塞 userId** 了 ——
     *  因为 Jackson 是按字段名自动绑定的，它会去调 setUserId()。
     *
     *  也就是说，坏人可以这样构造请求：
     *      POST /api/account/create
     *      {"name":"伪装账户", "balance":1, "userId":2}    ← 想写到别人名下！
     *
     *  这就是 Day9 讲过的 **Mass Assignment（批量赋值）漏洞**，
     *  今天它从"理论"变成了"真实会发生的事"。
     *
     *  【解法：Service 层强制覆盖，绝不让客户端决定】
     *      account.setUserId(当前登录用户的id);   // 不管前端传了什么都盖掉
     *  具体见 AccountService.create() 里那一行。
     *
     *  【一条通用原则（这是今天最值钱的一句）】
     *      **凡是由服务器决定归属的字段，都要在写入前强制覆盖。**
     *      因为客户端传上来的任何东西都不可信 —— 这是 Day4 就定下的规矩，
     *      今天它第一次真正造成了危害。
     * ============================================================
     */
    private Long userId;

    /** 账户名称，如"微信钱包""支付宝" */
    private String name;

    /** 账户余额（元）。对应 DECIMAL(12,2) */
    private BigDecimal balance;

    /** 创建时间。对应 DATETIME，Java 里用 LocalDateTime（JDK8+ 的新时间 API） */
    private LocalDateTime createdAt;

    // ---------------------------------------------------------------
    // 下面是 getter / setter。
    // 为什么必须写？两个原因：
    //   1) MyBatis 从数据库取出数据后，靠 setter 把列值"塞"进对象（这叫属性赋值）
    //   2) Spring 把对象转成 JSON 返回给前端时，靠 getter 读取属性值
    // 少写了哪个，哪里就出问题，而且报错不明显——最常见的就是"返回全是 null"。
    // 等下一阶段我们会引入 Lombok 的 @Data 注解自动生成这些方法，但今天先手写，
    // 你要亲眼见过一次，才知道 @Data 到底替你干了什么。
    // ---------------------------------------------------------------

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Long getUserId() {
        return userId;
    }

    public void setUserId(Long userId) {
        this.userId = userId;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public BigDecimal getBalance() {
        return balance;
    }

    public void setBalance(BigDecimal balance) {
        this.balance = balance;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }
}
