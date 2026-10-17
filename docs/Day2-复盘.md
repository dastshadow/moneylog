# Day2 复盘卡 · 前端工程与联调

> 这是你的学习笔记，可以随意加自己的话。我不会删改你写的任何内容。

---

## 一、今天做成的事

**把前端从"后端顺手发的一个静态页"升级成了独立的前端工程**，并且打通了前后端联调：

```
浏览器 (5173)
   │  请求 /api/account/list
   ↓
Vite 开发服务器 (5173)   ← 页面也是它发的
   │  代理转发（因为路径以 /api 开头）
   ↓
后端 Java (8080)
   │  Controller → Service → Mapper → XML → SQL
   ↓
MySQL
```

**最终效果**：打开 `http://localhost:5173`，页面自动显示表格，两行数据来自 MySQL。

---

## 二、项目结构（今天重构过，这是最终版）

```
D:\dev\moneylog\
├── .gitignore          ← 整个项目的忽略规则
├── backend\            ← 后端（完整独立）
│   ├── pom.xml         ← 后端依赖清单
│   ├── src\            ← 后端代码
│   ├── logs\           ← 后端日志
│   └── target\         ← 编译产物（自动生成）
├── frontend\           ← 前端（完整独立）
│   ├── package.json    ← 前端依赖清单
│   ├── vite.config.js  ← Vite 配置（代理写在这）
│   ├── index.html      ← 页面空壳
│   ├── node_modules\   ← 前端依赖（自动生成，体积大）
│   └── src\
│       ├── main.js     ← 前端入口
│       ├── App.vue     ← 根组件
│       └── components\
└── docs\               ← 学习笔记（就是我们写的复盘卡）
```

> **为什么重构**：之前后端在 `moneylog\src`、前端在 `moneylog\frontend\src`，
> **两个 `src` 撞名**，害你找了半天文件。现在 `backend` 和 `frontend` 各占一层，一眼分清。

---

## 三、后端 vs 前端 —— 一对孪生兄弟

这是今天最值钱的一张对照表。**理解了它，你就懂了前端工程是怎么组织的**：

| 干什么 | 后端 | 前端 |
|---|---|---|
| **依赖清单** | `pom.xml` | `package.json` |
| **装依赖的命令** | `mvn install` | `npm install` |
| **依赖下载器** | Maven | npm |
| **依赖存哪** | `~\.m2\repository` | `项目\node_modules\` |
| **程序入口** | `MoneylogApplication.java` 的 `main()` | `src/main.js` 的 `createApp()` |
| **配置文件** | `application.yml` | `vite.config.js` |
| **启动命令** | `mvn spring-boot:run` | `npm run dev` |
| **端口** | 8080 | 5173 |
| **产物目录** | `target\` | `dist\` |

**记住这张表，以后换任何语言/框架都能对上号。**

---

## 四、三个关键概念

### 1. 组件树 —— 页面是怎么拼出来的

```
index.html        里面只有一个空壳：<div id="app"></div>
     ↓
App.vue           【根组件】决定往空壳里装什么
     ↓
子组件（如 HelloWorld.vue）
     ↓
页面显示的内容
```

**核心认知：`App.vue` 里写什么，页面就显示什么。**

- 换掉 `App.vue` 的内容 = 换总装图纸
- 子组件文件还在，但**不被引用就不会显示**（今天就发生了这件事：你以为"精美页面不见了"，其实文件一直都在，只是 App.vue 不再引用它）

**类比**：`App.vue` 是总装车间，`components\` 是零件仓库。零件躺在仓库里不会自己跑到成品上，得总装图（App.vue）点名要它。

### 2. HMR（热更新）

改代码 → **Ctrl+S 保存** → 浏览器自动变，**不用手动刷新**。

- 你今天就体验过：把 `<h1>Get started</h1>` 改成 `<h1>我的记账本</h1>`，保存后标题立刻就变了
- 这是前端开发效率的核心。后端改代码要重启服务，前端不用

### 3. 跨域（CORS）—— 今天撞的那个坑

**先说"源"的定义：协议 + 域名 + 端口，三个里有一个不同，就不算同源。**

- `http://localhost:5173`（前端）
- `http://localhost:8080`（后端）
- **端口不同 → 不同源**

**跨域的真实过程（反直觉，务必记牢）**：

| 步骤 | 发生什么 |
|---|---|
| ① | 浏览器发出请求 |
| ② | **后端正常干活，返回 200 和完整数据** |
| ③ | **浏览器**检查响应头里有没有"通行证"（`Access-Control-Allow-Origin`） |
| ④ | 没有 → **浏览器把已经到手的数据扔掉**，抛 CORS 错误 |

**三条必须纠正的误解**：

| 误解 | 真相 |
|---|---|
| "跨域是后端拒绝了我" | ❌ 后端没拒绝，它正常返回了（并且**不知道自己被拦了**） |
| "跨域要改后端配置" | ❌ **是浏览器的安全规则**，跟后端让不让无关 |
| "跨域是请求发不出去" | ❌ 请求发出去了、响应也回来了，是**浏览器把结果扣下了** |

**今天的证据**：我用 curl（不受浏览器规则约束）请求后端，拿到了 `200` + 完整数据，
但响应头里就是没有通行证 —— 所以浏览器会拦，而 curl 不会。

---

## 五、解决方案：Vite 代理（绕开，而不是硬闯）

### 改了两个地方

**① `frontend/vite.config.js`** —— 加代理：

```js
server: {
  proxy: {
    '/api': {                              // 凡是 /api 开头的请求
      target: 'http://127.0.0.1:8080',     // 都转给后端
      changeOrigin: true,
    },
  },
}
```

**② `frontend/src/App.vue`** —— 请求地址去掉域名和端口：

```js
改前：axios.get('http://localhost:8080/api/account/list')   // 跨源 → 被拦
改后：axios.get('/api/account/list')                        // 同源 → 放行
```

### 为什么这样就解决了

```
浏览器 → Vite 服务器(5173) → 后端(8080)
        ↑ 浏览器眼里只有这一段

对浏览器来说：我从 5173 发请求，还是 5173 回数据 —— 同源，放行
```

**关键**：Vite 服务器是 **Node 程序，不是浏览器**，它**不受浏览器的跨域规则约束**。

**生活比喻**：小区规定"不许跨楼直接交流"→ 你找了个**本楼的中介**（Vite）。
你只跟中介说话，中介跑去隔壁楼把东西取回来。**小区规定管不到中介。**

**一句话记住：代理是"绕开"规则，不是"满足"规则。**

### 我今天取到的有力证据

通过代理请求时，响应头里**依然没有通行证**，但请求**成功了**：

```
HTTP/1.1 200 OK
content-type: application/json
                        ← 没有 Access-Control-Allow-Origin，但成功了！
{"code":0,"msg":"ok","data":[...]}
```

因为浏览器压根没觉得这是跨域请求 —— **规则根本没被触发**。

---

## 六、前端页面是怎么跟后端说话的（呼应 Day1）

```js
// ① 发请求（地址用相对路径，交给代理）
const resp = await axios.get('/api/account/list')

// ② 后端返回的是统一响应体 { code, msg, data } —— 这是 Day1 定义的 Result 类
if (resp.data.code === 0) {
  accounts.value = resp.data.data      // code=0 才取 data
} else {
  errorMsg.value = resp.data.msg       // 否则把 msg 当错误信息
}
```

**看清楚这个衔接**：

- Day1 我们定义 `Result` 类时，你可能觉得"为什么要包一层 code/msg/data"
- 今天答案揭晓了：**前端靠 `code` 判断成功还是失败，靠 `data` 取真实数据，靠 `msg` 显示错误提示**
- **这一层包装就是前后端之间的"接口契约"**

---

## 七、关键词

| 分类 | 词 |
|---|---|
| 前端工程 | Vite、npm、`package.json`、`node_modules`、HMR、组件、根组件 |
| Vue | `ref`、`onMounted`、`<script setup>`、`<template>`、`<style scoped>` |
| 组件库 | Element Plus、`el-table`、`el-card`、`el-alert`、全局注册 `.use()` |
| 请求库 | axios、`axios.get()` |
| 跨域 | CORS、同源、源（协议+域名+端口）、`Access-Control-Allow-Origin`、代理 proxy、`changeOrigin` |

---

## 八、今天遇到的错误与排查

| 现象 | 真实原因 | 怎么排查 |
|---|---|---|
| 红色框 "Network Error"，控制台 `ERR_FAILED` | **跨域**，不是后端坏了 | 看控制台红字：会说清"谁"被"什么规则"拦了 |
| 控制台一堆 `404` | 路径写错 | 拼一遍完整的请求路径 |
| "精美页面不见了" | **`App.vue` 不再引用 `HelloWorld`** | 检查 App.vue 里的 `import` 和 `<template>` |
| 组件长得很丑、没有样式 | 忘了引组件库的 CSS | `main.js` 里 `import 'element-plus/dist/index.css'` 两行缺一不可 |
| 页面字体异常大 / 变黑色 | 全局样式打架（脚手架自带的 `style.css`） | 看 `main.js` 引入了哪些全局样式 |
| 文件找不到（你今天的经历） | **编辑器要先"打开文件夹"**；且项目里有**两个 `src`** | 先确认路径，再用"地址栏粘路径"直达 |

---

## 九、排错套路（今天新增的两招）

1. **报错先分类**：
   - 提示 `CORS` / `ERR_FAILED` → **代理没生效**（改完 `vite.config.js` 要重启 dev）
   - 提示 `404` → 代理通了，**后端路径不对**
   - 提示 `500` → 代理和后端都通了，**后端自己出错**（去看后端日志）

2. **不确定是哪一层的问题，就用 curl 逐层测**（今天就靠这招定位了跨域）：
   ```bash
   curl -i http://127.0.0.1:8080/api/account/list      # 直接测后端
   curl -i http://localhost:5173/api/account/list      # 测经过代理
   ```
   两个都通 → 前端问题；第一个通第二个不通 → 代理问题。

---

## 十、我自己的笔记（这里归你，随便写）

（写下你自己的话 —— 哪个地方卡住了、哪句话你用自己的理解记住了、还想问什么）

-


---

## 十一、明天预告（Day3）

**做「新增账单」—— 第一个写操作。** 会涉及：

- 前端弹窗表单（Element Plus 的 `el-dialog` + `el-form`）
- 表单数据怎么发给后端（POST 请求、JSON 请求体）
- 后端怎么接收（`@PostMapping` + `@RequestBody`）
- **SQL 从 SELECT 变成 INSERT**，要处理自增主键回填
- 写操作要验证：**页面提交 → 数据库真的多了一行**
