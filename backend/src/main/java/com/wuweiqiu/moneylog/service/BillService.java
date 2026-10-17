package com.wuweiqiu.moneylog.service;

import com.wuweiqiu.moneylog.entity.Account;
import com.wuweiqiu.moneylog.entity.Bill;
import com.wuweiqiu.moneylog.mapper.AccountMapper;
import com.wuweiqiu.moneylog.mapper.BillMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * 账单业务层。
 *
 * 【对比 AccountService，这一层今天多干了一件事】
 *   它同时注入了 AccountMapper —— 因为记账时要先确认"这个账户真的存在"。
 *
 * 【为什么这里注入的是 AccountMapper 而不是 AccountService？】
 *   因为只需要"按 id 查一下账户"这一个动作，直接用 Mapper 最省事。
 *
 *   但在更规范的大项目里，通常会注入 AccountService，
 *   理由是：账户相关的业务规则应该集中在 AccountService 一处。
 *   如果别的 Service 都直接调 Mapper 绕过它，那些规则就形同虚设了。
 *   （想象一下：AccountService 里规定"锁定状态的账户不允许操作"，
 *     但 BillService 直接调 Mapper 查，就能绕过这条规则。）
 *
 *   现在这两个类还很简单，两种做法都行。
 *   等你以后遇到"规则被绕过"的 bug 时，会想起这段话。
 */
@Service
public class BillService {

    @Autowired
    private BillMapper billMapper;

    @Autowired
    private AccountMapper accountMapper;

    /**
     * 查询所有账单（含账户名称）。
     * 只转发，因为"查列表"没有任何业务规则需要处理。
     */
    public List<Bill> findAll(Long userId) {
        return billMapper.findAll(userId);
    }

    /**
     * 新增一笔账单 —— 记一笔账。
     *
     * ============================================================
     *  【Day7 核心：@Transactional 事务】
     * ============================================================
     *
     *  这个方法要做两件事：
     *     ① 往 bill 表插一条账单
     *     ② 改 account 表的余额
     *
     *  加了这个注解之后，这两步会变成一个"整体"：
     *     · 两步都成功 → 一起生效（提交）
     *     · 任意一步失败 → 一起撤销（回滚），回到调用之前的样子
     *
     *  ============================================================
     *  为什么必须加？我们刚才已经用实验证明过两遍了：
     *  ============================================================
     *  实验1：故障发生在两步之后
     *    → 账单写入了、钱也扣了，但用户看到"系统繁忙"以为失败
     *    → 用户重试就会重复扣款
     *
     *  实验2：故障发生在两步中间
     *    → 账单写入了（8条），但钱没扣（还是2000）
     *    → 数据库里出现一笔"没收钱的账单"，账永久不平
     *
     *  加了 @Transactional 之后，同样的故障再触发一次：
     *    → 账单也不会留下来，余额也没变 → 数据库干干净净
     *
     *  ============================================================
     *  【事务的四个特性 ACID —— 面试常问，也是理解的基础】
     *  ============================================================
     *    A 原子性（Atomicity）：要么全做，要么全不做。 ← 今天重点体会这个
     *    C 一致性（Consistency）：数据从一个正确状态变到另一个正确状态
     *    I 隔离性（Isolation）：并发的事务之间互不干扰
     *    D 持久性（Durability）：提交之后就不会丢（就算断电）
     *
     *  今天你亲眼看到的是 **A（原子性）**：
     *    两个操作被打包成一个"原子"，不能切开。
     *
     *  ============================================================
     *  【几个必须知道的细节】
     *  ============================================================
     *
     *  ① 默认只对"运行时异常"回滚，不对"检查型异常"回滚
     *     · RuntimeException（包括我们的 IllegalArgumentException）
     *       → 默认回滚 ✓
     *     · Exception 里的非 RuntimeException（比如 IOException）
     *       → 默认**不回滚**！需要写成 @Transactional(rollbackFor = Exception.class)
     *
     *     【我们项目刚好没问题】：所有业务校验抛的都是 IllegalArgumentException，
     *     它是 RuntimeException 的子类，所以默认就能回滚。
     *     但这是个经典坑 —— 如果哪天换成抛出受检异常，事务会静默失效，
     *     看起来"事务加了，但没起作用"，非常难查。
     *
     *  ② 这个方法必须是 public
     *     Spring 的事务是靠"代理对象"实现的（跟 MyBatis 的 Mapper 代理类似）。
     *     它的工作原理是：调用方法时先开启事务，执行完再提交。
     *     如果方法不是 public，代理拦不住，事务就失效了。
     *
     *  ③ 【进阶坑，先知道有这回事】同类内部调用不走代理
     *     如果这个类里 methodA 调 methodB，而 methodB 上有 @Transactional，
     *     直接 `this.methodB()` 是不会开启事务的（因为绕过了代理对象）。
     *     这个坑等你以后遇到再说，现在记住"@Transactional 靠代理生效"就够了。
     *
     *  ④ 加在哪里？
     *     一般加在 Service 的方法上。
     *     为什么？因为 Service 是"业务规则"的所在地，
     *     **一次完整业务操作**的边界就在这里。
     *     不该加在 Controller（粒度太粗，会把查询也算进去），
     *     也不该加在 Mapper（那里的每个方法只是一小步，不是完整业务）。
     *
     *  ============================================================
     *  【还需要 import 这一行】
     *     import org.springframework.transaction.annotation.Transactional;
     * ============================================================
     */
    @Transactional
    public void create(Long userId, Bill bill) {

        // ============================================================
        //  ⭐【Day11】第一件事：把归属**强制**设成当前登录用户
        // ============================================================
        //  放在方法最前面，是为了保证后面任何一步用到的 bill.userId
        //  都是可信的值。
        //
        //  为什么必须覆盖？
        //    Bill 现在有了 userId 字段和 setUserId()，Jackson 会把
        //    请求体里的 "userId" 自动绑进来。如果不管它，坏人就能：
        //        POST /api/bill/create
        //        {"accountId":1, "amount":1, "userId":2, ...}
        //                                    ↑ 把账记到别人头上
        //
        //    这就是 Day9 讲的 Mass Assignment 漏洞 ——
        //    同一个坑，在账单这个入口也存在，所以每个入口都要堵。
        //    （教训：**加一个字段，就要想一遍"客户端能不能塞它进来"。**）
        // ============================================================
        bill.setUserId(userId);

        // 校验1：必须指定账户
        if (bill.getAccountId() == null) {
            throw new IllegalArgumentException("请选择所属账户");
        }

        // 校验2：账户必须真实存在，并且要把账户对象拿到手
        // ============================================================
        //  Day6 时这里只判断了"存不存在"，今天要把结果**留下来**，
        //  因为下面还要用它的余额来判断"钱够不够花"。
        //  （这就是为什么前面写 `if (accountMapper.findById(...) == null)`，
        //    现在改成先赋值给变量再判断 —— 需求变了，代码跟着变。）
        // ============================================================
        // ============================================================
        //  【Day11 这一行特别重要】查账户时带上 userId
        // ============================================================
        //  以前是 findById(accountId) —— 只要账户存在就能用。
        //  现在多传了 userId，于是"别人的账户"在这里就查不到 → 报错。
        //
        //  【这一行挡住了一类很隐蔽的攻击】
        //    假设没有这个条件，坏人只要知道你的账户 id，就能把
        //    **他自己的支出记到你的账户上**：
        //        POST /api/bill/create
        //        {"accountId": 1, "amount": 9999, ...}   ← 1 是你的微信钱包
        //    结果：这 9999 元从**你的余额**里扣掉了 ——
        //    钱被扣了，账还是记在他名下。你只会发现余额莫名少了钱。
        //
        //    这类漏洞叫"水平越权"。注意：光靠"页面只显示自己的账户"
        //    是挡不住的 —— 因为接口可以直接调（Day4 学过这件事）。
        //    所以校验必须做在**后端**，而且要做在**数据这一层**。
        // ============================================================
        Account account = accountMapper.findById(bill.getAccountId(), userId);
        if (account == null) {
            throw new IllegalArgumentException("所选账户不存在，请重新选择");
        }

        // 校验3：类型只能是 1（支出）或 2（收入）
        //   注意这里用的是 Bill 类里定义的常量，而不是直接写数字 1 和 2。
        //   【为什么？】那种直接写在代码里的数字叫"魔法数字"——
        //   半年后你自己看到 `if (type == 1)`，会想"1 是啥来着？"
        //   用 TYPE_EXPENSE 一眼就懂，而且以后要改含义只改一处。
        if (bill.getType() == null
                || (bill.getType() != Bill.TYPE_EXPENSE && bill.getType() != Bill.TYPE_INCOME)) {
            throw new IllegalArgumentException("账单类型不正确");
        }

        // 校验4：金额必须大于 0
        //   注意：判断"大于 0"要分两步写，不能只写 compareTo > 0
        //     · 第一步：null 判断（没传金额，compareTo 会直接空指针崩溃）
        //     · 第二步：数值比较
        //   这是所有比较操作的固定套路 —— **先用 null 判断兜住，再比数值**。
        if (bill.getAmount() == null || bill.getAmount().compareTo(BigDecimal.ZERO) <= 0) {
            // 用了 <= 而不是 < ：金额为 0 的账单没有意义，也要拦掉
            throw new IllegalArgumentException("金额必须大于 0");
        }

        // 校验5：分类不能为空（沿用 trim() 的老办法，挡住纯空格）
        if (bill.getCategory() == null || bill.getCategory().trim().isEmpty()) {
            throw new IllegalArgumentException("请选择或填写分类");
        }
        if (bill.getCategory().trim().length() > 20) {
            // 对齐数据库的 VARCHAR(20)
            throw new IllegalArgumentException("分类不能超过 20 个字");
        }

        // 校验6：账单日期不能为空
        if (bill.getBillDate() == null) {
            throw new IllegalArgumentException("请选择账单日期");
        }
        // 顺便拦一下"未来日期"—— 记账记的是已经发生的钱
        //   isAfter(...) 是 LocalDate 自带的方法：判断"是不是在某个日期之后"
        if (bill.getBillDate().isAfter(LocalDate.now())) {
            throw new IllegalArgumentException("账单日期不能晚于今天");
        }

        // 校验7：备注长度（备注可以为空，所以只判断"有值的时候"超没超）
        if (bill.getRemark() != null && bill.getRemark().trim().length() > 200) {
            throw new IllegalArgumentException("备注不能超过 200 个字");
        }

        // ============================================================
        //  校验8【Day7 新增】：支出时，余额必须够花
        // ============================================================
        //  【为什么这条规则必须放在这里，而不是放在数据库里？】
        //    因为这是一条**业务规则**，不是数据完整性约束。
        //    数据库只保证"数据格式正确"（比如 balance 不能是 null），
        //    但"花钱不能超过余额"是业务上的规定，数据库管不了。
        //    ——这正是 Service 这一层存在的意义：**放业务规则**。
        //
        //  【为什么只检查支出，不检查收入？】
        //    收入是往口袋里加钱，加多少都不会"超"。
        //    只有支出才需要担心"钱不够"。
        // ============================================================
        if (bill.getType() == Bill.TYPE_EXPENSE
                && account.getBalance().compareTo(bill.getAmount()) < 0) {
            // 提示里带上当前余额，用户一眼就知道差多少，不用自己去翻
            throw new IllegalArgumentException(
                    "账户余额不足，当前余额 ¥" + account.getBalance() + "，本次需要 ¥" + bill.getAmount());
        }

        // ============================================================
        //  校验全部通过 —— 开始动数据库（两个动作，必须一起来）
        // ============================================================
        //
        //  【今天的核心】"记一笔账"实际上要做两件事：
        //     ① 往 bill 表插一条账单记录
        //     ② 把 account 表的余额改掉
        //
        //  这两件事必须**同生共死**：
        //    · 两步都成功 → 账记上了、钱也扣了 ✓
        //    · 任意一步失败 → 两步都要撤销，回到记账之前的样子
        //
        //  为什么？想想只成功一半的后果：
        //    · 只成功① → 有账单记录，但钱没扣 → 用户白记了（账不平）
        //    · 只成功② → 钱扣了，但没账单记录 → 用户的钱凭空少了 💀
        //  两个都是灾难。
        //
        //  保证"同生共死"的技术就叫**事务**。
        //  现在**先不加事务**，我们来看看会发生什么 —— 这是今天最重要的实验。
        // ============================================================

        // 第①步：写账单
        billMapper.insert(bill);

        // 第②步：改账户余额
        //   计算"变动量"：收入是正数，支出是负数
        //     negate() 是 BigDecimal 的方法，作用是"取相反数"（35 → -35）
        BigDecimal delta = bill.getType() == Bill.TYPE_INCOME
                ? bill.getAmount()
                : bill.getAmount().negate();

        // 【Day11】updateBalance 也加上 userId —— 纵深防御，
        //   哪怕前面的 findById 已经确认过归属，这一层也自己守一遍。
        accountMapper.updateBalance(bill.getAccountId(), userId, delta);
    }
}
