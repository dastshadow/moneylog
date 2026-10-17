# Day5 复盘卡 · 补齐 CRUD（修改与删除）

> 这是你的学习笔记，可以随意加自己的话。我不会删改你写的任何内容。

---

## 一、今天做的事：CRUD 四件套齐了

```
C  Create  新增    POST   /api/account/create       @RequestBody
R  Read    查询    GET    /api/account/list         无参数
U  Update  修改    PUT    /api/account/{id}         @PathVariable + @RequestBody
D  Delete  删除    DELETE /api/account/{id}         @PathVariable
```

**"CRUD" 是全世界的软件开发通用词**（Create / Read / Update / Delete），
任何一套管理系统，核心都是对数据做这四件事。

### 四个接口的参数形式（注意它跟"需要多少信息"走，不是固定套路）

| 接口 | 路径 | 参数 | 为什么这样 |
|---|---|---|---|
| 查询列表 | `GET /list` | 无 | 查全部，不需要指定什么 |
| 新增 | `POST /create` | `@RequestBody` | 要给一整套新数据 |
| 修改 | `PUT /{id}` | `@PathVariable` + `@RequestBody` | 既要**定位**（改哪条）又要**数据**（改成什么） |
| 删除 | `DELETE /{id}` | `@PathVariable` | 只要**定位**（删哪条） |

---

## 二、今天最重要的新知识点：`@PathVariable`

### 三种传参方式，怎么选？

| 参数放在哪 | 长什么样 | 用什么注解 | 什么时候用 |
|---|---|---|---|
| **URL 问号后面** | `/list?type=支出&page=1` | `@RequestParam` | 筛选、分页条件（还没用到） |
| **请求体里** | `{"name":"中国银行"}` | `@RequestBody` | 要提交的数据 |
| **URL 路径里** | `/api/account/6` | **`@PathVariable`** | **指向某一条记录** |

**判断方法**：问自己"这个参数是**要传的数据**，还是**指向某条记录**？"
后者就用路径参数 —— 因为它最符合直觉："我要操作 6 号"。

### 代码怎么写

```java
@PutMapping("/{id}")                                        // ← {id} 声明"这一格是变量"
public Result<Void> update(@PathVariable Long id,           // ← 把 {id} 取出来
                           @RequestBody Account account) {  // ← 从请求体取数据
    accountService.update(id, account);
    return Result.ok();
}
```

**三个细节**：
1. **名字要对上**：`/{id}` 和 `@PathVariable Long id` 的 `id` 要一致
2. **自动转类型**：URL 里挖出来的原始值是字符串 `"6"`，声明成 `Long` 后 Spring 自动转成数字
3. **转不了会怎样**：`/api/account/abc` 这种，Spring 在转换时就失败了，**请求根本进不到你的方法里**

### 一次修改请求的完整长相

```
PUT /api/account/6
      ↑ 改哪条（路径参数）        Content-Type: application/json
                                 {"name":"建设银行","balance":8000}
                                  ↑ 改成什么（请求体）
```

**"定位在路径，数据在体内"** —— 记住这句话，RESTful 接口设计基本就通了。

### 顺便一个安全相关的小知识

浏览器地址栏输入 `http://localhost:8080/api/account/6` 发的是 **GET** 请求，
匹配到的是"查询"而不是"删除" —— **什么都不会被删掉。**

**URL 一样、方法不同、行为完全不同**，这是 HTTP 方法存在的重要意义之一：
防止你手滑在地址栏回车一下就删了数据。

---

## 三、写 UPDATE / DELETE 的铁律：WHERE 是命根子

```sql
UPDATE account SET name = ?, balance = ?  WHERE id = ?     ← 只改 1 条 ✓
UPDATE account SET name = ?, balance = ?                   ← 改了整张表 💀
```

**忘写 WHERE，就是"把全表每一行都改掉"或"清空整张表"。**

这在真实生产环境里是最著名的"事故级"错误之一。所以行业里有条硬规矩：

> **写 UPDATE / DELETE 之前，先用 SELECT 加同样的 WHERE 条件试一次**，
> 看看要影响的是不是你以为的那几行。确认无误，再把 SELECT 改成 UPDATE/DELETE。

我们今天写的 SQL 都带了 WHERE，但这条规矩你要刻在脑子里。

---

## 四、今天发现并修复的两个真实问题

### 问题 1：路径参数传字母，提示却是"系统繁忙"

```
DELETE /api/account/abc
→ 修前：{"code":1,"msg":"系统繁忙，请稍后再试"}   日志记 ERROR
→ 修后：{"code":1,"msg":"参数 id 格式不正确"}     日志记 WARN
```

**三个错误，跟 Day4 讲的完全对应**：

| 错在哪 | 说明 |
|---|---|
| ① 提示错得离谱 | 这是"你地址栏写错了"，不是"服务器坏了"。用户看到"系统繁忙"会以为我们出故障 |
| ② **日志级别用错** | Day4 讲过：用户输入错误用 `WARN`，系统故障才用 `ERROR`。用 ERROR 记这种错会让日志堆满噪音 |
| ③ 职责没划清 | 这个异常是 Spring 在"把字符串转成数字"时抛的，**根本还没进到我们的代码里** |

**修复方式**：给它单独配一个处理方法（`MethodArgumentTypeMismatchException`），
给出精准提示、记 WARN —— 这就用上了 Day4 学的"就近匹配原则"。

### 问题 2：404 被我们改成了 200（这个更危险）

```
DELETE /api/nothing/1        ← 访问一个压根不存在的接口
→ 修前：HTTP 200 + {"code":1,"msg":"系统繁忙，请稍后再试"}
→ 修后：HTTP 404 + {"code":1,"msg":"请求的地址不存在"}
```

**为什么"200"是个大问题？**
因为浏览器、网关、监控系统、日志分析工具**全靠 HTTP 状态码判断请求是否正常**。

所有错误都被伪装成 200，会导致：
- 你以后接监控报警，配置"统计 4xx/5xx 数量"时**全是 0**
- 出了问题**根本察觉不到**
- **这比报错更危险** —— 报错至少提醒了你，掩盖问题让你一无所知

### 那 Day4 不是说"我们选 HTTP 200 + 业务 code"吗？矛盾吗？

**不矛盾。** 关键在于区分两类不同的失败：

| 类型 | 例子 | 该用什么 |
|---|---|---|
| **① HTTP 层面的失败**<br/>（请求压根没找到能处理它的接口） | 地址写错（404）<br/>方法用错，如对只收 GET 的地址发 POST（405） | **真实的 HTTP 状态码**<br/>因为这是"协议层面"的事，跟业务无关 |
| **② 业务层面的失败**<br/>（请求找到了接口，但业务规则不允许） | 账户名称为空<br/>账户不存在 | **HTTP 200 + code≠0**<br/>因为"HTTP 这次交互是成功的，只是业务没通过" |

> **一句话记住：**
> **"找不到门"要用 404，"进门后被拒绝"用 200 + code。**

---

## 五、两个"把之前埋的伏笔收回来"的地方

### 1. 返回值终于用上了（Day3 的伏笔）

Day3 我说过："`insert` 返回的'影响行数'以后会用到"。今天用上了：

```java
int rows = accountMapper.update(account);
if (rows != 1) {
    throw new IllegalArgumentException("修改失败，请刷新后重试");
}
```

**为什么现在才需要？** 因为改/删的时候，"影响 0 行"是有真实含义的
—— 说明"没找到这条记录"。而新增时"影响 0 行"通常意味着 SQL 已经出异常了。

### 2. 校验抽出来了（避免复制粘贴）

新增要校验，修改也要校验，规则**完全一样**。所以抽成了一个 `private` 方法：

```java
public void create(Account account) {
    validate(account);     // ← 同一套规则
    ...
}

public void update(Long id, Account account) {
    validate(account);     // ← 同一套规则，不用复制
    ...
}

private void validate(Account account) {
    // 三条校验规则只有这一份
}
```

**为什么必须抽出来？** 想象一下复制粘贴两份的后果：

> 以后规则变了（比如名称上限从 50 改成 30），
> 你改了新增那份、**忘了改修改那份** →
> 有人就能"先建个合法的，再改成 31 个字"**绕过限制**。

**通用原则：重复的代码是 bug 的温床 —— 同一个规则只能有一处实现。**

---

## 六、亲眼验证：自增 ID 不会回退

Day3 讲过的理论，今天动手看了一次：

```
第1步  新增「临时账户A」  → 拿到 id = 18
第2步  把 A 删掉
第3步  新增「临时账户B」  → 拿到 id = 19   ← 不是 18！
```

**删除后号码不会退还，别人也不能复用这个号。**

所以数据库里现在有一路空号：`3、4、5、7~18、20`
（这些都是 Day3~Day5 测试时消耗掉的，**完全正常**，下次新增会是 21）

**为什么要这样设计？** 如果删除后号码被复用，就会出现
"你以为在看订单 5，其实订单 5 已经换成别人的订单了"这种灾难。

---

## 七、前端今天学到的东西

### 1. 一个弹窗，两种模式（`editingId` 的妙用）

新增和编辑长得几乎一样，所以**共用一个弹窗**。靠一个变量区分：

```js
const editingId = ref(null)

// null     → 新增模式（表单空着，提交时发 POST）
// 有数字    → 编辑模式（表单预填，提交时发 PUT，数字就是要改的 id）
```

**为什么可以用"有没有 id"来区分？** 因为**新增的记录还没进数据库，根本不可能有 id**。
这种"用一个字段的有无来表达状态"的做法，在实际开发里非常常见。

```js
function submitForm() {
  if (editingId.value === null) {
    resp = await axios.post('/api/account/create', form.value)      // 新增
  } else {
    resp = await axios.put('/api/account/' + editingId.value, form.value)  // 编辑
  }
}
```

**注意这个对应关系**：前端 `axios.put(path, body)` 的两个参数，
正好对应后端 `@PathVariable Long id, @RequestBody Account account` 的两个参数。
**路径传 id，body 传数据。**

### 2. 一个必须注意的坑：编辑时要"复制"数据

```js
// ❌ 错误写法
form.value = row                       // 表格里的数据会被弹窗实时改掉！

// ✅ 正确写法
form.value = { name: row.name, balance: Number(row.balance) }
```

**如果直接赋值 `form.value = row`**，那么你在弹窗里输入时，
**表格中那一行的显示会跟着一起变** —— 你还没点确定，表格就已经变了。
因为 `row` 和 `form.value` 指向的是**同一个对象**。

（`{...row}` 这种"展开语法"也能复制，效果一样。）

### 3. 删除必须二次确认

```js
await ElMessageBox.confirm(
  `确定要删除账户「${row.name}」吗？删除后无法恢复。`,
  '删除确认',
  { confirmButtonText: '确定删除', cancelButtonText: '取消', type: 'warning' }
)
```

`ElMessageBox.confirm` 返回 Promise：
- 用户点确定 → 正常往下走
- 用户点取消 → **抛异常**，被 `catch` 接住，什么也不做

**为什么删除一定要确认？** 因为它是**不可逆**的。
修改错了还能再改回来，删了就真没了。**一般规则：不可逆操作都要二次确认。**

### 4. axios 的一个细节：HTTP 非 200 时怎么取后端的错误信息

```js
try {
  resp = await axios.delete(...)
} catch (e) {
  const serverMsg = e.response?.data?.msg    // ← 从这里取
  ElMessage.error(serverMsg || '删除失败')
}
```

**为什么不能直接用 `resp.data.msg`？**
因为 axios 看到 HTTP 状态码不是 2xx（比如 404），
会认为"请求失败"，**直接跳到 catch**，你就拿不到 `resp` 了。

但错误对象 `e` 里带着响应内容 —— 通过 `e.response.data` 能取到。
（`?.` 是"可选链"，意思是"如果前面是 null 就不继续取，直接返回 undefined"，
防止 `e.response` 不存在时又报一个新错。）

**所以两种情况的错误处理都要写**：
- 业务失败（HTTP 200 + code≠0）→ 从 `resp.data.msg` 取
- 请求失败（HTTP 404/500 等）→ 从 `e.response.data.msg` 取

---

## 八、关键词

| 分类 | 词 |
|---|---|
| HTTP 方法 | `GET` / `POST` / `PUT` / `DELETE` / `PATCH`（了解即可） |
| 注解 | `@PutMapping`、`@DeleteMapping`、`@PathVariable`、`@ResponseStatus` |
| 异常 | `MethodArgumentTypeMismatchException`、`NoResourceFoundException`、异常分级 |
| SQL | `UPDATE ... SET ... WHERE`、`DELETE ... WHERE`、WHERE 是命根子 |
| 前端 | `ElMessageBox.confirm`、二次确认、弹窗复用、`{...row}` 复制、`e.response?.data`、可选链 `?.` |
| 概念 | CRUD、RESTful 风格、防御性编程、代码复用、状态码语义 |

---

## 九、错误对照表（Day5 新增）

| 现象 | 原因 | 怎么办 |
|---|---|---|
| 修改"成功"但数据没变 | 忘了 `account.setId(id)`，SQL 变成 `WHERE id = null` | 检查 Service 里有没有把 URL 的 id 塞进对象 |
| 一次修改改了所有行 | SQL 忘写 WHERE | **致命**。先用 SELECT 试 WHERE 条件 |
| `DELETE /api/account/abc` 报"系统繁忙" | 参数类型转换失败，被兜底方法接住了 | 单独处理 `MethodArgumentTypeMismatchException` |
| 访问不存在的地址返回 200 | 兜底方法把 404 也接住了 | 单独处理 `NoResourceFoundException` + `@ResponseStatus` |
| 编辑弹窗一输入，表格就跟着变 | `form.value = row` 指向同一对象 | 改成复制一份 |
| HTTP 非 200 时取不到错误信息 | axios 直接跳 catch 了 | 从 `e.response.data.msg` 取 |
| 删除不存在的记录返回"成功" | 没检查记录是否存在 | 先 `findById`，查不到就报错 |
| 改了规则却还能绕过 | 校验代码被复制成了两份，只改了一份 | **同一规则只能有一处实现** |

---

## 十、我自己的笔记（这里归你，随便写）

（写下你自己的话 —— 哪个地方卡住了、哪句话你用自己的理解记住了、还想问什么）

-


---

## 十一、CRUD 全部打通的里程碑

到这里，一个标准管理系统的核心骨架已经完整了：

```
后端
├── Controller   四个接口（查/增/改/删）        ✓
├── Service      业务校验 + 规则复用             ✓
├── Mapper       四个数据访问方法                ✓
├── XML          四条真实 SQL                    ✓
└── 全局异常处理  统一格式 + 分级处理            ✓

前端
├── 表格          展示数据                       ✓
├── 新增弹窗      表单 + 前端校验                ✓
├── 编辑弹窗      复用弹窗 + 区分模式            ✓
└── 删除          二次确认                       ✓
```

**从明天开始，就可以做真正的「记账」功能了** —— 因为记账本质上就是
"对账单表做 CRUD"，而 CRUD 你现在已经会了。

---

## 十二、明天预告（Day6）

**建 `bill` 账单表，开始做真正的记账功能。**

要学的新东西：
- **第二张表**：账单表要记录"金额、类型（收入/支出）、分类、日期、备注、所属账户"
- **表与表的关系**：一条账单属于某个账户 → **外键**（这个概念第一次出现）
- **`@RequestParam`**：终于要用到第三种传参方式了，用来做"按类型筛选账单"
- **一次请求写两张表**（记账要同时改账户余额）→ **`@Transactional` 事务**（重要！）

明天开始就有"真正的业务复杂度"了 —— 你会发现前几天学的基础设施
（校验、异常处理、统一响应体）**全都派上用场**。
