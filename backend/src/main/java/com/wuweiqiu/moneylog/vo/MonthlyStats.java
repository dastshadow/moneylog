package com.wuweiqiu.moneylog.vo;

import java.math.BigDecimal;

/**
 * 月度统计结果 —— 装"某个月的收入合计、支出合计"。
 *
 * 对应 SQL：
 *   SELECT DATE_FORMAT(bill_date, '%Y-%m') AS month,
 *          SUM(CASE WHEN type = 1 THEN amount ELSE 0 END) AS expense,
 *          SUM(CASE WHEN type = 2 THEN amount ELSE 0 END) AS income
 *   FROM bill
 *   GROUP BY DATE_FORMAT(bill_date, '%Y-%m')
 *
 * 【为什么要按"月"分组，而不是直接按日期分组？】
 *   按日期分组会得到"每天一条"，做整月趋势图时数据太碎。
 *   按月分组才是我们想要的"月度收支趋势"。
 */
public class MonthlyStats {

    /** 月份，格式 "2026-10"（SQL 里用 DATE_FORMAT 从日期里截出来的） */
    private String month;

    /** 当月支出合计 */
    private BigDecimal expense;

    /** 当月收入合计 */
    private BigDecimal income;

    public String getMonth() {
        return month;
    }

    public void setMonth(String month) {
        this.month = month;
    }

    public BigDecimal getExpense() {
        return expense;
    }

    public void setExpense(BigDecimal expense) {
        this.expense = expense;
    }

    public BigDecimal getIncome() {
        return income;
    }

    public void setIncome(BigDecimal income) {
        this.income = income;
    }
}
