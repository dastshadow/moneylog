# MoneyLog · 个人记账本

一个练手用的全栈项目：记录每一笔收支，按分类和月份统计，多用户各自管理自己的账本，并且能部署上线。

> **这是一个学习项目。** 代码以"把每一层讲清楚"为目标，注释量很大（甚至偏多），
> 不是生产级实现。用它来理解一个 Web 系统的完整链路，而不是直接上线用。

---

## 功能

| 模块 | 内容 |
|---|---|
| **账户管理** | 新增 / 修改 / 删除 / 列表 |
| **记账** | 记支出或收入，关联账户、分类、日期、备注 |
| **余额联动** | 记账时自动更新账户余额，用事务保证"账单"和"余额"同时成功或同时回滚 |
| **统计** | 按分类汇总、按月收支趋势、月度结余（`GROUP BY` 聚合查询） |
| **图表** | ECharts 饼图（支出构成）+ 柱状图（月度趋势） |
| **用户体系** | 注册 / 登录（密码 BCrypt 加密存储，登录签发 JWT） |
| **数据隔离** | 每个用户只能看到、只能操作自己的账户和账单 |

---

## 技术栈

**后端**

- JDK 21
- Spring Boot 3.5.15
- MyBatis（`mybatis-spring-boot-starter` 3.0.5，SQL 写在 XML 里，不用 MyBatis-Plus）
- MySQL 8.0
- JWT（jjwt 0.12.7）+ BCrypt（spring-security-crypto）

**前端**

- Vue 3 + Vite
- Element Plus
- axios
- ECharts

**部署**

- Maven 打可执行 jar（内嵌 Tomcat，只需 JDK）
- Nginx：托管前端静态文件 + 反向代理 `/api`

---

## 项目结构

```
moneylog/
├── backend/                       后端
│   ├── pom.xml
│   └── src/main/
│       ├── java/com/wuweiqiu/moneylog/
│       │   ├── MoneylogApplication.java
│       │   ├── common/            Result / 全局异常处理 / JWT 工具 / 拦截器
│       │   ├── config/            WebConfig（拦截器注册 + 白名单）
│       │   ├── controller/        接请求
│       │   ├── service/           业务规则、事务
│       │   ├── mapper/            SQL 接口
│       │   ├── entity/            与表一一对应
│       │   ├── dto/               接收前端入参
│       │   └── vo/                返回给前端 / 统计结果
│       └── resources/
│           ├── application.yml
│           └── mapper/            ← 真正的 SQL 在这个目录的 XML 里
├── frontend/                      前端
│   └── src/
│       ├── api/request.js         axios 统一封装（自动带 token、统一处理 401）
│       ├── components/            LoginPanel / StatsPanel
│       └── App.vue
└── docs/                          建表脚本 + 每天的学习复盘
```

**分层约定**：Controller 只管收发、Service 管规则和事务、Mapper 只管取数、SQL 在 XML 里。

---

## 快速开始

### 1. 建数据库

```bash
mysql -uroot -p < docs/schema.sql
```

（或者用 DataGrip / Navicat 打开 `docs/schema.sql` 全选执行）

### 2. 改配置

编辑 `backend/src/main/resources/application.yml`：

```yaml
spring:
  datasource:
    url: "jdbc:mysql://localhost:3306/moneylog?useUnicode=true&characterEncoding=utf8&serverTimezone=Asia/Shanghai&allowPublicKeyRetrieval=true&useSSL=false"
    username: root
    password: "改成你自己的 MySQL 密码"

moneylog:
  jwt:
    secret: "改成一段足够长的随机字符串"
```

### 3. 启动后端

```bash
cd backend
mvn spring-boot:run
```

后端在 `http://localhost:8080`。

### 4. 启动前端

```bash
cd frontend
npm install
npm run dev
```

打开 `http://localhost:5173`，先**注册一个账号**（新账号默认没有数据，自己建账户、记几笔账）。

> 前端开发服务器（5173）通过 `vite.config.js` 里的 `server.proxy` 把 `/api` 转发到 8080，
> 这样浏览器认为请求是同源的，不会触发跨域限制。

---

## 打包与部署

```bash
# 后端 → 一个可执行 jar（内嵌 Tomcat，运行只需要 JDK）
cd backend
mvn package
java -jar target/moneylog-0.0.1-SNAPSHOT.jar

# 前端 → 静态文件
cd frontend
npm run build      # 产物在 dist/
```

**生产环境的建议形态**：

```
浏览器 → Nginx:80 ─┬─→ frontend/dist  （HTML / JS / CSS，开 gzip）
                    └─→ /api/*  → 127.0.0.1:8080（Spring Boot）
```

两点要注意：

1. **敏感配置不要打进 jar**。在 jar 同目录放 `config/application.yml`，只写要覆盖的项
   （数据库密码、JWT 密钥）。Spring Boot 的加载优先级是：
   **命令行参数 > 环境变量 > 外置 config > jar 内的 application.yml**。
2. **后端只监听本机**，避免被绕过 Nginx 直连：

   ```yaml
   server:
     address: 127.0.0.1
   ```

---

## 开发笔记

`docs/` 下按天记录了每阶段的实现过程、踩过的坑和排查思路：

```
Day1   环境与最小只读链路（浏览器 → Controller → Mapper → SQL → MySQL）
Day2   Vue3 工程 + 跨域与代理
Day3   新增（POST + @RequestBody + 自增主键回填）
Day4   前端校验不可信 → 后端校验 + 全局异常处理
Day5   修改 / 删除（@PathVariable）+ 异常分级
Day6   第二张表：外键与连表查询
Day7   事务：余额联动与回滚实验
Day8   GROUP BY 聚合统计 + ECharts
Day9   BCrypt + JWT
Day10  拦截器：所有 /api 默认需要登录
Day11  数据隔离：user_id 与三步走数据迁移
Day12  部署：jar + 配置外置 + Nginx
Day13  Git 入门
```

---

## 已知限制

诚实地列出来：

- 没有 HTTPS
- 账单只支持新增和查询，**暂不支持修改和删除**
- 账单列表没有分页和条件筛选（数据量大了会慢）
- 没有单元测试覆盖（只有 3 个集成测试：登录、拦截器、数据隔离）
- 没做过压力测试，不要用真实财务数据

---

## License

仅供学习参考。
