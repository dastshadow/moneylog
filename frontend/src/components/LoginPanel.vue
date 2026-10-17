<script setup>
import { ref, reactive } from 'vue'
import { ElMessage } from 'element-plus'
import request, { saveAuth } from '../api/request'

/**
 * 登录 / 注册面板。
 *
 * 【设计思路：为什么"登录"和"注册"放在同一个组件里？】
 *   因为它们长得几乎一样（都是用户名 + 密码），只是注册多一个昵称。
 *   做成两个组件会有 80% 重复代码。
 *   用一个 mode 变量切换即可 —— 这就是 Day2 讲的"数据驱动视图"：
 *   改一个变量的值，界面自己变，不用写两套。
 *
 * 【注册成功后为什么直接登录？】
 *   用户刚注册完，让他再手打一遍密码登录，纯属折磨。
 *   所以注册成功后自动调一次登录接口，把 token 拿到手。
 *   这叫"优化用户路径" —— 少一次操作，体验差很多。
 *
 * 【这个组件怎么把"我登录成功了"告诉父组件？】
 *   用 emit('success', user)。子组件不直接改父组件的数据，
 *   而是"喊一声"，由父组件决定怎么处理。这是 Vue 的标准做法，
 *   好处是子组件不依赖父组件的内部结构，可以随便复用。
 */
const emit = defineEmits(['success'])

// 'login' = 登录模式，'register' = 注册模式
const mode = ref('login')

const loading = ref(false)
const formRef = ref(null)

const form = reactive({
  username: '',
  password: '',
  nickname: '',
})

// 前端校验规则（只是"体验优化"，真正的防线在后端 —— Day4 亲手验证过）
const rules = {
  username: [{ required: true, message: '请输入用户名', trigger: 'blur' }],
  password: [{ required: true, message: '请输入密码', trigger: 'blur' }],
}

async function submit() {
  formRef.value.validate(async (valid) => {
    if (!valid) return

    loading.value = true
    try {
      // ---------- 第一步：如果是注册模式，先注册 ----------
      if (mode.value === 'register') {
        const regResp = await request.post('/api/user/register', {
          username: form.username,
          password: form.password,
          nickname: form.nickname,
        })
        if (regResp.data.code !== 0) {
          // 后端返回的人话提示，原样弹出来（比如"用户名已被占用，请换一个"）
          ElMessage.error(regResp.data.msg)
          return
        }
        ElMessage.success('注册成功，正在为你登录…')
      }

      // ---------- 第二步：登录，拿 token ----------
      const resp = await request.post('/api/user/login', {
        username: form.username,
        password: form.password,
      })
      if (resp.data.code !== 0) {
        ElMessage.error(resp.data.msg)
        return
      }

      const d = resp.data.data
      // 把通行证存进 localStorage，之后 request.js 会自动带上它
      saveAuth(d.token, { userId: d.userId, username: d.username, nickname: d.nickname })

      ElMessage.success('欢迎回来，' + (d.nickname || d.username))
      // 喊一声"登录成功了"，并把这个用户的信息交给父组件
      emit('success', { userId: d.userId, username: d.username, nickname: d.nickname })
    } catch (e) {
      // 注意：401 已经在 request.js 的响应拦截器里统一处理了（清 token + 刷新），
      // 这里只需要兜住其他错误（网络断了、后端 500 等）
      ElMessage.error(e.response?.data?.msg || ('请求失败：' + e.message))
    } finally {
      loading.value = false
    }
  })
}
</script>

<template>
  <el-card class="login-card" shadow="never">
    <h2 class="login-title">MoneyLog</h2>
    <p class="login-sub">个人记账本 · 请先登录</p>

    <!-- 登录 / 注册 切换 -->
    <el-radio-group v-model="mode" class="mode-switch">
      <el-radio-button value="login">登录</el-radio-button>
      <el-radio-button value="register">注册新账号</el-radio-button>
    </el-radio-group>

    <el-form
      ref="formRef"
      :model="form"
      :rules="rules"
      label-width="0"
      @keyup.enter="submit"
    >
      <el-form-item prop="username">
        <el-input
          v-model="form.username"
          placeholder="用户名（3-20 位字母、数字或下划线）"
          size="large"
        />
      </el-form-item>

      <el-form-item prop="password">
        <el-input
          v-model="form.password"
          type="password"
          placeholder="密码（6-30 位）"
          size="large"
          show-password
        />
      </el-form-item>

      <!-- 只有注册模式才需要昵称 -->
      <el-form-item v-if="mode === 'register'" prop="nickname">
        <el-input
          v-model="form.nickname"
          placeholder="昵称（可选，不填就用用户名）"
          size="large"
          maxlength="30"
        />
      </el-form-item>

      <el-button
        type="primary"
        size="large"
        class="submit-btn"
        :loading="loading"
        @click="submit"
      >
        {{ mode === 'login' ? '登 录' : '注册并登录' }}
      </el-button>
    </el-form>

    <!--
      ⚠️ 开发便利提示。
      真实上线前**必须删掉**这两行 —— 页面上写出可用账号，
      等于把钥匙挂在门上。（这就是"开发便利"和"信息安全"的取舍，
      我们选择先便利，但在注释里记下这笔账。）
    -->
    <p class="dev-hint">本机开发环境已有账号：wuweiqiu / zhangsan</p>
  </el-card>
</template>

<style scoped>
.login-card {
  max-width: 420px;
  margin: 40px auto;
  border-radius: 10px;
  padding: 8px 12px 4px;
}

.login-title {
  font-size: 22px;
  margin: 8px 0 2px;
  color: #1f2328;
  text-align: center;
}

.login-sub {
  margin: 0 0 18px;
  text-align: center;
  color: #6b7280;
  font-size: 13px;
}

.mode-switch {
  display: flex;
  justify-content: center;
  margin-bottom: 18px;
}

.submit-btn {
  width: 100%;
}

.dev-hint {
  margin: 14px 0 6px;
  text-align: center;
  font-size: 12px;
  color: #9ca3af;
}
</style>
