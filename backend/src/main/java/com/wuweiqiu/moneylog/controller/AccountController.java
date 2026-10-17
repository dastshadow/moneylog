package com.wuweiqiu.moneylog.controller;

import com.wuweiqiu.moneylog.common.AuthInterceptor;
import com.wuweiqiu.moneylog.common.Result;
import com.wuweiqiu.moneylog.entity.Account;
import com.wuweiqiu.moneylog.service.AccountService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 账户接口层 —— 负责"接收 HTTP 请求、返回结果"，不写业务规则、不写 SQL。
 *
 * 完整调用链（这就是我们今天要精读的主链）：
 *
 *   浏览器/Postman
 *        ↓ HTTP 请求 GET /api/account/list
 *   AccountController   ← 接收请求，拆出参数，调用 Service
 *        ↓ Java 方法调用
 *   AccountService      ← 处理业务规则（今天只是转发）
 *        ↓ Java 方法调用
 *   AccountMapper       ← 接口，无实现类，由 MyBatis 动态代理实现
 *        ↓ 代理去 XML 里找 id="findAll"
 *   AccountMapper.xml   ← 真正的 SQL 写在这里
 *        ↓ JDBC
 *   MySQL               ← 真正执行 SQL，返回结果集
 *        ↓ 逐层原路返回，MyBatis 把每行封装成 Account 对象
 *   Result<List<Account>>  ← 统一包装成 {code, msg, data}
 *        ↓ Jackson 转成 JSON
 *   浏览器收到 JSON
 */
@RestController
@RequestMapping("/api/account")   // 类级前缀，本类所有接口都以它开头
public class AccountController {

    @Autowired
    private AccountService accountService;

    /**
     * 查询账户列表。
     * 访问方式：http://localhost:8080/api/account/list
     *
     * 返回值类型 Result<List<Account>> 拆开读：
     *   Result<...>     最外层统一响应体
     *   List<Account>   data 字段里装的是"账户对象的列表"（因为查出来有多条）
     */
    @GetMapping("/list")
    public Result<List<Account>> list(@RequestAttribute(AuthInterceptor.ATTR_USER_ID) Long userId) {
        // 【Day11】第一个参数就是"我是谁" ——
        //   它由拦截器验签后放进 request 属性，这里用 @RequestAttribute 取出来。
        //   （Day10 讲过它跟前面四个取参注解的**本质区别**：
        //     这个值来自**服务器自己**，所以可以信。）
        //
        //   Controller 的职责依然是那三行：取参数 → 调 Service → 包装返回。
        //   它不知道 SQL 里怎么过滤（那是 Mapper 的事），
        //   也不知道 userId 是怎么验出来的（那是拦截器的事）。
        List<Account> accounts = accountService.findAll(userId);
        return Result.ok(accounts);
    }

    /**
     * 新增账户。
     * 访问方式：POST http://localhost:8080/api/account/create
     *
     * ============================================================
     *  今天新出现的三个变化，逐个讲清
     * ============================================================
     *
     * 【变化1：@GetMapping 换成了 @PostMapping】
     *   为什么新增要用 POST，不能用 GET？这不是"规定"，是有原因的：
     *     · GET  语义是"读取"，不该改变服务端数据。而且它的参数会出现在 URL 上，
     *            会被浏览器历史、服务器日志、代理服务器记录下来 —— 密码、金额这类
     *            信息放 URL 上很不安全。
     *     · POST 语义是"提交/创建"，数据放在**请求体**里，不在 URL 上。
     *   一句话：**读取用 GET，会改变数据的用 POST**。这是 HTTP 的基本约定，
     *   用对了，别人看你的接口就能猜到它干什么。
     *
     * 【变化2：@RequestBody —— 把请求体里的 JSON 变成一个 Java 对象】
     *   前端发过来的是一个 JSON 字符串：
     *       {"name":"招商银行","balance":5000}
     *   而我们要的是一个 Account 对象。这中间的转换就是 @RequestBody 干的：
     *       JSON 文本 →（Jackson 自动反序列化）→ Account 对象
     *   它靠什么对应上？靠**字段名**：
     *       JSON 里的 "name"    → 去找 Account 的 setName("招商银行")
     *       JSON 里的 "balance" → 去找 Account 的 setBalance(5000)
     *   【这就是为什么实体类必须有 setter】—— 没有 setter，这里就收不到值。
     *   （Day1 讲过 setter 的两个用途：MyBatis 从数据库塞值、Jackson 转 JSON 取值。
     *     今天补上第三个用途：Jackson 把前端 JSON 塞进来也要用 setter。）
     *
     *   对比一下：查询接口没有 @RequestBody，因为它不需要参数。
     *   以后遇到带参数但不用 @RequestBody 的情况，那是从 URL 上传参（@RequestParam），
     *   两者的区别我们后面用到时再讲。
     *
     * 【变化3：返回值是 Result<Void>】
     *   新增操作不需要返回数据（数据在请求体里，前端本来就知道自己传了什么）。
     *   所以 data 是空的。用 Void 明确告诉大家"这个接口不返回业务数据"，
     *   比用一个模糊的类型更清楚。
     *   前端拿到 {"code":0,"msg":"ok","data":null} 就知道"成功了"。
     */
    @PostMapping("/create")
    public Result<Void> create(@RequestAttribute(AuthInterceptor.ATTR_USER_ID) Long userId,
                              @RequestBody Account account) {
        // ============================================================
        //  【Day11 最重要的一课：这两个参数的"可信度"完全不同】
        // ============================================================
        //     userId  ← @RequestAttribute：**服务器验签得到的身份**，可信 ✓
        //     account ← @RequestBody：    **客户端发来的数据**，不可信 ✗
        //
        //  特别注意：account 里面**也可能带一个 userId** ！
        //    前端只要在 JSON 里写 "userId":2，Jackson 就会把它绑进去
        //    （因为 Account 现在有 setUserId 了）。
        //
        //  那到底谁说了算？
        //    → 上面那个可信的 userId 说了算。
        //      Service 里会 `account.setUserId(userId)` 把客户端的值强行盖掉。
        //      见 AccountService.create() 里那一行。
        //
        //  记住这条通用原则：
        //    **客户端传来的值只能当"参考"，服务器该定的事自己定。**
        // ============================================================
        accountService.create(userId, account);
        return Result.ok();
    }

    /**
     * 修改账户。
     * 访问方式：PUT http://localhost:8080/api/account/6
     *          请求体：{"name":"建设银行","balance":8000}
     *
     * ============================================================
     *  今天的新东西：@PathVariable —— 从 URL 路径里挖参数
     * ============================================================
     *
     *  先看这个 URL：
     *      PUT /api/account/6
     *                     ↑
     *                  这一段就是 id
     *
     *  在注解里用花括号声明"这一段是个变量"，然后方法参数上用 @PathVariable 取值：
     *
     *      @PutMapping("/{id}")              ← {id} 声明"这个位置是参数"
     *      public ... update(@PathVariable Long id, ...)
     *                                   ↑ 声明"把上面那个 {id} 给我"
     *
     *  【名字必须对得上】@PutMapping("/{id}") 里的 id 和
     *    @PathVariable Long id 里的 id 要一致（不一致时得写成 @PathVariable("id") Long accountId）。
     *
     *  【它会自动转换成 Long】
     *    从 URL 里挖出来的原始值是字符串 "6"，但我们声明的是 Long，
     *    Spring 会自动帮我们转成数字 6。
     *   如果 URL 写成 /api/account/abc（不是数字），转换失败，
     *     Spring 会直接返回 400 错误 —— 请求根本进不到我们的方法里。
     *
     *  ============================================================
     *  @PathVariable 和 @RequestBody 放一起，各管一半
     *  ============================================================
     *      @PutMapping("/{id}")                              ← 从路径取 id
     *      public Result<Void> update(@PathVariable Long id,
     *                                 @RequestBody Account account)  ← 从请求体取数据
     *
     *  为什么要分开？因为它们是两种不同性质的信息：
     *    · id      = "改哪一条记录"（定位信息）→ 放路径里，一眼看得见
     *    · account = "改成什么样"（数据内容）  → 放请求体里，结构清晰
     *
     *  所以典型的一次修改请求长这样：
     *      PUT /api/account/6
     *      {"name":"建设银行","balance":8000}
     *      ↑ 改哪条          ↑ 改成什么
     *
     *  ============================================================
     *  【对比 GET / POST / PUT / DELETE 的语义】
     *  ============================================================
     *      GET    读取（不改数据）
     *      POST   创建新资源
     *      PUT    整体更新某个资源     ← 今天用的
     *      DELETE 删除某个资源         ← 下面那个
     *
     *  设计接口时按这个语义选方法，别人看你的接口就能猜到它干什么。
     *  （还有个 PATCH 表示"只改部分字段"，我们用得少，知道有就行。）
     */
    @PutMapping("/{id}")
    public Result<Void> update(@PathVariable Long id,
                              @RequestAttribute(AuthInterceptor.ATTR_USER_ID) Long userId,
                              @RequestBody Account account) {
        // 【Day11】三个参数恰好来自三个不同的地方，把"取参方式全家桶"用齐了：
        //    id      ← @PathVariable       URL 路径（改哪一条）
        //    userId  ← @RequestAttribute   服务器认证出的身份（这条是不是我的）
        //    account ← @RequestBody        请求体（改成什么样）
        //
        //  三个来源、三种可信度，一眼就能看出哪个能信、哪个不能。
        accountService.update(id, userId, account);
        return Result.ok();
    }

    /**
     * 删除账户。
     * 访问方式：DELETE http://localhost:8080/api/account/6
     *
     * 【注意：删除接口只有一个参数，没有请求体】
     *   因为"删哪条"只需要一个 id，不需要任何其他数据。
     *   对比一下四个接口的参数形式：
     *      查询列表  GET    /list             无参数
     *      新增     POST   /create           @RequestBody（要一整套新数据）
     *      修改     PUT    /{id}             @PathVariable + @RequestBody（定位 + 新数据）
     *      删除     DELETE /{id}             @PathVariable（只要定位）
     *
     *  参数形式跟着"需要多少信息"走，不是固定的套路。
     *
     * 【浏览器地址栏为什么删不了？】
     *   你可以试试在浏览器地址栏输 http://localhost:8080/api/account/6 ——
     *   那发的是 GET 请求，匹配到的是"查询"而不是"删除"，
     *   所以什么都不会被删掉。
     *   这是 HTTP 方法的保护作用：**URL 一样，方法不同，行为完全不同**。
     *   要真正发 DELETE 请求，得用前端代码或 Postman/curl 这类工具。
     */
    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id,
                              @RequestAttribute(AuthInterceptor.ATTR_USER_ID) Long userId) {
        accountService.delete(id, userId);
        return Result.ok();
    }
}
