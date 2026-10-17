# Day3 复盘卡 · 第一个写操作（新增账户）

> 这是你的学习笔记，可以随意加自己的话。我不会删改你写的任何内容。

---

## 一、今天做成的事

**从"只会读"跨到"会写"** —— 在页面上填个表单，数据真的进了数据库。

```
弹窗表单填「中国银行 / 3000」
     ↓  POST /api/account/create  （经 Vite 代理）
Controller  @PostMapping + @RequestBody 把 JSON 变成 Account 对象
     ↓
Service     业务规则：余额没填就补 0
     ↓
Mapper      insert(account)
     ↓
XML         INSERT INTO account (name, balance) VALUES (?, ?)
     ↓
MySQL       真的多了一行：id=6  中国银行  3000.00
```

**页面上多一行 + 数据库里多一行 = 写操作闭环成立。**

---

## 二、只改了 4 个文件，每个只加一个方法（这就是分层的价值）

| 文件 | 加的东西 | 它的职责 |
|---|---|---|
| `AccountMapper.java` | `int insert(Account account);` | 声明"我要写数据" |
| `AccountMapper.xml` | `<insert id="insert">` 一段 SQL | 放真正的 SQL |
| `AccountService.java` | `create()` 方法 | 放业务规则 |
| `AccountController.java` | `@PostMapping("/create")` | 接 HTTP 请求 |

**关键感受**：想加一个功能，**四层各加一处，不用满项目翻**。Day1 学的分层，今天兑现了。

> 回想 Day1 你的疑问："Service 只是转发，多这一层干嘛？"
> **今天答案来了**：Service 里加了"余额没填算 0"这条规则。
> 这类规则既不属于 HTTP（Controller），也不属于 SQL（Mapper），它属于**业务**。

---

## 三、三个新知识点

### 1. 为什么新增用 POST，不用 GET

这不是死规定，是有原因的：

| | GET | POST |
|---|---|---|
| 语义 | **读取**，不该改变数据 | **提交 / 创建** |
| 参数位置 | **URL 上** | **请求体**里 |
| 风险 | URL 会被浏览器历史、服务器日志、代理服务器记录 → 密码、金额放上去不安全 | 数据在请求体里，不暴露在地址栏 |

**一句话记住：读取用 GET，会改数据的用 POST。**

（小技巧：以后看到接口用错方法，比如 `GET /delete?id=1`，就知道这人设计得不规范。）

### 2. `@RequestBody` —— JSON 文本怎么变成 Java 对象

前端发过来的是一串**文本**：
```json
{"name":"中国银行","balance":3000}
```

后端需要的是一个 `Account` **对象**。中间转换靠 `@RequestBody`：

```
JSON 文本  →（Jackson 自动反序列化）→  Account 对象
                  靠什么对应上？靠字段名：
                  "name"    → setName("中国银行")
                  "balance" → setBalance(3000)
```

**⚠️ 这里补全了 Day1 的一个伏笔**——为什么实体类必须写 setter：

| setter 的用途 | 谁要用它 |
|---|---|
| ① 从数据库把值塞进对象 | MyBatis 查数据时 |
| ② 把对象转成 JSON 返回 | Jackson 返回响应时 |
| ③ **把前端 JSON 塞进对象** | **Jackson 接收请求时（今天新增的）** |

**三个用途都靠它。少写一个 setter，哪条链路断哪条，而且报错很不明显。**

### 3. `useGeneratedKeys` —— 自增 ID 怎么"回填"

我们 INSERT 时**没传 id**，是数据库自己发号的。程序怎么知道新记录 id 是多少？

```xml
<insert id="insert" useGeneratedKeys="true" keyProperty="id">
```

这两个属性让 MyBatis 做一件事：**INSERT 完成后，把数据库生成的自增 id 回填到你传进去的那个对象上。**

```
调用 insert 之前：account.getId()  →  null
调用 insert 之后：account.getId()  →  6      ← 自动填上了
```

**什么时候需要它？**
- 插入账单后要跳转到它的详情页 → 需要 id
- 插入主表后要接着插子表（子表要存主表 id）→ 需要 id

---

## 四、写操作 vs 读操作：怎么在日志里区分

**这是今天最实用的一招。** 看日志的最后一个词就知道是读还是写：

| | 读操作（SELECT） | 写操作（INSERT / UPDATE / DELETE） |
|---|---|---|
| 日志末尾 | `<==      Total: 3` | **`<==    Updates: 1`** |
| 含义 | 查到了几行 | **影响了几行** |
| 参数日志 | 通常为空（没条件） | 会打印实际值 |

今天真实的日志长这样：
```
==>  Preparing: INSERT INTO account (name, balance) VALUES (?, ?)
==> Parameters: 中国银行(String), 3000(BigDecimal)
<==    Updates: 1
```

**三个细节值得注意**：
- SQL 里是 `(?, ?)` 而不是直接写值 → 这叫**预编译占位符**，能防 SQL 注入
- `Parameters:` 那行告诉你**每个 ? 实际被填了什么**，值和类型都标出来了
- `<== Updates: 1` → 成功写入 1 行。**如果不是 1，就要警惕**

---

## 五、自增 ID 的两个真相（今天踩到的）

### 真相 1：**自增 ID 只增不减，永不回收**

删除记录**不会**把号码退还，别人也不能复用它。

今天数据库里的实况：
```
id 1  微信钱包
id 2  支付宝
id 3  ← 空号
id 4  ← 空号
id 5  ← 空号
id 6  中国银行   ← 你今天新增的
```
**号码有空洞是完全正常的，不是数据坏了。** 下次新增会是 **7**。

**为什么这么设计？** 为了保证 ID 唯一且不重复利用。如果删除后号码被复用，
可能出现"你以为在看订单 5，其实订单 5 已经换成别人的订单了"这种灾难。

### 真相 2：**`SHOW TABLE STATUS` 的"下一次自增值"可能是过期值**

排查今天那个"为什么是 6 不是 5"时发现的：

```sql
-- 这两条读的是缓存统计，可能过期：
SHOW TABLE STATUS LIKE 'account';                        -- 曾显示 5（过期）
SELECT AUTO_INCREMENT FROM information_schema.TABLES ...  -- 也曾显示 5（过期）

-- 刷新统计信息后才准确：
ANALYZE TABLE account;
SHOW TABLE STATUS LIKE 'account';                        -- → 7 正确

-- 或者用最直接的方式（推荐）：
SELECT MAX(id), COUNT(*) FROM account;                   -- → 6, 3  可信
```

**教训：一个数字，最好用两种独立方式互相印证，别只看一个输出就当真。**

---

## 六、今天的一手排错案例（很真实，值得记住）

**事件**：我告诉用户"新增后 ID 会是 5"，结果用户新增后拿到的是 **6**。

**还原真相**：

| 时间 | 谁 | 干了什么 | 自增值变化 |
|---|---|---|---|
| 16:13 | 我 | 测试插入 id=3、4（验功能） | → 5 |
| 16:13 | 我 | 删掉 3、4 清理数据 | 仍是 5 |
| 16:13 | 我 | 查到 5，**告诉用户"下一个是 5"** | — |
| 16:14 | 我 | **又做了一次代理测试，插入 id=5** | **→ 6** |
| 16:14 | 我 | 删掉 id=5 | 仍是 6 |
| 16:19 | 用户 | 新增"中国银行" | **拿到 6** ✅ |

**根因**：我给出"5"之后，**自己又消耗掉了 5，却没重新核对**，把过期结论留给了用户。

**从这里能提炼出两条通用经验**：

1. **给任何具体数字之前，重新查一次实况。** 不要凭记忆，尤其是那些会变的值。
2. **自己做完测试性写操作（插入/删除）之后，必须重新核对状态**，因为你的测试本身改变了现场。

**用户做得对的地方**：他记住了我给的"5"，**主动核对并追问为什么是 6**。
这就是工程里最宝贵的习惯——**不盲信任何人的断言，看到不符就追问。**

---

## 七、关键词

| 分类 | 词 |
|---|---|
| HTTP | `POST`、请求体（body）、`Content-Type: application/json` |
| 注解 | `@PostMapping`、`@RequestBody` |
| MyBatis | `<insert>`、`useGeneratedKeys`、`keyProperty`、`#{}` 占位符、`Updates` |
| 数据库 | `AUTO_INCREMENT`、自增不回退、`information_schema`、`ANALYZE TABLE`、`SHOW TABLE STATUS` |
| 前端 | `el-dialog`、`el-form`、`el-form-item`、`prop`、`rules`、`el-input-number`、`ElMessage`、表单引用 `formRef` |
| 概念 | 前端校验 vs 后端校验、业务规则、数据驱动视图 |

---

## 八、错误对照表（今天新增）

| 现象 | 原因 | 怎么办 |
|---|---|---|
| 提交后**页面看不到新记录** | 忘了重新拉列表 | 提交成功后要 `await loadAccounts()` |
| 提交后**数据库没有新行** | 请求没到后端 / SQL 出错 | 看日志有没有 `Preparing: INSERT` |
| `<== Updates: 0` | 影响 0 行，没写进去 | 检查 SQL 条件和参数 |
| 新增时**字段全是 null** | 缺 setter，或 JSON 字段名不匹配 | 检查实体类 setter + 前端字段名 |
| 删除数据后**号码不连续** | 自增 ID 不回收 | **正常现象，不用处理** |
| 点了提交没反应 | 前端校验没过（名字为空） | 看表单里的红色提示文字 |
| 手快连点两次提交 | 没做防重复提交 | 用 `:loading="submitting"` 禁用按钮 |

---

## 九、我自己的笔记（这里归你，随便写）

（写下你自己的话 —— 哪个地方卡住了、哪句话你用自己的理解记住了、还想问什么）

-


---

## 十、诚实的提醒：现在还缺什么

**今天这个"新增"是有隐患的——它没有后端校验。**

前端表单拦住了"名字为空"，但那只是**用户体验优化**，不是安全防线。
任何人用 curl / Postman 都能绕过页面，直接给接口发一个空名字，**照样能写进数据库**。

我在代码注释里写了这句话，今天没动手修：

> 前端校验不可信 —— 任何人用 curl/Postman 都能绕过页面直接调接口。

**Day4 我们就亲手绕过它一次**，亲眼看到脏数据进库，然后补上：
- 后端校验（Service 里校验 name 不能为空、balance 不能为负）
- 全局异常处理（`@RestControllerAdvice`），把异常统一转成 `{code:1, msg:"..."}`
- **顺便闭环 Day2 前端里那句 `if (resp.data.code !== 0)`** —— 写了这么久，终于要派上用场了
