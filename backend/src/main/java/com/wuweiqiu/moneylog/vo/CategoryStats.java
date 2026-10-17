package com.wuweiqiu.moneylog.vo;

import java.math.BigDecimal;

/**
 * 分类统计结果 —— 装"某个分类一共花了多少"这种汇总数据。
 *
 * ============================================================
 *  【先说一个重要的新概念：为什么单独建一个 vo 包？】
 * ============================================================
 *
 *  前面几天，我们的实体类（Account、Bill）都是"表的映射"：
 *    表的一行 ↔ 对象的一个实例，基本一一对应。
 *
 *  但今天不一样了。看这条 SQL 的查询结果：
 *      SELECT category, SUM(amount) AS total, COUNT(*) AS billCount
 *      FROM bill GROUP BY category
 *
 *  结果长这样：
 *      category | total  | billCount
 *      餐饮     | 35.00  | 3
 *      交通     | 55.51  | 2
 *
 *  【这个结果不是 bill 表的任何一行！】
 *    它是"好几行账单合并算出来的一个汇总数"。
 *    bill 表里根本没有 total 这一列，也没有 billCount 这一列。
 *
 *  所以不能用 Bill 去装 —— 装不下。得给"查询结果的形状"单独建一个类。
 *  这类对象的行业叫法（都差不多，不同团队习惯不同）：
 *    · VO（View Object）     视图对象 —— 专门给"展示/返回"用的
 *    · DTO（Data Transfer Object） 传输对象 —— 跨层传递数据用的
 *    · BO（Business Object） 业务对象
 *  我们统一用 vo，你以后看到别的项目叫 dto，知道是同一类东西就行。
 *
 *  ============================================================
 *  【记住这个判断方法】
 *  ============================================================
 *    结果 = 一张表的一行            → 用 entity
 *    结果 = 多行算出来的汇总/拼接结果 → 用 vo
 *
 *  这是你以后设计新接口时每天都要做的判断。
 * ============================================================
 *
 * 【字段和别名的对应关系（又是那个"接头暗号"）】
 *    SQL 里的别名  →  这里的属性名
 *    total         →  total
 *    billCount     →  billCount
 *  名字对不上，那个字段就会是 null —— 而且不报错，静默变 null，
 *  这是很难查的一类问题（回看 Day1 讲过的 setter/映射规则）。
 */
public class CategoryStats {

    /** 分类名，如"餐饮" */
    private String category;

    /** 该分类的金额合计 */
    private BigDecimal total;

    /** 该分类有几笔（顺便查出来，前端可以显示"餐饮 3 笔"） */
    private Integer billCount;

    public String getCategory() {
        return category;
    }

    public void setCategory(String category) {
        this.category = category;
    }

    public BigDecimal getTotal() {
        return total;
    }

    public void setTotal(BigDecimal total) {
        this.total = total;
    }

    public Integer getBillCount() {
        return billCount;
    }

    public void setBillCount(Integer billCount) {
        this.billCount = billCount;
    }
}
