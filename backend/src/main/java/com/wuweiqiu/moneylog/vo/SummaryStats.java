package com.wuweiqiu.moneylog.vo;

import java.math.BigDecimal;

/**
 * 某个月的收支汇总 —— 页面上那几个大数字（总支出 / 总收入 / 结余）就用它。
 *
 * 【注意 balance 这个字段的特殊性】
 *   它**不在 SQL 的查询结果里**，是 Java 里算出来的：
 *       balance = income - expense
 *
 *   为什么不在 SQL 里直接算？
 *     用 SQL 也能算（SUM(type=2) - SUM(type=1)），但"结余"是**业务概念** ——
 *     它属于"业务规则"，按 Day1 定的分工，业务规则该放在 Service 层。
 *
 *   这也算一个小小的设计取舍：
 *     · 放 SQL 里算 → 少写一行 Java 代码，但业务规则散进了 SQL
 *     · 放 Service 里算 → 多一行代码，但"结余怎么算"这件事只在一个地方
 *   我们选后者。等你以后遇到"同一条计算公式在多处 SQL 里各写了一遍、
 *   改的时候漏了一处"的问题，就会明白这个选择的价值。
 */
public class SummaryStats {

    /** 当月支出合计 */
    private BigDecimal totalExpense;

    /** 当月收入合计 */
    private BigDecimal totalIncome;

    /** 结余 = 收入 - 支出（Java 里算出来的，不是 SQL 查出来的） */
    private BigDecimal balance;

    /** 当月账单笔数 */
    private Integer billCount;

    public BigDecimal getTotalExpense() {
        return totalExpense;
    }

    public void setTotalExpense(BigDecimal totalExpense) {
        this.totalExpense = totalExpense;
    }

    public BigDecimal getTotalIncome() {
        return totalIncome;
    }

    public void setTotalIncome(BigDecimal totalIncome) {
        this.totalIncome = totalIncome;
    }

    public BigDecimal getBalance() {
        return balance;
    }

    public void setBalance(BigDecimal balance) {
        this.balance = balance;
    }

    public Integer getBillCount() {
        return billCount;
    }

    public void setBillCount(Integer billCount) {
        this.billCount = billCount;
    }
}
