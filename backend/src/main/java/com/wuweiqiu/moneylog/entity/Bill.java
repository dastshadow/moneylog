package com.wuweiqiu.moneylog.entity;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 账单实体类 —— 对应 bill 表的一行。
 *
 * 对照关系（跟 Account 一样）：
 *   表的一行 <-> 这个类的一个对象
 *   表的一列 <-> 类的一个属性
 *
 * ============================================================
 *  【本表设计要点，也是实体类要如实反映的东西】
 * ============================================================
 *  1) accountId  —— 账单选了哪个账户（对应表里的 account_id）
 *     它是一个"外键"：指向 account 表的 id。
 *     注意：这里存的是**编号**，不是账户名字。
 *     想看名字要靠连表查询（见 BillMapper.xml 里的 JOIN）。
 *
 *  2) type  —— 1 = 支出，2 = 收入
 *     用数字而不是文字，省空间、也不容易写错。
 *     这个 1 和 2 是什么含义，代码里必须能看懂，
 *     所以下面定义了常量，避免满代码都是"魔法数字 1"。
 *
 *  3) amount —— 金额，**永远存正数**
 *     方向（是花出去还是收进来）由 type 决定，不由正负号决定。
 *     【为什么这样设计更好？】
 *       如果支出存负数、收入存正数，那么每处代码都要小心处理符号：
 *       "这个 -35.00 是支出还是退款？" —— 容易搞混。
 *       现在做成"金额永远是正的，方向看 type"，任何地方一看就清楚。
 *
 *  4) billDate —— 账单日期（哪一天的钱），类型是 LocalDate（只有年月日）
 *     createdAt —— 录入时间（什么时候记的），类型是 LocalDateTime（含时分秒）
 *     【这两个必须分开】
 *       你昨天花的钱，今天才想起来补记 —— billDate 填昨天，createdAt 是今天。
 *       如果只有一个时间字段，这个需求就没法满足，
 *       而且"按日期统计月度开支"会算错。
 *
 *  5) accountName —— 【注意！这个字段在表里不存在】
 *     它是连表查询时，从 account 表"顺便带回来"的账户名。
 *     【这是一个重要认知】实体类不一定要和表字段一一对应。
 *       为了让前端能直接显示"微信钱包"而不是干巴巴的"1"，
 *       我们在查询时把账户名一起查出来，塞进这个字段。
 *       表里没有这一列，但对象里有这个属性 —— 完全允许，而且很常见。
 *       做"新增"时这个字段不用传，数据库也不关心它。
 */
public class Bill {

    /** 支出 */
    public static final int TYPE_EXPENSE = 1;
    /** 收入 */
    public static final int TYPE_INCOME = 2;

    /** 主键 */
    private Long id;

    /**
     * 【Day11 新增】所属用户 id —— 这笔账是"谁的"。
     *
     * 和 Account.userId 是同一个东西、同一个套路（详细说明见 Account.java 里那段注释）：
     *   · 查询要带 `WHERE user_id = 当前登录用户`，否则能看到别人的账
     *   · 新增时**由 Service 强制覆盖**，不接受客户端传的值
     *     （否则坏人可以在 JSON 里写 "userId":2，把账记到别人头上）
     */
    private Long userId;

    /** 所属账户的编号（外键，指向 account.id） */
    private Long accountId;

    /** 类型：1 = 支出，2 = 收入 */
    private Integer type;

    /** 金额（永远为正数，方向由 type 决定） */
    private BigDecimal amount;

    /** 分类，如"餐饮""交通""工资" */
    private String category;

    /** 账单日期（哪一天的钱） */
    private LocalDate billDate;

    /** 备注，可以为空 */
    private String remark;

    /** 录入时间（什么时候记的这笔账） */
    private LocalDateTime createdAt;

    /** 【表里没有这一列】连表查询带回来的账户名称，仅用于显示 */
    private String accountName;

    // ---------------------------------------------------------------
    //  getter / setter
    //  还是那三个老理由，一个都不能少：
    //    ① MyBatis 从数据库取数后，靠 setter 塞进对象
    //    ② Jackson 把对象转 JSON 返回时，靠 getter 读值
    //    ③ Jackson 把前端传来的 JSON 变成对象时，也靠 setter 塞值
    //  （从今天起你可以试着不手写这些了 —— 等我们引入 Lombok 的 @Data 就能自动生成。
    //    但在那之前，手写一遍你能更清楚它到底在干什么。）
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

    public Long getAccountId() {
        return accountId;
    }

    public void setAccountId(Long accountId) {
        this.accountId = accountId;
    }

    public Integer getType() {
        return type;
    }

    public void setType(Integer type) {
        this.type = type;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public void setAmount(BigDecimal amount) {
        this.amount = amount;
    }

    public String getCategory() {
        return category;
    }

    public void setCategory(String category) {
        this.category = category;
    }

    public LocalDate getBillDate() {
        return billDate;
    }

    public void setBillDate(LocalDate billDate) {
        this.billDate = billDate;
    }

    public String getRemark() {
        return remark;
    }

    public void setRemark(String remark) {
        this.remark = remark;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public String getAccountName() {
        return accountName;
    }

    public void setAccountName(String accountName) {
        this.accountName = accountName;
    }
}
