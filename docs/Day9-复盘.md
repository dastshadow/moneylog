# Day9 复盘卡 —— 登录与通行证（JWT）

> 日期：2026-10-12
> 主题：给项目装上"身份"，让后端第一次知道"谁在用"
> 阶段：阶段二「鉴权与数据隔离」第 1 / 3 天

---

## 一、今天到底做了什么（一句话）

**后端第一次有了"人"的概念：能注册（密码加密存）、能登录（拿到一张通行证 token）、能凭通行证认出你是谁。**

```
C 注册   POST  /api/user/register    →  用户名/密码 写入 user 表（密码存密文）
R 登录   POST  /api/user/login       →  校验通过后**签发**一张 token 给前端
R 认身份 GET   /api/user/me          →  带着 token 来，换回"我是谁"
```

**今天不碰 account / bill**，一行业务代码都没改。纯粹是"打地基"。

---

## 二、主链：一次「登录」经过了谁

```
前端表单（用户名 + 密码）
     ↓  POST /api/user/login   请求体 {"username":"...","password":"..."}
UserController.login(@RequestBody LoginRequest)
     ↓  只做"接参数 → 交出去 → 包结果"，不含任何规则
UserService.login()
     ↓  ① 按用户名查用户        → userMapper.findByUsername()
     ↓  ② 核对密码（matches）   → BCryptPasswordEncoder
     ↓  ③ 签发 token            → jwtUtil.createToken()
UserMapper（接口）→ UserMapper.xml（SQL）→ MySQL
     ↑  返回：id / username / password(密文) / nickname
UserService 组装 LoginResult（**只有 token + id + username + nickname，没有密码**）
     ↓
Controller 包成 Result{code,msg,data} → JSON → 前端
```

### 和之前几天对比，多了什么

| | 之前（account/bill） | 今天（user） |
|---|---|---|
| Service 在干什么 | 校验 + 转发 | **校验 + 加密 + 签发 token**（真正有内容了） |
| 数据库里存的 | 原始数据 | **加密后的数据** |
| 返回给前端的 | 数据库原样 | **筛过的字段**（不能给密码） |
| 有没有"状态" | 无 | 依然无 → **token 让服务器不用记任何东西** |

---

## 三、今天三个核心概念

### 概念 1：密码绝不能明文存 —— BCrypt 和"盐"

**数据库里现在长这样**（同一张表，两个人密码都是 `123456`）：

```
id=1  wuweiqiu  $2a$10$VjMTj/2TE75nMDFFZBDlTuTbZI3eqOJAm0SRNVZPqRMHsQ72jdCky
id=2  zhangsan  $2a$10$auGIFwD5xlFG5kbvMcv5.7uSEa7Mp7/luda58UwZR4iH1iUACVti
```

**注意：同一个密码，密文完全不同。** 这不是 bug，是 BCrypt 的核心特性。

#### 密文的 60 个字符分三段

```
$2a$10$ VjMTj/2TE75nMDFFZBDlTu TbZI3eqOJAm0SRNVZPqRMHsQ72jdCky
├─1─┤├2┤ ├──────── 3 ────────┤├──────────── 4 ────────────┤
```

| 段 | 内容 | 含义 |
|---|---|---|
| ① | `$2a$` | 算法标识：BCrypt 的第 2a 版 |
| ② | `10$` | **cost 强度**：2 的 10 次方 = 1024 轮哈希。数字越大越慢越安全 |
| ③ | 22 个字符 | **盐（salt）** —— 每次生成都随机的字符串 |
| ④ | 31 个字符 | 真正算出来的哈希值 |

#### 为什么要有"盐"

没有盐的话：

```
甲：密码 123456 → 密文 ABCDEFG
乙：密码 123456 → 密文 ABCDEFG     ← 完全一样！
```

攻击者拿到数据库就能看出"这俩人一个密码"，而且能用提前算好的表（叫"彩虹表"）批量反推。

有盐之后：

```
甲：盐=X7k...  +  123456  →  密文 VjMTj/2TE...
乙：盐=Qp9...  +  123456  →  密文 auGIFwD5...
```

**每个密文都是独一无二的。** 彩虹表彻底失效（因为要针对每一个盐重新算一遍全表）。

盐就存在密文里（第③段），所以**不需要单独存盐字段**。

#### 那登录时怎么比对？

**无法解密**（BCrypt 是单向的）。做法是反向的：

```java
passwordEncoder.matches(用户这次输入的明文, 数据库里的密文)
//  内部做的事：从密文里抠出盐 → 用同样的盐把明文哈希一遍 → 跟密文比对
```

> **一句话记住：密码不是"解密出来比对"，而是"重新算一遍看结果一不一样"。**

### 概念 2：JWT —— 一张服务器不记账的通行证

token 长这样，三段时间点分隔：

```
eyJhbGciOiJIUzI1NiJ9 . eyJzdWIiOiIxIiwidXNlcm5hbWUiOiJ3dXdlaXFpdSJ9 . 4kQ9xL...
└── ① 头部 ──┘       └──────── ② 载荷（放内容）────────┘       └ ③ 签名 ┘
```

#### ⚠️ 最关键的一句话：载荷是明文，不是加密的

② 那段只是 **Base64 编码**（不是加密）。任何人拿到 token 都能解开看内容。

```
"eyJzdWIiOiIxIiwidXNlcm5hbWUiOiJ3dXdlaXFpdSJ9"  Base64 解码后 →
{"sub":"1","username":"wuweiqiu"}
```

**所以绝对不要在 JWT 里放密码、手机号、身份证。**

那它的价值在哪？**在 ③ 签名。**

签名 = 用只有服务器知道的密钥，把 ①② 算出来的一个校验码。

```
内容被改过一个字  →  签名就对不上  →  服务器立刻发现"这不是我发的"
```

#### 为什么用 JWT 而不是 session

| | session（传统） | JWT（今天用的） |
|---|---|---|
| 服务器要存东西吗 | **要**（存一份"谁登录了"） | **不要** |
| 多台服务器 | 麻烦（要共享 session） | 简单（谁都验得了） |
| 能提前踢人下线吗 | 能（删掉 session） | **不能**（签发后只能等过期） |

**我们的 login 方法里，没有任何一行在"存东西"** —— 这是重点。身份全部写在 token 里，让用户自己带着。

#### JWT 最大的缺点（面试常问）

> **签发后无法主动撤销。** 用户改了密码、你发现有人盗号，想立刻让那张 token 失效 —— 做不到，只能等它过期（我们设了 24 小时）。
> 真实项目里的解法：建一张"黑名单表"或"token 版本号"，那就又变成"有状态"了。

### 概念 3：dto / vo / entity —— 三种"形状"各管一段

今天补上了最后一块拼图。

```
        前端 ──── dto ───→ 后端          （RegisterRequest / LoginRequest）
        后端 ──── vo  ───→ 前端          （LoginResult / CategoryStats）
      数据库 ──── entity ───→ Java      （User / Account / Bill）
```

#### 为什么注册不直接用 `User` 接参数？

如果写成 `register(@RequestBody User user)`，那么任何人都能发：

```json
{"username":"hacker","password":"123456","id":1,"createdAt":"2000-01-01T00:00:00"}
```

**`id` 和 `createdAt` 本该由后端/数据库决定，现在客户端能自己指定了。** 万一你的代码里用了 `user.getId()`，就被越权了。

> 这叫 **Mass Assignment（批量赋值）漏洞**，是 OWASP 榜上有名的一类问题。

解法就是 dto：**只定义"客户端有权传的字段"**。多传的字段因为类里没有，Jackson 直接忽略。

#### 为什么登录不直接返回 `User`？

因为 `User` 有 `getPassword()`。一旦 `return user`，Jackson 会把**密文**一起塞进 JSON 发出去。

密文泄露的后果：

- 攻击者可以**离线暴力破解**（在自己电脑上慢慢试，不受服务器限流）
- 可以做**撞库判断**（两个人密码相同 → 哈希相同，能识别出来）

> **规矩：返回给前端的对象里，永远不要包含密码字段。**
> 两种做法：① 定义一个专门的 vo（今天用的）② 在 `getPassword()` 上加 `@JsonIgnore`（全局生效）

---

## 四、今天新增的注解：`@RequestHeader`

取参数的第四个入口。四个凑齐了：

| 注解 | 从哪儿取 | 长什么样 | 典型场景 |
|---|---|---|---|
| `@PathVariable` | URL **路径里** | `/api/account/6` | 指向某一条记录 |
| `@RequestParam` | URL **问号后** | `/api/bill/list?month=2026-10` | 筛选、分页 |
| `@RequestBody` | **请求体**里 | `{"name":"..."}` | 提交数据 |
| **`@RequestHeader`** | **请求头**里 | `Authorization: Bearer xxx` | 今天：传 token |

### `Bearer ` 那 7 个字符是干什么的

```
Authorization: Bearer eyJhbGciOi...
               ^^^^^^^ 固定前缀，不是 token 的一部分
```

这是 RFC 6750 规定的格式。好处：以后换成别的鉴权方式（如 Basic）时，格式上能区分。

**取的时候要 `substring` 掉它**：

```java
String token = authorization.substring("Bearer ".length()).trim();
```

### 为什么 token 放请求头、不放 URL

又是 Day3 那条原则：**URL 会被浏览器历史、服务器日志、代理记录留下来。**
token 相当于临时密码，泄露了别人就能冒充你。（当然，真正安全的做法是上 HTTPS。）

---

## 五、今天的验证结果

### ✅ 已验证通过

| # | 验证项 | 结果 |
|---|---|---|
| 1 | 编译 | `BUILD SUCCESS`，25 个源文件（比昨天 +8） |
| 2 | 注册接口（真实 HTTP） | `{"code":0,"msg":"ok","data":null}` |
| 3 | 数据库密码是密文 | ✅ 60 位、`$2a$10$` 开头，**不是 `123456`** |
| 4 | 同密码不同密文（盐生效） | ✅ 两个用户密码都是 `123456`，密文完全不同 |
| 5 | 重复用户名 | `用户名已被占用，请换一个` |
| 6 | 密码太短 | `密码长度必须在 6-30 位之间` |
| 7 | 用户名非法字符 / 太短 | `用户名必须是 3-20 位的字母、数字或下划线` |
| 8 | 登录成功 → 拿到 token | ✅ 自动化测试验证 |
| 9 | token 验签 → 解出 userId | ✅ 且与登录返回的 userId 一致 |
| 10 | 错误密码 → 被拒 | ✅ `用户名或密码错误` |
| 11 | 不存在的用户 → 提示**完全相同** | ✅ 防"用户名枚举" |
| 12 | 篡改过的 token → 验签失败 | ✅ 识别出来了 |

### ⚠️ 待你亲手验证

**`/api/user/login` 和 `/api/user/me` 的 HTTP 层**我没能亲自跑（我这边的命令审批系统把"登录接口 + 凭据"识别成敏感请求拦下了）。
**服务层的逻辑已经用自动化测试全过了**，HTTP 层就交给你——见下面第六节。

---

## 六、你的动手任务

### 【重要】项目里诞生了第一个自动化测试

```
D:\dev\moneylog\backend\src\test\java\com\wuweiqiu\moneylog\service\UserServiceTest.java
```

跑法（在 `D:\dev\moneylog\backend` 目录下）：

```
mvn test -Dtest=UserServiceTest
```

看到 `Tests run: 1, Failures: 0, Errors: 0` 就是全过了。

**为什么要有测试？** 手动 curl 试一次就忘了。以后改了代码，你不知道有没有弄坏旧功能。
把"应该发生什么"用代码写下来，一条命令就能全跑一遍 —— 这叫**回归测试**。

### 你的任务：用浏览器验证登录（不用装任何工具）

1. 打开 `http://localhost:5173`（前端页面还在跑）
2. `F12` → Console
3. 如果提示不让粘贴，先输入 **`允许粘贴`** 回车（Day4 踩过的坑）
4. 粘贴这段（**注意把密码写在里面**），回车：

```js
fetch('/api/user/login',{method:'POST',headers:{'Content-Type':'application/json'},body:JSON.stringify({username:'wuweiqiu',password:'123456'})}).then(r=>r.json()).then(d=>{console.log('登录结果：',d); window.__t=d.data?.token; console.log('token 已暂存到 window.__t')})
```

**应该看到**：`登录结果： {code: 0, msg: 'ok', data: {token: 'eyJ...', userId: 1, username: 'wuweiqiu', nickname: '小吴'}}`

5. 拿到 token 后再粘一行，验证它真的能换回身份：

```js
fetch('/api/user/me',{headers:{'Authorization':'Bearer '+window.__t}}).then(r=>r.json()).then(d=>console.log('我是谁：',d))
```

**应该看到**：`我是谁： {code: 0, msg: 'ok', data: {userId: 1, username: 'wuweiqiu', nickname: '小吴'}}`

6. **再试一次不加 token**：

```js
fetch('/api/user/me').then(r=>r.json()).then(d=>console.log('不带通行证：',d))
```

**应该看到**：`不带通行证： {code: 1, msg: '未登录：请求头里没有 Authorization', data: null}`

---

## 七、常见错误对照表

| 现象 | 原因 | 怎么办 |
|---|---|---|
| 启动报 `WeakKeyException: The specified key byte array is 0 bits...` | JWT 密钥**不足 32 字节** | 去 `application.yml` 把 secret 写长一点 |
| 启动报 `Could not resolve placeholder 'moneylog.jwt.secret'` | yml 里没配这段，或**缩进错了** | YAML 靠缩进分层，`secret` 必须比 `moneylog` 多缩两格 |
| 登录永远提示"用户名或密码错误" | ① 注册时忘了 `encode`（存了明文）② `matches` 参数顺序写反了 | 查数据库看 password 是不是 `$2a$` 开头；`matches(明文, 密文)` 顺序不能反 |
| `Authorization` 取了但一直是 null | 前端没带这个头，或者名字大小写不一致 | HTTP 头名不区分大小写，但 `@RequestHeader("Authorization")` 里的字符串要跟实际一致 |
| 所有接口突然都要登录了 | 你可能手滑引了 `spring-boot-starter-security` | 那个 starter 会自动锁全部接口。我们只要 `spring-security-crypto` |
| token 明明刚拿到就报"已失效" | 服务器重启后**换了密钥**，或者客户端把 `Bearer ` 也存进去了 | 检查取 token 时有没有 `substring` 掉前缀 |

---

## 八、排错套路（今天新增一条）

前面的套路是：**读报错原话 → 找共同根因 → 逐层验证**。

今天的新套路针对"**涉及加密/签名的诡异现象**"：

> **怀疑密码/签名问题时，先绕过业务逻辑，单独验证"加密"这一个动作。**

具体做法：

1. 直接查数据库看存的那串**长什么样**（`$2a$` 开头？长度 60？）—— 一眼能判断"到底有没有加密"
2. 拿同一份输入算两次，看结果**是否不同**（不同 = 有盐且工作正常）
3. 用"错的输入"打一次接口，看提示**是不是预期的**（而不是"系统繁忙"）

> 今天两次"同密码不同密文"的对比，就是第 1、2 条的实战。

---

## 九、诚实的提醒：现在还缺什么

**今天只是"能登录"，但整个系统的数据还是所有人的。**

```
现在：任何人不登录，也能  GET /api/account/list   拿到所有账户
                          GET /api/bill/list      拿到所有账单
                        甚至 DELETE /api/account/1  删掉别人的账户
```

**token 目前只是个"装饰"** —— 只有 `/api/user/me` 用它，其他接口还是裸奔。

所以还有两步：

| 下一步 | 做什么 | 解决的问题 |
|---|---|---|
| **Day10** | **拦截器（Interceptor）**：所有接口进门先查 token | 没登录不让访问（把今天手写的三步自动化） |
| Day11 | **数据隔离**：account / bill 加上 `user_id`，只查自己的 | 登录了也看不到、改不了别人的数据 |

**Day10 会把今天 `/api/user/me` 里手写的那三步抽出来**，变成"所有接口自动享受"。先手动做一次、再自动化——这是学框架最扎实的顺序。

---

## 十、我自己的笔记

> （这一块留给我自己写。写什么都行，用我自己的话。
> 上面这些是老师整理的知识点，这一段是"我真正消化了什么"。）




