package com.wuweiqiu.moneylog.mapper;

import com.wuweiqiu.moneylog.entity.Account;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.math.BigDecimal;
import java.util.List;

/**
 * 账户数据访问接口 —— 只负责"取数据"，不写业务规则。
 *
 * 【关键认知：这个接口没有实现类！】
 *   你没看错，它只有方法声明，没有方法体。那为什么能调用？
 *   因为 MyBatis 在启动时会给它**动态生成一个代理对象**（JDK 动态代理），
 *   这个代理对象知道：调用 findAll() 时，就去 AccountMapper.xml 里找
 *   id="findAll" 的那个 <select>，把它里面的 SQL 发给数据库。
 *
 *   所以有一条铁律：**接口里的方法名，必须和 XML 里的 id 一一对应**。
 *   名字对不上，或者 XML 没被扫描到，就会报：
 *       Invalid bound statement (not found): com.wuweiqiu.moneylog.mapper.AccountMapper.findAll
 *   这个报错极其常见，见到它就直接去核对三件事：
 *       ① XML 的 namespace 是不是正好等于本接口的全限定名
 *       ② XML 里的 id 是不是正好等于方法名
 *       ③ application.yml 里 mapper-locations 的路径能不能匹配到 XML 文件
 *
 * 【@Mapper 注解的作用】
 *   告诉 MyBatis："扫描到我的时候，给我生成代理对象，并注册到 Spring 容器里"。
 *   有了它，AccountService 里才能用 @Autowired 把它注入进来。
 *   （另一种做法是在启动类上加 @MapperScan("com.wuweiqiu.moneylog.mapper")，
 *     两者选一即可，我们这里用注解方式，更直观。）
 */
@Mapper
public interface AccountMapper {

    /**
     * 查询【当前登录用户的】所有账户。
     * 对应 AccountMapper.xml 里的 <select id="findAll">。
     *
     * ============================================================
     *  【Day11 关键改动】方法多了一个 userId 参数
     * ============================================================
     *  以前它是"查所有账户"，现在是"查**我的**所有账户"。
     *
     *  方法名没变，但语义变了 —— SQL 里多了 `WHERE user_id = #{userId}`。
     *  这就是"数据隔离"的全部秘密：
     *    **不是靠拦截器，而是靠每一条 SQL 都带上归属条件。**
     *
     *  拦截器只能证明"你是合法用户"，它不知道第 8 号账户是不是你的 ——
     *  因为它不懂业务。所以隔离必须落到 SQL 这一层。
     *
     *  【为什么 userId 必须是参数传进来，不能让前端自己传？】
     *  因为前端能传的话，它就能填别人的 id，隔离等于没做。
     *  所以 userId 的来源只有一个：
     *    **拦截器验签后放进 request 属性的那个值**（Controller 用
     *      @RequestAttribute 取出来，再一层层传到这里）。
     * ============================================================
     */
    List<Account> findAll(@Param("userId") Long userId);

    /**
     * 新增一个账户。
     * 对应 AccountMapper.xml 里的 <insert id="insert">。
     *
     * 【为什么返回 int？】
     *   表示"影响了几行"。插入成功就是 1，失败就是 0（或者直接抛异常）。
     *   这是所有写操作（INSERT / UPDATE / DELETE）的通用返回类型。
     *   它也是你的"第一道校验"：如果返回值不是 1，说明没插进去，要当心。
     *
     * 【为什么参数是整个 Account 对象，而不是 (String name, BigDecimal balance) 两个零散参数？】
     *   因为这样 XML 里就能直接写 #{name}、#{balance} 去取对象的属性值。
     *   参数一多，用对象比用一长串参数清爽得多，加字段也不用改方法签名。
     *
     * 【注意】这里还没有任何校验。如果传进来 name 是空的，它照样会插进去。
     *   为什么先不做校验？因为前端表单已经拦了一层（必填）。
     *   但"前端校验不可信"—— 等 Day4 我们会用工具绕过前端直接调接口，
     *   让你亲眼看到没校验的后端有多危险，那时再加后端校验才讲得透。
     */
    int insert(Account account);

    /**
     * 根据 id 查询单个账户。
     * 对应 AccountMapper.xml 里的 <select id="findById">。
     *
     * 【为什么需要这个方法？】
     *   做"修改"和"删除"之前，必须先确认这条记录**真的存在**。
     *   否则你会遇到这种尴尬情况：
     *     · 用户点删除 id=999 的账户，SQL 执行成功但删了 0 行
     *     · 接口却返回"删除成功" —— 用户以为删掉了，其实什么都没发生
     *   所以标准做法是：**先查在不在，再动手**。
     *
     * 【返回值为什么是 Account 而不是 List？】
     *   因为按主键查最多只有一条，不需要 List。
     *   如果查不到，MyBatis 会返回 null —— 这个 null 就是"不存在"的信号。
     */
    // 【Day11 改动】加了 userId：现在只能查到"自己的"账户。
    //   即使有人猜到了别人的账户 id（比如 1），SQL 里的
    //   `AND user_id = #{userId}` 也会让它查不到 → 返回 null
    //   → 后面"修改/删除"自然会失败。
    //   **一行 WHERE 条件，就挡住了水平越权。**
    Account findById(@Param("id") Long id, @Param("userId") Long userId);

    /**
     * 修改账户。
     * 对应 AccountMapper.xml 里的 <update id="update">。
     *
     * 返回 1 表示改成功了 1 行；返回 0 表示"没找到这条记录，一行都没改"。
     *
     * 【注意对象里必须有 id】
     *   因为 SQL 的 WHERE 条件靠 id 定位记录。id 是哪来的？
     *   是 Controller 从 URL 路径里取出来，塞进这个对象的。
     */
    int update(Account account);

    /**
     * 根据 id 删除账户。
     * 对应 AccountMapper.xml 里的 <delete id="deleteById">。
     *
     * 返回 1 表示删掉 1 行；返回 0 表示"没有这条记录，什么都没删"。
     *
     * 【为什么传 Long 而不是整个 Account 对象？】
     *   因为删除只需要一个 id，不需要其他字段。
     *   用参数的形式更明确地表达"删这条记录只需要一个编号"。
     *   （对比：修改需要 name、balance 这些新值，所以必须传整个对象。）
     *
     * 【一个小知识：单个参数时，XML 里的 #{...} 名字可以随便写】
     *   方法只有一个参数时，MyBatis 会把 #{任意名字} 都指向这唯一的参数。
     *   所以 XML 里写 #{id}、#{accountId} 甚至 #{abc} 都能取到值。
     *   但**强烈建议还是写 #{id}** —— 名字和参数对得上，代码才好读。
     *   等以后方法有多个参数时，就必须用 @Param("名字") 注解了，那时名字不能乱写。
     */
    // 【Day11 改动】加了 userId：只能删自己的。
    //   SQL 变成 `DELETE FROM account WHERE id = ? AND user_id = ?`，
    //   删别人的账户会"影响 0 行"，Service 里据此报错。
    int deleteById(@Param("id") Long id, @Param("userId") Long userId);

    /**
     * 【Day7 新增】在原有余额基础上"增加"一个数值（可正可负）。
     * 对应 AccountMapper.xml 里的 <update id="updateBalance">。
     *
     * ============================================================
     *  【关键设计：为什么是"增量"而不是"设置新值"？】
     * ============================================================
     *  假设微信钱包余额是 1000，你要支出 35 元。有两种改法：
     *
     *  改法A（设置新值）：先查出来算好，再写回去
     *      Account a = findById(1);          // 查到 balance = 1000
     *      a.setBalance(a.getBalance() - 35); // Java 里算 = 965
     *      update(a);                         // 把 965 写回去
     *
     *  改法B（增量更新）：直接让数据库自己算
     *      updateBalance(1, -35);             // SQL: SET balance = balance + (-35)
     *
     *  【为什么必须用改法B？—— 并发场景下改法A会算错】
     *  想象两个人几乎同时从微信钱包支出：
     *
     *      时刻   用户甲                      用户乙                  数据库余额
     *      ─────────────────────────────────────────────────────────────────
     *      T1    查到余额=1000                                        1000
     *      T2                                查到余额=1000            1000
     *      T3    算好 1000-35=965                                     1000
     *      T4                                算好 1000-20=980        1000
     *      T5    写入 965                                              965
     *      T6                                写入 980                 980
     *
     *      结果：总共支出了 55 元，但余额从 1000 只变成了 980（少扣了 35）。
     *      用户甲那笔支出**凭空消失了**。
     *
     *      这个经典问题叫"**丢失更新**"（Lost Update），是并发编程里的常客。
     *
     *  用改法B（增量更新）为什么就不会出问题？
     *    因为"计算"这一步交给了数据库，而数据库执行 UPDATE 时
     *    会对这一行**加锁**，第二个人的更新会等第一个人做完再执行：
     *
     *      T5  数据库执行 balance = balance + (-35)  → 965
     *      T6  数据库再执行 balance = balance + (-20) → 945  ← 正确！
     *
     *  【一句话记住】
     *    "算钱"这件事，能在数据库里做就别拿到 Java 里做。
     *    因为数据库有锁机制保证顺序，Java 代码之间是没有的。
     *    （这跟你以后可能听说的"悲观锁/乐观锁"是同一个话题的入口。）
     * ============================================================
     *
     * 【关于 @Param —— 多参数时必须用】
     *   这个方法有两个参数（id 和 delta）。
     *   MyBatis 面对多个参数时，无法自动判断 XML 里的 #{id} 该对应哪个参数
     *   （它能看到的只是没有名字的第1个、第2个参数），所以必须用 @Param 明确告诉它。
     *
     *   【对比】前面那几个方法都只有一个参数，所以 #{id} 怎么写都能取到值。
     *   但**一旦方法有 2 个以上参数，@Param 就是必须的**，
     *   不加会报 "Parameter 'id' not found. Available parameters are [arg0, arg1, param1, param2]"
     *   这类错误 —— 那个报错信息其实已经把答案告诉你了（它说只有 arg0/arg1 可用）。
     *
     *   顺便说明：即使某个项目配置了保留参数名（我们项目就是，
     *   Maven 编译时会带 -parameters 参数），@Param 依然是**推荐写法** ——
     *   它让代码不依赖编译配置，任何环境下都稳。
     *
     * @param id    账户 id
     * @param delta 要增加的金额（正数=增加，负数=减少）。注意这里传的是"变动量"，不是最终值。
     * @return 影响行数
     */
    // 【Day11 改动】加了 userId 条件（纵深防御）。
    //   按理说调用它之前，已经用 findById(id, userId) 确认过"这个账户是我的"了。
    //   但每条 SQL 都带上归属条件更稳 —— 万一以后有人加了个新的调用点、
    //   忘了先校验，数据库这一层还能兜住。
    //   —— 这叫**纵深防御（Defense in Depth）**：不依赖单点防护，每一层都自己守好。
    //   （javadoc 里的 @param 也需要补一个 userId，这里一并说明。）
    int updateBalance(@Param("id") Long id,
                      @Param("userId") Long userId,
                      @Param("delta") BigDecimal delta);
}
