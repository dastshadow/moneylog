package com.wuweiqiu.moneylog.controller;

import com.wuweiqiu.moneylog.common.AuthInterceptor;
import com.wuweiqiu.moneylog.common.Result;
import com.wuweiqiu.moneylog.service.StatsService;
import com.wuweiqiu.moneylog.vo.CategoryStats;
import com.wuweiqiu.moneylog.vo.MonthlyStats;
import com.wuweiqiu.moneylog.vo.SummaryStats;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 统计接口层。
 *
 * ============================================================
 *  【今天终于用上了第三种传参方式：@RequestParam】
 * ============================================================
 *  到目前为止我们用过两种：
 *    @RequestBody   从请求体拿数据（新增/修改时传一整套数据）
 *    @PathVariable  从 URL 路径里拿（指定某一条记录，如 /api/account/6）
 *
 *  今天这个接口需要"筛选条件"，长这样：
 *      GET /api/stats/category?month=2026-10&type=1
 *                               └──── 问号后面的就是"查询参数" ────┘
 *
 *  取它的注解就是 @RequestParam。
 *
 *  【怎么选？一张表说清】
 *  ┌──────────────────┬───────────────────────────┬─────────────────┐
 *  │ 参数放哪          │ 例子                       │ 注解            │
 *  ├──────────────────┼───────────────────────────┼─────────────────┤
 *  │ URL 问号后面      │ /list?month=2026-10&type=1 │ @RequestParam   │
 *  │ 请求体里          │ {"name":"中国银行"}        │ @RequestBody    │
 *  │ URL 路径里        │ /api/account/6             │ @PathVariable   │
 *  └──────────────────┴───────────────────────────┴─────────────────┘
 *
 *  【判断口诀】
 *    · "筛选用什么条件"        → 问号后面（@RequestParam）
 *    · "要提交/保存的数据"      → 请求体（@RequestBody）
 *    · "指向某一条具体记录"     → 路径里（@PathVariable）
 *
 *  所以：查列表、查统计（带筛选条件）用第一个；
 *        新增、修改用第二个；删单条、改单条用第三个。
 *
 *  【两个可选参数的小知识】
 *    @RequestParam 有个 required 属性，默认是 true（必填）。
 *    想让它可选就写 @RequestParam(required = false)。
 *    如果没传、又是必填，Spring 会直接返回 400 错误
 *    （这就是 Day5/Day6 讲的"HTTP 层面的问题用 4xx"）。
 *    但我们在 Service 里也做了校验并给出更友好的中文提示 ——
 *    这是"框架兜底 + 业务友好提示"的又一次配合。
 */
@RestController
@RequestMapping("/api/stats")
public class StatsController {

    @Autowired
    private StatsService statsService;

    /**
     * 按分类统计。
     * 访问方式：GET /api/stats/category?month=2026-10&type=1
     *
     * @param month 月份，如 "2026-10"
     * @param type  1=支出，2=收入
     *
     * 返回示例：
     *   {"code":0,"msg":"ok","data":[
     *      {"category":"购物","total":88.00,"billCount":1},
     *      {"category":"餐饮","total":35.00,"billCount":1},
     *      {"category":"交通","total":25.50,"billCount":1}]}
     */
    @GetMapping("/category")
    public Result<List<CategoryStats>> category(
            @RequestAttribute(AuthInterceptor.ATTR_USER_ID) Long userId,
            @RequestParam String month,
            @RequestParam Integer type) {
        // ============================================================
        //  【Day11】这个接口有 3 个参数，来自 2 个不同的地方 ——
        //  正好说明了"可信"和"不可信"的差别：
        // ============================================================
        //    userId ← @RequestAttribute  服务器认证出的身份（可信）
        //                                **统计"谁"的数据，由它决定，客户端说不上话**
        //    month / type ← @RequestParam  客户端的筛选条件（不可信）
        //                                **但它们只是"筛"，改大了也只能筛自己的数据**
        //
        //  这就是"不可信"不一定要命的例子：客户端能改 month，
        //  但它改来改去，userId 始终是服务器给的 ——
        //  所以最坏情况也只是"他自己看错月份"，伤不到别人。
        //  **真正危险的是"能决定归属"的参数**，比如 userId、accountId。
        // ============================================================
        return Result.ok(statsService.categoryStats(userId, month, type));
    }

    /**
     * 月度收支趋势（所有有账单的月份）。
     * 访问方式：GET /api/stats/monthly
     *
     * 返回示例：
     *   {"data":[{"month":"2026-10","expense":177.51,"income":8600.01}]}
     */
    @GetMapping("/monthly")
    public Result<List<MonthlyStats>> monthly(
            @RequestAttribute(AuthInterceptor.ATTR_USER_ID) Long userId) {
        // 【Day11】以前是"所有人的月度趋势"，现在是"我的"
        return Result.ok(statsService.monthlyStats(userId));
    }

    /**
     * 某个月的收支汇总。
     * 访问方式：GET /api/stats/summary?month=2026-10
     *
     * 返回示例（balance 是 Java 里算出来的结余）：
     *   {"data":{"totalExpense":177.51,"totalIncome":8600.01,
     *            "balance":8422.50,"billCount":6}}
     */
    @GetMapping("/summary")
    public Result<SummaryStats> summary(
            @RequestAttribute(AuthInterceptor.ATTR_USER_ID) Long userId,
            @RequestParam String month) {
        // 【Day11】"我的"月度汇总
        return Result.ok(statsService.monthSummary(userId, month));
    }
}
