package com.wuweiqiu.moneylog.mapper;

import com.wuweiqiu.moneylog.entity.Bill;
import com.wuweiqiu.moneylog.vo.CategoryStats;
import com.wuweiqiu.moneylog.vo.MonthlyStats;
import com.wuweiqiu.moneylog.vo.SummaryStats;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 账单数据访问接口。
 *
 * 【跟 AccountMapper 一模一样的三条铁律，再看一遍加深印象】
 *   ① 接口只有方法声明，没有实现 —— MyBatis 动态生成代理对象
 *   ② 方法名必须和 XML 里的 id 完全一致（唯一的"接头暗号"）
 *   ③ XML 的 namespace 必须等于本接口的全限定名
 *
 *   对不上就报：
 *     Invalid bound statement (not found): ...BillMapper.xxx
 *
 * 【注意】这个文件你看着陌生，其实完全不用怕 ——
 *   它和 AccountMapper 的结构是一样的，只是换了个业务对象。
 *   这就是"套路"：新加一张表，就是把同一套模子再浇一遍。
 *   等你自己能默写出这个文件，就说明你真正掌握分层了。
 */
@Mapper
public interface BillMapper {

    /**
     * 查询所有账单（带账户名称）。
     *
     * 【为什么这里要用"连表查询"？】
     *   因为 bill 表里只有 account_id（一个数字），没有账户名字。
     *   但页面上要显示"微信钱包"，不能显示"1"。
     *   所以 SQL 里要把 account 表接进来，把 name 一起查出来。
     *   具体怎么写见 BillMapper.xml —— 那里有详细解释。
     */
    // 【Day11】加了 userId：只能查到"自己的"账单。
    //   SQL 里会加上 `WHERE b.user_id = #{userId}`。
    List<Bill> findAll(@Param("userId") Long userId);

    /**
     * 新增一笔账单。
     * 返回影响行数（写操作的常规返回类型）。
     * 对应 XML 里的 <insert id="insert">，同样会用 useGeneratedKeys 回填自增 id。
     */
    int insert(Bill bill);

    /**
     * ============================================================
     *  【Day8 新增】三个统计方法
     * ============================================================
     *  注意它们的返回值类型 —— 都是 vo 包里的类，不是 Bill。
     *  因为查询结果是"多行算出来的汇总"，不是某一行的样子。
     */

    // ============================================================
    //  【Day11】三个统计方法也都加了 userId
    // ============================================================
    //  ⚠️【这里是做数据隔离时最容易漏掉的地方，一定要记住】
    //
    //  大家改"查列表"的时候一般都不会忘（因为明显），
    //  但**统计报表极容易被漏掉** —— 它看起来"只是个汇总"，
    //  很容易让人觉得"跟归属没关系"。
    //
    //  漏掉的后果有两个，都很严重：
    //    ① 数字是错的：图表把**所有人**的账加在一起显示给你，
    //       你会看到一个自己根本没花过的金额
    //    ② 泄露隐私：别人的消费情况、收入水平，全糊在你眼前的饼图里
    //
    //  【由此得到一条通用的检查方法】
    //    做隔离时不要只盯着"最显眼的那条 SQL"，而要
    //    **把"所有会读这张表的地方"列出来，逐个过一遍**。
    //    方法是全局搜表名：grep "FROM bill" —— 搜到一处就改一处。
    //    （搜表名这个动作，比"凭记忆想"可靠得多。）
    // ============================================================

    /**
     * 按分类汇总某个月的金额。
     *
     * @param userId 当前登录用户 —— 只统计他的账单
     * @param month  月份，格式 "2026-10"
     * @param type   1=支出，2=收入
     */
    List<CategoryStats> sumByCategory(@Param("userId") Long userId,
                                      @Param("month") String month,
                                      @Param("type") Integer type);

    /**
     * 按月汇总：每个月一共收了多少、花了多少。
     * 对应 XML 里的 <select id="sumByMonth">。
     *
     * 不需要参数 —— 把有账单的月份全都统计出来。
     */
    // 【Day11】加了 userId
    List<MonthlyStats> sumByMonth(@Param("userId") Long userId);

    /**
     * 某个月的收支汇总（总支出 / 总收入 / 笔数）。
     * 对应 XML 里的 <select id="sumByMonthSummary">。
     *
     * 【为什么返回一个对象而不是 List？】
     *   因为一条聚合查询（没有 GROUP BY）永远只返回一行结果。
     *   返回 List 反而让调用方要多写"取第 0 个"的代码，还得到处判空。
     *   ——"返回值类型要如实反映数据的形状"，这是个小但重要的习惯。
     */
    // 【Day11】加了 userId
    SummaryStats sumByMonthSummary(@Param("userId") Long userId, @Param("month") String month);
}
