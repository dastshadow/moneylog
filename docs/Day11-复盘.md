# Day11 复盘卡 —— 数据隔离：让每一行数据都有主人

> 日期：2026-10-16  
> 主题：张三只能看到张三的账，李四只能看到李四的账  
> 阶段：阶段二「鉴权与数据隔离」第 3 / 3 天 —— **阶段二完成**

---

## 一、今天做了什么（一句话）

**给 account 和 bill 两张表加上 `user_id`，然后让每一条 SQL 都带上"只属于我"这个条件。**

```
Day10 之前：没有"人"的概念
Day10     ：能认出"你是谁"（token + 拦截器）     ← 认证
Day11     ：能限制"你能动哪些数据"（user_id + WHERE）← 授权
```

**今天改动的量**：

| 类别  | 内容                                                                |
| --- | ----------------------------------------------------------------- |
| 数据库 | `account` / `bill` 加 `user_id` 列 + 索引 + 外键（含老数据迁移）                |
| 后端  | **12 个文件**：2 个实体、2 个 Mapper 接口、2 个 XML、3 个 Service、3 个 Controller |
| 前端  | **0 个文件** ← 这是今天最值得说的一件事，见第五节                                     |
| 测试  | 新增 `DataIsolationTest`（6 项断言）                                     |

---

## 二、数据库改造：一次真实的"数据迁移"

难点在于：**这不是新表，是一张已经装了数据的老表。** 怎么给老数据补上 `user_id`？

### 先看"直接干"会出什么事（我做了个实验，用临时表，不碰真数据）

```sql
-- 直接加一个 NOT NULL 的列
ALTER TABLE account_demo ADD COLUMN user_id BIGINT NOT NULL;
```

结果**三条**：

1. 命令**没报错**，看起来一切正常
2. 但 3 行老数据的 `user_id` 全被**静默填成了 `0`** —— 一个根本不存在的用户 id
3. 接着加外键时，数据库才报错：
   ```
   ERROR 1452 (23000): Cannot add or update a child row:
   a foreign key constraint fails
   ```

**⚠️ 最可怕的是：如果我不加外键，这堆 `user_id = 0` 的垃圾数据就会永远留在库里，而且没有任何报错。**

> **这是"数据迁移"最典型的陷阱：**  
> 数据库不会阻止你做错事，它只会安静地按"隐式默认值"填满。  
> 你必须在**迁移之后主动验证**，否则错误会在几个月后以"某个用户看到别人的数据"的形式爆发。

### 正确做法：三步走

```sql
-- ── 第 1 步：加列，先允许 NULL（因为老数据此刻还没有值）──
ALTER TABLE account ADD COLUMN user_id BIGINT NULL COMMENT '所属用户ID' AFTER id;
ALTER TABLE bill    ADD COLUMN user_id BIGINT NULL COMMENT '所属用户ID' AFTER id;

-- ── 第 2 步：数据迁移 —— 把老数据指定给某个用户 ──
--   WHERE user_id IS NULL 保证只碰"还没有主人"的行 → 这句可以重复执行，安全
UPDATE account SET user_id = 1 WHERE user_id IS NULL;
UPDATE bill    SET user_id = 1 WHERE user_id IS NULL;

-- ── 第 3 步：收紧约束（现在才敢"上锁"）──
ALTER TABLE account MODIFY COLUMN user_id BIGINT NOT NULL;      -- 3a 改成必填
ALTER TABLE bill    MODIFY COLUMN user_id BIGINT NOT NULL;
ALTER TABLE account ADD INDEX idx_user_id (user_id);            -- 3b 加索引
ALTER TABLE bill    ADD INDEX idx_user_id (user_id);
ALTER TABLE account ADD CONSTRAINT fk_account_user              -- 3c 加外键
    FOREIGN KEY (user_id) REFERENCES `user` (id);
ALTER TABLE bill    ADD CONSTRAINT fk_bill_user
    FOREIGN KEY (user_id) REFERENCES `user` (id);
```

### 为什么必须"先松后紧"，不能一步到位？

| 步骤               | 为什么不能省                                             |
| ---------------- | -------------------------------------------------- |
| 先允许 NULL         | 加列那一刻，老数据没有值。若直接 NOT NULL → 被静默填 0（上面看到的）          |
| 中间的 UPDATE       | **这一步才是"迁移"本身** —— 补上历史数据。也是唯一需要"人做决策"的一步（这些账算谁的？） |
| 最后 NOT NULL + 外键 | 有了正确数据，才敢上锁，防止以后写入脏数据（比如 user_id=0）                |

> **这套"加列 → 回填 → 收紧"是行业里改线上表的通用模式。**  
> 更严格的场景（表很大、不能停机）还会拆成更多步、配合灰度，  
> 但核心思想完全一样：**每个时刻都不能让表处于"不合法的状态"**。

### 顺手验证了一下外键真的生效

```sql
INSERT INTO account (user_id, name, balance) VALUES (999, '幽灵账户', 1);
→ ERROR 1452 ... foreign key constraint fails
```

**外键 = 数据库层面保证 `user_id` 一定指向一个真实存在的用户。** 跟 Day6 那条"不许给不存在的账户记账"是同一个道理，只是这次防的是"归属到一个不存在的人"。

---

## 三、代码改造：改了 12 个文件，但套路只有一个

不要被"12 个文件"吓到 —— 其实是**同一个动作重复了 12 遍**：

```
每一层都做同一件事：把 userId 往下传一格

拦截器验签        userId 从 token 里来（可信）
      ↓ request.setAttribute
Controller        @RequestAttribute 取出来 ── 往下传
      ↓
Service           ── 往下传（新增时还要"强制覆盖"）
      ↓
Mapper 接口       加 @Param("userId") 参数
      ↓
XML（SQL）        加 WHERE user_id = #{userId}   ← 隔离真正生效的地方
```

### 完整的 SQL 变化清单（这是核心，值得抄下来）

| 操作    | 改前                                    | 改后                                             |
| ----- | ------------------------------------- | ---------------------------------------------- |
| 查列表   | `FROM account`                        | `FROM account WHERE user_id = ?`               |
| 查单条   | `WHERE id = ?`                        | `WHERE id = ? AND user_id = ?`                 |
| 新增    | `INSERT INTO account (name, balance)` | `INSERT INTO account (user_id, name, balance)` |
| 修改    | `WHERE id = ?`                        | `WHERE id = ? AND user_id = ?`                 |
| 删除    | `WHERE id = ?`                        | `WHERE id = ? AND user_id = ?`                 |
| 连表查账单 | `FROM bill b LEFT JOIN account a ...` | 同上 + `WHERE b.user_id = ?`                     |
| 三个统计  | 有的**没有 WHERE**，有的只有条件                 | 全部加上 `WHERE user_id = ?`                       |

> **一句话总结**：数据隔离不是靠"多写几个 if 判断"，而是靠  
> **每一条 SQL 都带上归属条件**。  
> 因为 if 会漏写、会被绕过；而 SQL 里的 WHERE 是绕不过去的。

---

## 四、今天最重要的三个安全概念

### 概念 1：认证 ≠ 授权（Day10 埋的伏笔，今天兑现）

|                       | 回答什么问题       | 靠什么实现             | 状态      |
| --------------------- | ------------ | ----------------- | ------- |
| **认证** Authentication | **你是谁**？     | token + 拦截器       | ✅ Day10 |
| **授权** Authorization  | **你能动哪些数据**？ | user_id + `WHERE` | ✅ 今天    |

**为什么拦截器做不了授权？** 因为它不懂业务。  
它只知道"这张 token 是我签的、属于用户 1"，但它**不知道第 8 号账户是不是用户 1 的** ——  
这个信息在**数据库的行里**，不在 token 里。所以必须下沉到 SQL。

### 概念 2：水平越权 vs 垂直越权（安全术语，面试常问）

```
垂直越权（Vertical）：普通用户 → 干了管理员才能干的事
                      （比如普通用户删了别人的用户账号）

水平越权（Horizontal）：用户 A → 动了用户 B 的数据
                        （A 和 B 是同级别的普通用户）      ← 今天防的就是这个
```

**水平越权的可怕之处：它没有任何"异常"表现。**

- 用 A 登录、看自己的账户 → 一切正常，你看不出任何问题
- 只有"用 B 的身份去访问 A 的数据"才会暴露 —— 而**页面上点不出来**（页面压根不显示别人的账户）

> **所以越权漏洞只能靠测试发现，不能靠"点一下看看"。**  
> 见第七节的验证方法。

### 概念 3：Mass Assignment —— 客户端能塞 `userId`（今天真的会踩）

**这是今天最值得记牢的一个坑。**

加了 `userId` 字段之后，`Account` 类就有了 `setUserId()`。而 Jackson 是**按字段名自动绑定**的，所以任何人只要在请求体里写：

```json
POST /api/account/create
{"name":"伪装账户", "balance":1, "userId":2}
                                 ↑ 想写到别人名下
```

Jackson 就会老老实实调 `setUserId(2)` —— **这个攻击是真的能成功的**。

（Day9 讲这个概念时还是"理论上可能发生"，今天它变成了"真的会发生"。）

**解法只有一行：**

```java
public void create(Long userId, Account account) {
    validate(account);
    account.setUserId(userId);   // ← 不管前端传了什么都盖掉
    accountMapper.insert(account);
}
```

**注意参数里那个 `userId` 和 `account.getUserId()` 是两回事：**

|                       | 来源                           | 可信吗   |
| --------------------- | ---------------------------- | ----- |
| 参数 `userId`           | 服务器验签得到（`@RequestAttribute`） | ✅ 可信  |
| `account.getUserId()` | 客户端 JSON 里的字段                | ❌ 不可信 |

**一行 `setUserId` 就是在说："归属这件事，我说了算，你说了不算。"**

> **通用原则（请记牢）**：  
> 凡是由**服务器决定**的字段（归属 user_id、创建时间、状态、价格、权限等级……），  
> 在写库之前都必须**强制覆盖**。  
> 因为客户端传上来的任何东西都不可信 —— 这是 Day4 就定下的规矩，  
> 今天它第一次真的会伤人。

---

## 五、一个"意外之喜"：前端一行都不用改

隔离全部做在**后端的 SQL 层**之后，前端的 `App.vue`、`StatsPanel.vue` **一个字都没动**，  
但它现在显示的就自动是"我的数据"了。

**为什么？**

```
前端：GET /api/account/list        ← 请求里根本没有 userId
           ↓
后端：拦截器从 token 里认出"你是用户 1"
           ↓
      SQL: WHERE user_id = 1
           ↓
前端：拿到 2 条数据，它不知道后端做了什么过滤，也不需要知道
```

> **这是一个很重要的架构认知：**  
> **安全的判断应该放在服务器内部，而不是靠"前端不显示"。**
>
> 前端只显示自己的账户，那是"体验"；  
> 后端 SQL 过滤掉别人的数据，那才是"安全"。  
> 前者可以绕过（Day4 验证过），后者绕不过。
>
> 反过来说：**如果前端需要为了安全而改代码，那说明你的安全设计放在错的地方了。**



---

## 六、今天最容易漏掉的地方：统计

做数据隔离时，绝大多数人都会记得改"查列表"，但**统计报表极容易被漏掉**。

我今天的做法是：**全局搜表名，而不是凭记忆想。**

```bash
grep -rn "FROM bill" backend/src/main/resources/mapper/
```

搜出来 5 处，逐个改。这样比"回忆哪些地方用了这张表"可靠得多。

**漏掉统计会有两个后果，都很严重：**

| 后果        | 表现                                   |
| --------- | ------------------------------------ |
| **数字是错的** | 图表把**所有人**的账加在一起显示给你，你看到一个自己根本没花过的金额 |
| **泄露隐私**  | 别人的消费情况、收入水平，全糊在你眼前的饼图里              |

而且注意：`sumByMonth`（月度趋势）以前**根本没有 WHERE 子句** ——  
它就是"统计所有人的所有月份"。这种"看起来只是个汇总、跟归属无关"的 SQL，  
正是最需要警惕的。

> **做隔离后的自查清单（建议每次都用）**
>
> 1. `grep` 表名，列出**所有**读这张表的地方
> 2. 逐个问："这里有没有限定 user_id？"
> 3. 特别注意**聚合查询**（SUM/COUNT/GROUP BY）和**连表查询**

---

## 七、今天的验证结果

### ✅ 自动化测试：6 项断言全过

新增 `backend/src/test/java/com/wuweiqiu/moneylog/DataIsolationTest.java`。  
**为什么这件事必须靠测试、不能手动点？** 因为越权漏洞在正常操作下**完全看不出来**。  
真正的验证必须是"**用 B 的身份去访问 A 的数据**"，而这在页面上点不出来（页面压根不显示别人的账户）。

| # | 验证项             | 做法                     | 结果                      |
| - | --------------- | ---------------------- | ----------------------- |
| ① | **查询隔离**        | B 拉账户列表                | 列表里**没有** A 的账户 ✓       |
| ② | **更新隔离**        | B 拿 A 的账户 id 去改        | 报"账户不存在"；DB 里名字**没变** ✓ |
| ③ | **删除隔离**        | B 拿 A 的账户 id 去删        | 报错；DB 里记录**还在** ✓       |
| ④ | **跨账户越权**       | B 把账记到 A 的账户上          | 拦下："所选账户不存在" ✓          |
| ⑤ | **伪造 userId** ★ | B 建账户时在 JSON 里写 A 的 id | 实际归属仍是 **B** ✓          |
| ⑥ | **统计隔离**        | A 记 500 支出，看两人的汇总      | A: 500.00；B: **0.00** ✓ |

**⑤ 是最关键的一条**，它同时验证了"漏洞真的存在"和"防护真的生效"：

```
[测试] ✓ ⑤ 伪造 userId 无效：请求体里写 userId=10，实际归属仍然是 B(id=11)
```

### ✅ 真实 SQL 日志（最有说服力的证据）

从 MyBatis 的 debug 日志里抓到实际执行的 SQL：

```sql
SELECT id, name, balance, created_at FROM account WHERE user_id = ? ORDER BY id
SELECT id, name, balance, created_at FROM account WHERE id = ? AND user_id = ?
UPDATE account SET balance = balance + ? WHERE id = ? AND user_id = ?
SELECT b.id, b.user_id, b.account_id, ... FROM bill b LEFT JOIN account a
   ON b.account_id = a.id WHERE b.user_id = ? ORDER BY b.bill_date DESC, b.id DESC
SELECT IFNULL(SUM(...),0) AS totalExpense, ... FROM bill WHERE user_id = ?
INSERT INTO account (user_id, name, balance) VALUES (?, ?, ?)
INSERT INTO bill (user_id, account_id, type, amount, ...) VALUES (?, ?, ...)
```

**每一条都带上了归属条件 —— 这就是隔离落地的铁证。**

### ✅ 三个测试类全部通过

```
Tests run: 1, Failures: 0, Errors: 0 -- AuthInterceptorTest
Tests run: 1, Failures: 0, Errors: 0 -- DataIsolationTest
Tests run: 1, Failures: 0, Errors: 0 -- UserServiceTest
```

### ✅ 老数据没丢

3 个账户 + 7 条账单全部迁移到了 `user_id = 1`（wuweiqiu），用原来的账号登录还能看到。

---

## 八、常见错误对照表

| 现象                                               | 原因                            | 怎么查                                 |
| ------------------------------------------------ | ----------------------------- | ----------------------------------- |
| 新用户登录后看到**别人的**数据                                | 某条 SQL 漏了 `WHERE user_id`     | `grep` 表名，逐个核对                      |
| 新用户登录后**什么都看不到**（连自己的也没有）                        | 插入时 `user_id` 没填对，或填成了 null   | 直接查库：`SELECT user_id FROM account`  |
| 报 `foreign key constraint fails`                 | 传了个不存在的 user_id（比如 0）         | 检查写入前有没有强制覆盖 userId                 |
| 报 `Field 'user_id' doesn't have a default value` | 表已改成 NOT NULL，但代码没给值          | 检查 insert 语句和 Service 有没有 setUserId |
| 加 `ALTER TABLE ... NOT NULL` 没报错，但数据全变 0         | 这就是"静默填充"陷阱                   | **迁移后必须 SELECT 检查一遍**               |
| 改了自己的数据却报"不存在"                                   | `findById` 传的 userId 和记录里的不一致 | 打印出来对比（常见于传错参数顺序）                   |
| 统计数字比账户流水大很多                                     | 统计 SQL 漏了 `user_id` 条件        | 检查聚合查询的 WHERE                       |
| 别人能改我的数据，但我看不出异常                                 | 水平越权，正常操作不可见                  | **必须写测试**（用另一个用户的 token 去打接口）       |

---

## 九、排错套路（今天新增一条）

**越权类问题的排查方法：换一个身份去试。**

前面几天我们用的套路是"curl 逐层测"（测后端、测代理）。  
今天针对**权限**问题有新的一招：

> **准备两个账号，一个当"受害者"、一个当"攻击者"，  
> 然后用攻击者的 token 去访问受害者的数据 id。**

具体做法（就是今天那个测试干的事）：

```
① 用 A 登录，创建一条数据，记下它的 id（比如 账户 id = 22）
② 换成 B 的 token，拿这个 id 去调所有接口：
     GET    /api/account/list       → 列表里不该有它
     PUT    /api/account/22         → 该失败
     DELETE /api/account/22         → 该失败
     POST   /api/bill/create {accountId:22} → 该失败
③ 每一步都**回数据库核对**，不能只看接口返回
```

**为什么要回数据库核对？** 因为接口可能"返回失败但实际改了"，  
也可能"返回成功但什么都没改"（Day5 见过这种）。  
**接口的响应和数据库的真实状态，要分别验证。**

另外一条今天的经验：

> **做隔离时不要"凭记忆想哪里要改"，要 `grep` 表名。**  
> 记忆会漏（尤其统计），grep 不会。

---

## 十、诚实的提醒：还缺什么

**至此，阶段二（鉴权与数据隔离）全部完成。** 现在的系统：

```
✅ 能注册、能登录（密码加密存储）
✅ 没登录不能访问任何业务接口（401）
✅ 登录后只能看到 / 修改 / 删除自己的数据
✅ 统计只统计自己的
✅ 伪造归属无效
```

**但离"能上线"还有一段距离，诚实列一下：**

| 缺口                            | 说明                             | 严重度            |
| ----------------------------- | ------------------------------ | -------------- |
| **已有账户仍无主**：注册新用户后，新用户一个账户都没有 | 需要"注册时自动初始化一个默认账户"             | 中（体验问题）        |
| **没有改密码 / 退出所有设备**            | token 签发后无法撤销（Day9 讲过的 JWT 缺点） | 中              |
| **账户之间不能转账**                  | 那才需要真正用上事务的复杂场景                | 低              |
| **没有分页**：账单多了会一次全查出来          | 数据量上来会变慢                       | 中              |
| **账单不能改和删**                   | CRUD 还差 U 和 D                  | 低              |
| **仍是 HTTP，不是 HTTPS**          | token 在网络上是明文传输                | **高（上线前必须解决）** |
| **密钥写死在 yml 里**               | 应该走环境变量                        | **高**          |
| **前端登录页上写了测试账号**              | 我在注释里标了"上线前必须删"                | 中              |

**下一步（阶段三）的候选方向：**

- **A. 完善记账本身**：账单的改/删、分页、筛选、账户间转账（用上事务）
- **B. 部署上线**：打包、HTTPS、环境变量、让别人能访问
- **C. 打磨体验**：注册时初始化默认账户、改密码、弱密码校验

---

## 十一、我自己的笔记

> （这一块留给我自己写。写什么都行，用我自己的话。  
> 上面这些是老师整理的知识点，这一段是"我真正消化了什么"。）
