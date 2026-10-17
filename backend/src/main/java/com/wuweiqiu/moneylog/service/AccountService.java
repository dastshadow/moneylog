package com.wuweiqiu.moneylog.service;

import com.wuweiqiu.moneylog.entity.Account;
import com.wuweiqiu.moneylog.mapper.AccountMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 账户业务层 —— 承载"业务规则"，不直接写 SQL。
 *
 * 【为什么要有 Service 这一层？】
 *   新手常问："Controller 直接调 Mapper 不就行了？多一层干嘛？"
 *   今天这个例子确实简单，Service 看起来只是"转发"了一下，显得多余。
 *   但它的价值在业务变复杂时才会显现，举几个马上就会遇到的例子：
 *     - 转账：要"扣 A 的余额 + 加 B 的余额"，这两步必须一起成功或一起失败（事务）
 *     - 新增账单：要校验金额 > 0、校验分类是否存在、还要更新账户余额
 *     - 删除账户：要先查这个账户下有没有账单，有就不许删
 *   这些规则属于"业务"，既不该写在 Controller（那是管 HTTP 的），
 *   也不该写在 Mapper（那是管 SQL 的）。所以单独一层放规矩。
 *
 *   一句话记住分工：
 *      Controller 管"怎么收发"（HTTP 参数、返回格式）
 *      Service    管"对不对、能不能"（业务规则、事务）
 *      Mapper     管"怎么取"（SQL 语句）
 */
@Service
public class AccountService {

    /**
     * @Autowired 让 Spring 自动把 AccountMapper 的代理对象"注入"进来。
     *
     * 注入过程是这样的：
     *   1) 启动时 MyBatis 扫描到 @Mapper，为 AccountMapper 生成了代理对象
     *   2) 这个代理对象被放进 Spring 容器（容器就像一个大 Map：类型 -> 对象）
     *   3) Spring 看到 @Autowired，就按类型去容器里找，找到就赋值给这个字段
     * 如果找不到，启动时会报：No qualifying bean of type 'AccountMapper'
     *   —— 见到这个报错，第一反应就是"Mapper 没被扫描到"。
     */
    @Autowired
    private AccountMapper accountMapper;

    /**
     * 查询【当前登录用户的】所有账户。
     * 对应 AccountMapper.xml 里的 <select id="findAll">。
     *
     * 【Day11】这个方法看起来只是"多传了个参数"，但意义完全变了：
     *   以前叫"查所有账户"，现在叫"查我的账户"。
     *
     * 【注意 userId 是怎么一路传下来的】
     *   拦截器验签 → request 属性 → Controller(@RequestAttribute)
     *     → Service(本方法) → Mapper → SQL 的 WHERE
     *   **全程它都不来自客户端，所以可信。**
     */
    public List<Account> findAll(Long userId) {
        return accountMapper.findAll(userId);
    }

    /**
     * 新增一个账户。
     *
     * 今天这一层终于不"只是转发"了 —— 它在中间干了一件事：
     * 把 balance 为空的情况兜住（默认给 0）。
     *
     * 为什么这件事该放在 Service，而不是 Controller 或 Mapper？
     *   · Controller 的职责是"收发 HTTP"，不该关心业务默认值
     *   · Mapper 的职责是"写 SQL"，不该管 Java 对象里字段有没有值
     *   · "余额没填就算 0" 属于**业务规则** → 正好是 Service 该干的事
     *
     * 这就是分层的实际意义：每加一条业务规则，你就知道该往哪个文件里写，
     * 不用满项目乱找，也不会把不相干的代码混在一起。
     *
     * 【顺便注意 insert 的返回值我们没接】
     *   accountMapper.insert(account) 返回了"影响行数"，这里没有接收。
     *   因为 SQL 真出错时 MyBatis 会直接抛异常，所以"返回值不是 1"这种情况
     *   目前不需要额外处理。但正式项目里通常会接住它并做判断，Day4 我们再补。
     */
    public void create(Long userId, Account account) {
        // 校验（抽到 validate 方法里了，因为"改"也要用同一套规则）
        validate(account);

        // 业务规则：余额没给就按 0 处理
        if (account.getBalance() == null) {
            account.setBalance(java.math.BigDecimal.ZERO);
        }

        // ============================================================
        //  ⭐⭐【Day11 最关键的一行】强制覆盖 userId ⭐⭐
        // ============================================================
        //  为什么必须有这一行？因为**下面这种攻击是真的能成功的**：
        //
        //      POST /api/account/create
        //      {"name":"伪装账户", "balance":1, "userId":2}
        //                                   ↑ 想把账户写到别人名下
        //
        //  原因：Account 类现在有了 userId 字段和 setUserId()，
        //  Jackson 会自动把请求体里的 "userId" 绑进对象 ——
        //  这就是 Day9 讲过的 **Mass Assignment（批量赋值）漏洞**。
        //  当时是"理论上可能发生"，今天它变成了"真的会发生"。
        //
        //  解法就是这一行：**不管前端传了什么，一律盖掉。**
        //  于是请求体里那个 "userId":2 就等于白写了。
        //
        //  【一条通用原则，请记牢】
        //    凡是由**服务器决定**的字段（归属 user_id、创建时间、
        //    状态、价格、权限等级……），在写库之前都必须强制覆盖。
        //    因为客户端传上来的任何东西都不可信 ——
        //    这是 Day4 定下的规矩，今天它第一次真的会伤人。
        // ============================================================
        account.setUserId(userId);

        accountMapper.insert(account);
    }

    /**
     * 修改账户。
     *
     * ============================================================
     *  【今天最重要的设计点】为什么参数是 (Long id, Account account) 两个？
     * ============================================================
     *  因为这两个信息来自**不同的地方**：
     *    · id      —— 来自 URL 路径（DELETE /api/account/6 里的 6）
     *    · account —— 来自请求体（JSON 里的 name 和 balance）
     *
     *  而数据库的 UPDATE 语句需要它们**合在一起**才能工作：
     *      UPDATE account SET name=?, balance=? WHERE id=?
     *                       ↑ 来自请求体         ↑ 来自 URL
     *
     *  所以在 Service 里做了一件事：**把 URL 里的 id 塞进对象**：
     *      account.setId(id);
     *  这样 XML 里写 WHERE id = #{id} 才能取到值。
     *
     *  【如果忘了 setId 会怎样？】
     *    id 是 null，SQL 变成 WHERE id = null —— 一条记录都匹配不上，
     *    返回 "影响 0 行"，更新静默失败。而前端可能显示"修改成功"。
     *    这是很隐蔽的 bug，所以我们在下面接了返回值并判断。
     * ============================================================
     */
    public void update(Long id, Long userId, Account account) {
        // 改的时候，校验规则和新增完全一样
        //   —— 不能因为是"修改"就放松要求，否则用户先建个合法账户、
        //      再改成空名字，脏数据照样能进来。
        validate(account);

        if (account.getBalance() == null) {
            account.setBalance(java.math.BigDecimal.ZERO);
        }

        // 【先查在不在】—— Day11 改成查"**我的**这条"在不在
        //   以前是 findById(id)；现在多了 userId 条件，
        //   于是"别人的账户"在这里就查不到 → 直接报错，后面的 UPDATE 根本不会执行。
        //
        //   【注意提示语】故意说"账户不存在"，而不是"这不是你的账户"。
        //   因为告诉攻击者"这条记录存在、但不属于你"，等于**泄露了信息** ——
        //   他就能据此推断出系统里有哪些 id 是有效的。
        //   安全提示语的原则：**能含糊就含糊，别给攻击者免费情报。**
        if (accountMapper.findById(id, userId) == null) {
            throw new IllegalArgumentException("账户不存在，可能已被删除");
        }

        // 把 URL 里的 id 塞进对象，UPDATE 的 WHERE 才有条件可用
        account.setId(id);

        // 【Day11】同样强制覆盖归属。
        //   不覆盖的话，理论上存在"改自己账户的同时把它转移给别人"的可能
        //   （请求体里塞 "userId":2 即可）。同一个漏洞，换了个入口。
        account.setUserId(userId);

        // 【接住返回值】这就是 Day3 埋的那个伏笔 —— 当时说"影响行数"以后会用到
        int rows = accountMapper.update(account);
        if (rows != 1) {
            // 正常情况下走不到这里（上面 findById 已经确认存在了）。
            // 但万一有并发（你查完的瞬间别人把它删了），这里能兜住。
            // 这叫"防御性编程"：不假设中间没有变故。
            throw new IllegalArgumentException("修改失败，请刷新后重试");
        }
    }

    /**
     * 删除账户。
     *
     * 【为什么删之前也要先查？】
     *   因为"删除不存在的记录"这个动作，数据库**不会报错**，
     *   它只是报告"影响 0 行"。
     *   如果不检查，用户点删除一个已经被删掉的账户，
     *   接口会返回"删除成功" —— 用户在骗自己。
     *   所以标准做法是明确的：**查不到就报错，让用户知道真相**。
     *
     *  （另一种技术路线是直接看 deleteById 的返回值是不是 1，
     *    那样可以省掉一次 findById 查询，性能更好。
     *    但先查一次的好处是：能给前端更明确的错误原因。
     *    等以后数据量大、性能敏感时，你可以改成只看返回值。）
     */
    public void delete(Long id, Long userId) {
        // 【Day11】查询和删除**都**要带上 userId
        //   只改其中一处是不够的：如果查询不带 userId、删除带，
        //   就会先"确认存在"、再"删除失败"，报错信息会变得莫名其妙。
        //   原则：**同一个操作的每一步都要用同一套归属条件。**
        if (accountMapper.findById(id, userId) == null) {
            throw new IllegalArgumentException("账户不存在，可能已被删除");
        }

        accountMapper.deleteById(id, userId);
    }

    /**
     * 【私有方法】账户数据的通用校验规则。
     *
     * ============================================================
     *  为什么要把校验抽出来？（这是一个真实的重构动作）
     * ============================================================
     *  情况是这样的：新增要校验，修改也要校验，而且规则完全一样。
     *
     *  如果不抽出来，就会变成"复制粘贴两份一模一样的校验代码"。
     *  那种写法有个致命问题：
     *    以后规则变了（比如名称上限从 50 改成 30），
     *    你改了新增的那份、忘了改修改的那份 →
     *    有人就能通过"先建个合法的，再改成 31 个字"绕过限制。
     *
     *  所以有一条通用原则：
     *    **重复的代码是 bug 的温床 —— 同一个规则只能有一处实现。**
     *
     *  private 表示"只给本类内部用"。校验规则是业务内部的事，
     *  不该暴露给 Controller 调用，所以是私有。
     *
     *  ============================================================
     *   下面三条规则逐个讲
     *  ============================================================
     */
    private void validate(Account account) {
        // 校验1：账户名称不能为空
        if (account.getName() == null || account.getName().trim().isEmpty()) {
            // 注意这里用了 trim()：
            //   trim() 会去掉字符串首尾的空白字符。
            //   所以 "     "（纯空格）经过 trim() 之后变成 ""，
            //   也能被 isEmpty() 拦住 —— 这叫"空白字符串"，比空字符串更隐蔽。
            //   如果没有 trim()，纯空格就能蒙混过关（就是我们攻击④的情况）。
            throw new IllegalArgumentException("账户名称不能为空");
        }

        // 校验2：账户名称长度限制
        //   为什么要限？因为数据库里 name 是 VARCHAR(50)。
        //   如果不拦，超长内容会直接报数据库错误，那时候用户看到的就是
        //   一个 500 和一堆英文堆栈 —— 体验很差，而且可能泄露表结构。
        //   在业务层提前拦住，就能给出人话提示。
        if (account.getName().trim().length() > 50) {
            throw new IllegalArgumentException("账户名称不能超过 50 个字");
        }

        // 校验3：余额不能为负数
        if (account.getBalance() != null && account.getBalance().compareTo(java.math.BigDecimal.ZERO) < 0) {
            // 【注意】BigDecimal 比较大小不能用 < > 运算符，必须用 compareTo()。
            //   compareTo(x) 返回：负数表示小于 x，0 表示等于，正数表示大于。
            //   为什么？因为 BigDecimal 是对象，不是基本类型，
            //   用 == 比的是"是不是同一个对象"，用 compareTo 比的才是"数值大小"。
            //   这是 BigDecimal 最经典的坑之一。
            throw new IllegalArgumentException("余额不能为负数");
        }
    }
}
