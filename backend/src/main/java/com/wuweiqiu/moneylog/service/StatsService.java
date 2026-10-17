package com.wuweiqiu.moneylog.service;

import com.wuweiqiu.moneylog.entity.Bill;
import com.wuweiqiu.moneylog.mapper.BillMapper;
import com.wuweiqiu.moneylog.vo.CategoryStats;
import com.wuweiqiu.moneylog.vo.MonthlyStats;
import com.wuweiqiu.moneylog.vo.SummaryStats;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 统计业务层。
 *
 * ============================================================
 *  【单独建一个 StatsService，而不是塞进 BillService】
 * ============================================================
 *  统计功能也是操作 bill 表，为什么不放在 BillService 里？
 *
 *  因为它们"服务的对象"不同：
 *    · BillService  —— 管账单的增删改查（**改变数据**）
 *    · StatsService —— 管数据的汇总分析（**只读，不改变任何东西**）
 *
 *  分开的好处：
 *    ① 职责清楚：以后要改统计逻辑，直接来这个文件，
 *       不用在一个几百行的 BillService 里翻找
 *    ② 便于调整：统计以后可能要加缓存（因为算起来费时间），
 *       缓存只会加在这个类上，不会影响记账功能
 *    ③ 好招人接手：新人看到"Stats"就知道这里面是统计，不用读代码猜
 *
 *  【这也体现了分层的可扩展性】
 *    加一个新功能，不是"往已有文件里塞更多方法"，
 *    而是"按职责新建一个专门的文件"。
 *    判断标准很简单：**这个新功能，和现有功能是同一种职责吗？**
 *      同 → 加方法；不同 → 新建类。
 * ============================================================
 */
@Service
public class StatsService {

    @Autowired
    private BillMapper billMapper;

    /**
     * 按分类统计某个月的金额。
     *
     * @param month 月份，格式 "2026-10"
     * @param type  1=支出，2=收入
     */
    public List<CategoryStats> categoryStats(Long userId, String month, Integer type) {
        // 【参数校验】month 不能为空
        if (month == null || month.trim().isEmpty()) {
            throw new IllegalArgumentException("请选择要统计的月份");
        }
        // 【参数校验】type 只能是 1 或 2（复用 Day6 定的那两个常量）
        if (type == null || (type != Bill.TYPE_EXPENSE && type != Bill.TYPE_INCOME)) {
            throw new IllegalArgumentException("统计类型不正确");
        }

        // 【顺便校验格式】必须是 "2026-10" 这种
        //   为什么要在这里校验？因为 month 会直接用于 SQL 的字符串比较。
        //   虽然 #{month} 是占位符、没有 SQL 注入风险，
        //   但如果前端传个 "abcd" 过来，SQL 不报错、只是查不到数据，
        //   用户会看到"这个月没有任何账单"——这是**静默的错误**，
        //   比明确报错更难排查。
        //   提前拦住并说清原因，是更好的做法。
        if (!month.matches("\\d{4}-\\d{2}")) {
            throw new IllegalArgumentException("月份格式不正确，应为 2026-10 这种形式");
        }

        // 【Day11】把 userId 一起带进 SQL —— 统计也只能统计"我的"
        return billMapper.sumByCategory(userId, month, type);
    }

    /**
     * 按月统计收支趋势（把所有有账单的月份都算出来）。
     * 不需要参数，也没有业务规则，直接转发。
     */
    public List<MonthlyStats> monthlyStats(Long userId) {
        // 【Day11】以前是"统计所有人的所有月份"，现在限定成"我的"
        return billMapper.sumByMonth(userId);
    }

    /**
     * 某个月的收支汇总。
     *
     * 【这里演示了"业务计算放 Service"】
     *   SQL 只查出 totalExpense 和 totalIncome 两个数字，
     *   而"结余 = 收入 - 支出"这一步在 Java 里算。
     *
     *   这样做的意义：**"结余怎么算"这条规则只存在于这一个地方**。
     *   以后如果业务改成"结余要扣掉手续费"，只改这一行 ——
     *   要是当初写在 SQL 里、而且被好几条 SQL 各写了一遍，
     *   就得挨个去改，漏一个就出错。
     */
    public SummaryStats monthSummary(Long userId, String month) {
        if (month == null || month.trim().isEmpty()) {
            throw new IllegalArgumentException("请选择要统计的月份");
        }
        if (!month.matches("\\d{4}-\\d{2}")) {
            throw new IllegalArgumentException("月份格式不正确，应为 2026-10 这种形式");
        }

        SummaryStats summary = billMapper.sumByMonthSummary(userId, month);

        // 【防御性编程】虽然 SQL 里用了 IFNULL 兜底、COUNT 也不会是 null，
        //   但"假设外部给的东西一定不为 null"是危险的。
        //   summary 这个对象本身是 MyBatis 新建的（不会是 null），
        //   但保险起见判一下 —— 万一以后有人改了 SQL、或者换了数据库呢。
        if (summary == null) {
            summary = new SummaryStats();
        }

        // 算结余（收入 - 支出）
        //   两个数都可能为 null（虽然 SQL 兜了 0，但 Java 里再兜一层更稳）
        java.math.BigDecimal expense = summary.getTotalExpense() == null
                ? java.math.BigDecimal.ZERO : summary.getTotalExpense();
        java.math.BigDecimal income = summary.getTotalIncome() == null
                ? java.math.BigDecimal.ZERO : summary.getTotalIncome();

        summary.setBalance(income.subtract(expense));

        return summary;
    }
}
