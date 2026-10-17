# Day1 复盘卡 · 骨架与第一条链路

> 这是你的学习笔记，可以随意在里面加自己的话。我不会删改你写的任何内容。

---

## 一、今天真正做成的一件事

**让一条请求走完全程**：

```
浏览器点按钮
  → GET /api/account/list
  → AccountController   （接请求，靠 @RestController + @GetMapping 认出路径）
  → AccountService      （放业务规则，今天只是转发）
  → AccountMapper       （接口，只有方法名，没有实现）
  → AccountMapper.xml   （真正的 SQL 在这里）
  → MySQL               （执行 SQL，返回 2 行）
  → 原路返回，MyBatis 把每行塞进 Account 对象
  → 包装成 {code, msg, data}
  → Jackson 转成 JSON
  → 浏览器渲染成表格
```

**判断标准**：页面上的数字和 DataGrip 里 `SELECT * FROM account` 的结果一致 → 链路是通的。

---

## 二、三个关键概念（今天只记这三条）

### 1. MyBatis 的"暗号"——接口和 XML 靠什么对上

| 暗号          | 接口里                                                                        | XML 里                                                    |
| ----------- | -------------------------------------------------------------------------- | -------------------------------------------------------- |
| ① namespace | `package com.wuweiqiu.moneylog.mapper;` + `public interface AccountMapper` | `namespace="com.wuweiqiu.moneylog.mapper.AccountMapper"` |
| ② 方法名       | `List<Account> findAll();`                                                 | `<select id="findAll">`                                  |

**这两个字必须一字不差。** 对不上就报：

```
org.apache.ibatis.binding.BindingException: Invalid bound statement (not found):
com.wuweiqiu.moneylog.mapper.AccountMapper.findAll
```

**注意**：这个错**启动时不报，调用接口才报**（HTTP 500，响应体里没有任何有用信息，真正的原因只在日志里）。

### 2. 日志的作者 ≠ 数据的来源

|                  | 谁                        | 证据                                                                                |
| ---------------- | ------------------------ | --------------------------------------------------------------------------------- |
| 这句日志**是谁打印的**    | **我们的 Java 程序**（MyBatis） | "谁打印的"那列写着 `c.w.m.mapper.AccountMapper.findAll` —— 是个 Java 类名，MySQL 不可能知道 Java 类名 |
| 那个 `2` **是从哪来的** | **MySQL** 数出来的           | 表里确实只有 2 行                                                                        |

**两套程序，两份日志，各写各的：**

|      | 应用日志                                                | MySQL 自己的日志                                                                 |
| ---- | --------------------------------------------------- | --------------------------------------------------------------------------- |
| 位置   | `D:\dev\moneylog\logs\moneylog.log`                 | `C:\ProgramData\MySQL\MySQL Server 8.0\Data\ADMIN.err`                      |
| 内容   | `c.w.m.mapper.AccountMapper.findAll : <== Total: 2` | `[InnoDB] initialization has started` / `mysqld.exe: ready for connections` |
| 更新时机 | 每次请求都写                                              | 只在 MySQL 启动那天写过                                                             |

MySQL 也能记录收到的 SQL（`general_log`），但**默认是关的**，所以我们的查询没出现在它那边。

### 3. MyBatis 的三个专属标志

看到这三个记号，就知道是 MyBatis 在说话：

```
==>  Preparing: SELECT id, name, balance, created_at FROM account ORDER BY id
==> Parameters:
<==      Total: 2
```

- `Preparing` = 我准备执行这条 SQL
- `Parameters` = 我传了哪些参数（今天没有条件，所以是空的）
- `Total` = 数据库返回了几行

**要看到这三行，必须开 SQL 日志**（`application.yml` 里把 mapper 包设成 `debug`）。

---

## 三、关键词（混个眼熟就行，后面每天都会遇到）

| 分类      | 词                                                                                                            |
| ------- | ------------------------------------------------------------------------------------------------------------ |
| 注解      | `@SpringBootApplication` `@RestController` `@RequestMapping` `@GetMapping` `@Service` `@Autowired` `@Mapper` |
| MyBatis | `namespace` `id` `resultType` `mapper-locations` `type-aliases-package`                                      |
| 概念      | 分层架构、动态代理、依赖注入、驼峰映射、连接池(HikariCP)                                                                            |
| 工具      | `mvn clean compile` / `mvn spring-boot:run` / `where` / `sysdm.cpl`                                          |

---

## 四、常见错误对照表（今天的实战版）

| 现象                                           | 原因                                      | 去哪查                                   |
| -------------------------------------------- | --------------------------------------- | ------------------------------------- |
| `Invalid bound statement (not found)`        | namespace / id / mapper-locations 三处没对上 | 核对两个暗号                                |
| 接口返回字段全是 `null`                              | 缺 setter，或没开驼峰映射                        | 看实体类；看 `map-underscore-to-camel-case` |
| 返回空对象 `{}`                                   | 缺 getter（Jackson 只认 getter）             | 看实体类                                  |
| `No qualifying bean of type 'AccountMapper'` | Mapper 没被扫描到                            | 看接口上有没有 `@Mapper`                     |
| 启动报 `Failed to configure a DataSource`       | yml 没被读到，或 url/密码错                      | 看 `application.yml` 位置和内容             |
| 启动报 `Port 8080 was already in use`           | 端口被占                                    | 换端口，或杀掉旧进程                            |
| 报时区错 / `Public Key Retrieval is not allowed` | JDBC URL 少了参数                           | 对照 URL 四个参数                           |
| `404`                                        | 路由找不到                                   | 拼一遍类上路径 + 方法上路径                       |
| `405`                                        | 路径对，但请求方法不对                             | GET/POST 写错了                          |

---

## 五、排错套路（比记答案重要）

1. **先读报错原话**，不要慌，看它到底在说什么
2. **找共同根因**：多个报错往往是一个原因（比如 JAVA_HOME 填错导致三个命令全挂）
3. **从外往里验**：用完整路径绕过环境变量单独验程序 → 再验变量生效 → 最后验工具识别
4. **500 一定要看日志**，响应体里没有有用信息
5. **一次只改一个变量**，改完立刻验，否则不知道是谁起的作用

---

## 六、环境知识（Windows，已经踩过一遍了）

- `JAVA_HOME` = **JDK 根目录**（`D:\javaa`），**不是** `bin`，更不是 `...\bin\java.exe`
- Path 拼接顺序：**系统 Path 在前，用户 Path 在后** → 要覆盖旧 Java 必须改**系统**变量
- `...\Oracle\Java\javapath` 是 Oracle 安装器的转发目录，会抢走 `java` 命令，已删掉
- 改了环境变量必须**重开终端**（进程启动时才读一次）
- Spring Boot 配置优先级：**命令行参数 > 环境变量 > application.yml**

---

## 七、我自己的笔记（这里归你，随便写）

（在这里写下你自己的话——哪个地方卡住了、哪句话你用自己的理解记住了、还想问什么）

- 我原来以为日志是数据库打印的，其实是Java程序打印的
- 

---

## 八、明天的预告

Day2 会用 **Vue3 + Vite + Element Plus** 替换掉现在这个静态 HTML 页面。
在正式上框架之前，会先让你看清"现在这个页面到底是怎么跟后端说话的"——因为框架只是把这件事包装得更舒服，原理不变。
