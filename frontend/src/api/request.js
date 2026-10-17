import axios from 'axios'
import { ElMessage } from 'element-plus'

/**
 * ============================================================
 *  统一请求模块（Day10 新增）
 * ============================================================
 *
 *  【为什么要单独抽一个文件，而不是到处 import axios？】
 *
 *   Day9 之前，我们在 App.vue、StatsPanel.vue 里各写了 7、4 处 axios 调用。
 *   今天加了登录之后，每个请求都得多带一个 Authorization 头 ——
 *   如果还在各处手写，就要改 11 处，而且**以后新加的请求一定会有人漏写**。
 *   （漏写的那个请求就会莫名 401，还很难查是哪儿漏的。）
 *
 *   所以做法是：**创建一个 axios 实例，给它装上"自动加工"的钩子**
 *   （axios 里叫拦截器 interceptor）。以后所有请求都走这个实例，
 *   带头、处理 401、统一报错，全都在一处搞定。
 *
 *  ============================================================
 *  【axios 拦截器是什么？跟后端拦截器是一对儿】
 *  ============================================================
 *     前端请求拦截器  ──→  HTTP  ──→  后端拦截器（AuthInterceptor）
 *     （出门前自动带证）                （进门时查证）
 *
 *   两个方向各有一个"关卡"，一个负责"带上"，一个负责"检查"。
 *   这是很典型的设计：**把横切关注点（cross-cutting concern）集中处理**，
 *   业务代码里就只剩下业务，干净。
 *
 *   axios 有两个拦截器：
 *    · interceptors.request  —— 请求发出去**之前**加工（我们用它加 token）
 *    · interceptors.response —— 响应回来**之后**加工（我们用它处理 401）
 *
 *  ============================================================
 *  【token 存在哪？为什么用 localStorage 而不是变量？】
 *  ============================================================
 *   如果用普通的 JS 变量，一刷新页面就没了，用户得重新登录。
 *   localStorage 是浏览器的"本地小仓库"，**关掉浏览器再打开还在**，
 *   所以刷新页面不会掉登录状态。
 *
 *   【它的代价（安全上要知道）】
 *    localStorage 里的东西，**页面上任何一个 JS 脚本都能读到**。
 *    万一网站有 XSS 漏洞（被注入恶意脚本），token 就会被偷走。
 *    更安全的做法是用 httpOnly Cookie（JS 读不到），
 *    但那样需要前后端配合处理 CSRF，对新手项目来说复杂度偏高。
 *    我们选 localStorage，是"够用且好懂"，真实项目里两种都在用。
 * ============================================================
 */

const TOKEN_KEY = 'moneylog_token'
const USER_KEY = 'moneylog_user'

/**
 * 用来记"这一轮会话里，我已经因为 401 自动刷过一次页了"。
 *
 * 【为什么用 sessionStorage 而不是 localStorage？】
 *   它只是个"临时计数器"，不需要长期保留。
 *   sessionStorage 在**关掉标签页时就自动清空**，正好符合需求；
 *   而 localStorage 会一直留着，下次打开浏览器还带着旧计数。
 *
 * 【为什么需要这个标记？】
 *   见下面响应拦截器里"防死循环"那一段 —— 这是我们今天踩过的坑。
 */
const RELOAD_FLAG = 'moneylog_401_reloaded'

// ---------- 1. 登录状态的小工具 ----------

export function getToken() {
  return localStorage.getItem(TOKEN_KEY) || ''
}

export function getUser() {
  try {
    return JSON.parse(localStorage.getItem(USER_KEY) || '{}')
  } catch {
    // localStorage 里的内容被手改坏了也不该让页面崩掉
    return {}
  }
}

export function saveAuth(token, user) {
  localStorage.setItem(TOKEN_KEY, token)
  localStorage.setItem(USER_KEY, JSON.stringify(user || {}))
  // 拿到新通行证了 —— 顺便把"已自动刷新过"的计数清掉，
  // 让这一轮会话重新拥有一次"自动救援"的机会
  sessionStorage.removeItem(RELOAD_FLAG)
}

export function clearAuth() {
  localStorage.removeItem(TOKEN_KEY)
  localStorage.removeItem(USER_KEY)
}

// ---------- 2. 创建 axios 实例 ----------

const request = axios.create({
  baseURL: '/',        // 走 Vite 代理（Day2 配的），所以只写相对路径
  timeout: 10000,      // 10 秒没响应就报超时，避免页面一直转圈
})

// ---------- 3. 请求拦截器：出门前自动带上通行证 ----------

request.interceptors.request.use(
  (config) => {
    const token = getToken()
    if (token) {
      // 后端 AuthInterceptor 认的格式就是 "Bearer " + token，一个字都不能差
      config.headers.Authorization = 'Bearer ' + token
    }
    return config
  },
  (error) => Promise.reject(error)
)

// ---------- 4. 响应拦截器：统一处理 401 ----------

// 防止"多个请求同时 401"导致页面反复刷新
let handling401 = false

request.interceptors.response.use(
  // 成功分支：直接透传，让业务代码拿到 resp 自己判断 code
  (response) => {
    // 【健康信号】只要有一个请求成功，说明这一轮会话是通的，
    //   就把"已自动刷新过"的标记清掉，恢复成"还能再救一次"的状态。
    if (sessionStorage.getItem(RELOAD_FLAG)) {
      sessionStorage.removeItem(RELOAD_FLAG)
    }
    return response
  },

  // 失败分支：HTTP 状态码不是 2xx 时进这里
  (error) => {
    const status = error.response?.status

    if (status === 401) {
      // 401 = 后端说"你没登录 / 通行证无效"
      // 注意：这类响应**不会**进上面那个成功分支，
      // 因为 axios 默认把非 2xx 当成错误（这正是状态码的价值所在）。

      // ---------------------------------------------------------
      //  【分支一】手上本来就没有通行证
      // ---------------------------------------------------------
      //  说明是"还没登录，就去调了需要登录的接口"。
      //  这是前端**调用时机**的问题 —— 刷新页面解决不了它，
      //  只会"刷新一次 → 再调一次 → 再 401 → 再刷新"，变成死循环。
      //  所以这里**什么都不做，直接返回**。
      //
      //  真正的解法是让调用方别在未登录时发请求（见 App.vue 里 onMounted 的判断）。
      //  —— 这就是今天那个 bug 的正面写法：
      //     **拦截器不该负责修补调用方的错误，只该处理"凭证失效"这一种情况。**
      if (!getToken()) {
        return Promise.reject(error)
      }

      // ---------------------------------------------------------
      //  【分支二】手上有通行证，但它失效了
      //           （过期 / 被改过 / 服务端换了密钥）
      // ---------------------------------------------------------
      //  这种情况才该"清掉证 + 回登录页"。

      // 同一批并发请求里可能有好几个同时 401，只处理第一个
      if (handling401) {
        return Promise.reject(error)
      }
      handling401 = true

      clearAuth()

      // ---- 防死循环的最后一道保险 ----
      //   正常情况下 clearAuth + reload 之后，页面就停在登录页了（不会再有请求）。
      //   但万一还有别的自动发请求的地方，就可能一直转圈。
      //   所以：**自动刷新最多只做一次**。还不行就提示用户手动处理。
      //
      //   【这是一条通用经验，值得记牢】
      //     "自动重试 / 自动跳转 / 自动刷新"这类逻辑，**必须配次数上限**。
      //     因为一旦它的触发条件没被真正消除，行为就会无限重复。
      //     宁可让用户看到一条提示，也不能让页面无限刷新。
      if (sessionStorage.getItem(RELOAD_FLAG)) {
        ElMessage.error('登录状态异常，请关闭页面重新打开')
        return Promise.reject(error)
      }
      sessionStorage.setItem(RELOAD_FLAG, '1')

      ElMessage.warning(error.response?.data?.msg || '登录已失效，请重新登录')

      // 【为什么直接 reload？】
      //   因为"重新加载页面"能让整个应用回到最干净的状态：
      //   内存里那些列表数据全部清空，App.vue 重新挂载时
      //   发现 localStorage 里没 token，自然就显示登录页了。
      //   比手动去清各种 ref 变量更可靠、更不容易漏。
      setTimeout(() => window.location.reload(), 800)
    }

    // 其余错误原样抛给业务代码去处理（比如显示 msg）
    return Promise.reject(error)
  }
)

export default request
