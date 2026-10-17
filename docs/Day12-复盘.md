# Day12 复盘卡 —— 部署上线（从源码到"能见人"的服务）

> 今天全程你自己动手：命令自己敲、文件自己改。
> 这份卡是把今天的操作整理成**可以照着复现的手册**，以后换台电脑也能照着走。

---

## 一、今天完成了什么

```
① mvn package           源码  →  可执行 jar（自带 Tomcat）
② npm run build         源码  →  静态文件 dist
③ 配置外置              密码/密钥  →  移出 jar，放进 config/application.yml
④ Nginx 前后端分离       静态归 Nginx，/api 反代到 8080
⑤ 8080 只监听本机        堵住绕过 Nginx 的后门
```

**最终的架构**：

```
浏览器
  │  http://localhost  (80)
  ▼
Nginx 1.28.3  ──┬─→ D:\dev\moneylog\frontend\dist\   （HTML/JS/CSS，gzip 压缩）
                │
                └─→ /api/*  proxy_pass
                              ▼
                     Spring Boot (127.0.0.1:8080)  ← 外面访问不到
                              ▼
                          MySQL (3306)
```

---

## 二、开发模式 vs 生产模式（今天最核心的一张表）

| | 开发模式 | 生产模式 |
|---|---|---|
| 后端启动 | `mvn spring-boot:run` | `java -jar xxx.jar` |
| 后端依赖 | JDK + Maven + 源码 | **只要 JDK + 一个 jar** |
| 前端启动 | `npm run dev`（Vite） | 静态文件（Nginx 发） |
| 前端依赖 | Node + npm + 源码 | 只要一个 Web 服务器 |
| 改代码 | 热更新，立刻生效 | 要**重新构建 + 重启** |
| 端口 | 5173 + 8080 | **只有 80** |
| 跨域 | 有（靠 Vite proxy 绕） | 没有（同源） |

**一句话**：开发模式为了**方便改**，生产模式为了**稳定跑**。

---

## 三、构建产物到底装了什么

`moneylog-0.0.1-SNAPSHOT.jar` 28 MB，解开看：

```
BOOT-INF/classes/          ← 我们自己的 .class + application.yml + Mapper XML + static/
BOOT-INF/lib/              ← 所有第三方 jar（Spring、MyBatis、MySQL 驱动、jjwt…）
org/springframework/boot/loader/  ← 启动器
```

| 装进去了 | 仍然在外面 |
|---|---|
| 自己编译的代码 | **JDK**（没有它 `java` 命令都敲不了） |
| 所有第三方依赖 | **MySQL**（独立进程） |
| **内嵌 Tomcat**（所以不用另装服务器） | |
| 资源文件（yml / XML / 静态页） | |

**那个 `.jar.original` 是什么**：52 KB，是"还没塞依赖的瘦 jar"。`spring-boot:repackage` 把瘦 jar 换成胖 jar，换下来的留成 `.original`。**体积差 500 倍**，这个数字就是"依赖占了多少"的直观答案。

---

## 四、dist 里藏着三个知识点

### ① 文件名里的哈希 `index-CKsBZIAR.js`

内容哈希。**作用：cache busting（缓存失效）**。
浏览器会缓存 JS。改代码后如果文件名不变，用户还在用旧缓存 → "我明明改了怎么没生效"。
内容一变文件名就变 → 浏览器当成新文件重新下载。

### ② `gzip: 712.03 kB` 那一列

```
index-CKsBZIAR.js   2,174.38 kB │ gzip: 712.03 kB
                    真实大小      压缩后
```

Nginx 配 `gzip on;` 就真的这么发。**体积降到 1/3**，手机网络差别巨大。

### ③ `index.html` 只有 476 字节

```html
<body><div id="app"></div></body>
```

**整个页面是个空壳**，真正的内容由 JS 生成。这解释了两个现象：

- **双击 `dist\index.html` 会白屏**：`<script src="/assets/...">` 是**绝对路径**，
  `file://` 下会解析成 `file:///D:/assets/...`（跑到 D 盘根目录）→ JS 加载失败 → Vue 没启动
- **dist 必须由 Web 服务器托管**，因为需要有人把 `/assets/...` 解析到正确文件

---

## 五、配置外置（今天最值钱的一节）

### 为什么要做

`application.yml` 被打进了 jar：`BOOT-INF/classes/application.yml`。
**谁拿到这个 jar，解开就能看到你的数据库密码和 JWT 密钥。**

JWT 密钥泄露 = 别人能**伪造任意用户的 token**，冒充任何人登录。

### 怎么做

Spring Boot 的配置优先级（高 → 低）：

```
命令行参数  >  环境变量  >  外置配置文件  >  jar 内的 application.yml
```

外置文件的搜索路径：**`./config/` → `./` → jar 内部**

所以在 jar 同目录建 `config/application.yml`，**只写要覆盖的项**就行：

```yaml
server:
  address: 127.0.0.1      # 只监听本机

spring:
  datasource:
    password: "123456"

moneylog:
  jwt:
    secret: "部署时提供的长随机字符串"
```

### ⚠️ 一个必须记住的陷阱：启动成功 ≠ 数据库通

今天实测发现：**密码故意写错，应用照样启动成功。**

```
09:35:30  Started MoneylogApplication       ← 看起来一切正常
09:45:53  HikariPool-1 - Starting...        ← 10 分钟后（第一次请求）才连库
          Access denied for user 'root'@'localhost'
```

**原因**：Spring Boot 3 + HikariCP **懒加载** —— 启动时不建任何真实连接，
第一次需要查数据时才建池。

> **部署检查清单里必须有这一条：打一个真正查数据库的接口。**
> 只看 `Started MoneylogApplication` 会漏掉"整个站其实是坏的"。

### 三个坑

| 坑 | 后果 |
|---|---|
| **YAML 用 Tab 缩进** | 直接报错（必须空格） |
| 文件名不是 `application.yml` | 不被识别 |
| **把 `config/` 提交进 Git** | 外置就白做了（见下） |

### `.gitignore` 里的坑（真实的）

```gitignore
config/      ← ❌ 会匹配【任意层级】叫 config 的目录
               把 backend/src/main/java/.../moneylog/config/ 也忽略了
               （那里面有 WebConfig.java！）

/config/     ← ✅ 只匹配仓库根目录
```

**规则：.gitignore 里的路径，能带前导斜杠就带。**

### 更规范的做法：环境变量

```
set MONEYLOG_JWT_SECRET=xxxx
set SPRING_DATASOURCE_PASSWORD=xxxx
java -jar moneylog-0.0.1-SNAPSHOT.jar
```

映射规则：**属性名全大写，`.` 和 `-` 换成 `_`**。好处是配置根本不在磁盘上。

### 换 JWT 密钥的副作用

密钥一换，**所有旧 token 立即失效**，用户被踢回登录页。
这是 JWT **唯一能"撤销凭证"**的手段（无状态的代价）。

---

## 六、Nginx 前后端分离

### 为什么要它

| | Spring Boot 兼管静态文件 | Nginx 在前面 |
|---|---|---|
| 静态文件 | Tomcat 发（为跑代码优化的） | Nginx 发（C 写的，专业） |
| 后端暴露 | 8080 直接对外 | 8080 只监听本机 |
| 附加能力 | 无 | gzip / HTTPS / 限流 / 负载均衡 / 日志 |

### 核心配置

```nginx
# http 块内
gzip  on;
gzip_min_length 1k;
gzip_types text/css application/javascript application/json image/svg+xml;
gzip_vary on;

server {
    listen       80;
    server_name  localhost;

    root   D:/dev/moneylog/frontend/dist;   # 正斜杠！
    index  index.html;

    location / {
        try_files $uri $uri/ /index.html;
    }

    location /api/ {
        proxy_pass http://127.0.0.1:8080;   # 不带路径 = 原样转发

        proxy_set_header Host              $host;
        proxy_set_header X-Real-IP         $remote_addr;
        proxy_set_header X-Forwarded-For   $proxy_add_x_forwarded_for;
        proxy_set_header X-Forwarded-Proto $scheme;
    }
}
```

### 每条配置在干什么

| 配置 | 作用 |
|---|---|
| `root D:/.../dist` | 静态文件的根目录。**必须正斜杠**，写 `D:\...` 会出问题 |
| `try_files $uri $uri/ /index.html` | 找不到文件就交给 index.html。以后上 vue-router，刷新深层路径靠它 |
| `proxy_pass http://127.0.0.1:8080` | 后面**不带路径**，所以 `/api/account/list` 原样转发，前缀保留 |
| `X-Real-IP` / `X-Forwarded-For` | 后端能拿到真实客户端 IP（否则日志里全是 127.0.0.1） |
| `X-Forwarded-Proto` | 以后上 HTTPS 时，后端才知道原始协议是 https |

### Nginx 操作四件套（生产里天天用）

```
nginx -t            ← 改完配置【先检查语法】
nginx -s reload     ← 重新加载配置，不中断服务
nginx -s stop       ← 停止
start nginx         ← 启动（Windows 下不加 start 会占住窗口）
```

> **为什么必须先 `-t`**：`reload` 时如果配置有错，Nginx **拒绝加载新配置、继续用旧的**（服务不中断）。
> 听起来安全，但你会以为"改完了怎么没效果"。

### 验证方法（不靠猜）

| 看什么 | 说明 |
|---|---|
| 响应头 `Server: nginx/1.28.3` | 页面确实是 Nginx 发的 |
| 响应头 `Content-Encoding: gzip` | 压缩真的开了 |
| `/api/ping` 返回 pong | 反代通了 |
| `/api/account/list` 返回 401 | 打到了后端，且拦截器在工作 |

---

## 七、最后一步：8080 只监听本机

`netstat` 里的区别：

```
0.0.0.0:8080      ← 监听所有网卡，同一 WiFi 的任何设备都能直连（后门）
127.0.0.1:8080    ← 只监听本机，外面只能经过 Nginx（正确）
```

配置就一行：

```yaml
server:
  address: 127.0.0.1
```

**不堵这个后门会失去什么**：

| 绕过 Nginx 后 | 后果 |
|---|---|
| 限流 / IP 白名单 | 全部失效 |
| HTTPS（配在 Nginx 上） | 明文传输 |
| 隐藏后端信息 | 暴露 Spring Boot / Java 版本 |
| 统一访问日志 | 有人狂刷后端，Nginx 日志一条都没有 |

---

## 八、完整操作手册（换台电脑照着走）

```bat
:: ========== 1. 构建后端 ==========
cd /d D:\dev\moneylog\backend
mvn clean package

:: ========== 2. 构建前端 ==========
cd /d D:\dev\moneylog\frontend
npm install
npm run build

:: ========== 3. 准备部署目录 ==========
mkdir D:\deploy-test\config
copy D:\dev\moneylog\backend\target\moneylog-0.0.1-SNAPSHOT.jar D:\deploy-test\

:: ========== 4. 写外置配置 D:\deploy-test\config\application.yml ==========
::   server.address / spring.datasource.password / moneylog.jwt.secret

:: ========== 5. 启动（用脚本） ==========
cd /d D:\deploy-test
start-all.bat

:: ========== 6. 停止 ==========
stop-all.bat
```

**Nginx 侧**：`D:\nginx\conf\nginx.conf` 改 `root` + 加 `location /api/` + 开 gzip，
然后 `nginx -t` → `nginx -s reload`。

**部署心法三句话**：**停旧 → 换新 → 启动**。
（不停旧版，`mvn package` 会因为 jar 被占用而失败）

---

## 九、今天踩到的坑（都是我判断失误，值得记）

### 坑 1：我以为"密码错 → 启动失败"

**实际**：启动成功，直到第一次访问数据库才炸。

**教训**：**框架的行为要实测，不能凭印象。**
`Started MoneylogApplication` 只证明 Web 层起来了，不证明数据库通。

### 坑 2：`.gitignore` 写 `config/` 会误伤代码包

项目里恰好有个 `moneylog/config/` 包（`WebConfig.java` 在那）。
`config/` 会连同它一起忽略 → 别人 clone 下来编译不过。

**教训**：`.gitignore` 的模式是"任意层级"匹配，**加前导斜杠收窄范围**。

### 坑 3：`dir target\*.jar` 看不到 `.jar.original`

因为实际文件名是 `xxx.jar.original`，**不以 `.jar` 结尾**。
是我题目出得不严谨，不是你看漏。

---

## 十、诚实的提醒：这还不算"真上线"

今天完成的是**单机部署**。真要给别人用，还差这些：

| 缺口 | 说明 | 优先级 |
|---|---|---|
| **HTTPS** | 现在全程明文，密码裸奔。真上线必须配证书 | 高 |
| **Linux 服务器** | 云服务器基本都是 Linux；Nginx 的 Windows 版官方明确说"不适合生产"（模拟 I/O，高并发不行）。**但配置语法完全一样**，学的能直接搬 | 高 |
| **进程守护** | 现在关了 cmd 窗口服务就没了。Linux 用 systemd，Windows 得用 nssm/服务 | 高 |
| **数据库备份** | 现在没有任何备份机制 | 高 |
| **日志轮转** | `moneylog.log` 会一直长，迟早撑满磁盘 | 中 |
| **前端静态文件冗余** | jar 里还带着一份 dist（约 2 MB），Nginx 那份才是真的 | 低 |
| **Git 版本管理** | 项目还没 `git init`，改坏了没法回退 | 高（学习层面） |

**这些不着急，但要知道它们在。** 一份诚实的部署清单，比"已经上线了"这句话有用。

---

## 十一、我自己的笔记

> （这块留给我自己写。用我自己的话，写我真正搞懂的东西。
> 上面是老师整理的，这一段是"我消化了什么"。）
