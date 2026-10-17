<script setup>
import { ref, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
// 【Day10 改动】不再直接 import axios，改用我们自己的 request。
//   它已经装好了两个钩子：① 自动带 token ② 收到 401 统一处理。
//   用法跟 axios 一模一样（request.get / request.post …），所以下面的调用几乎不用改。
import request, { getToken, getUser, clearAuth } from './api/request'
// 【Day8】引入统计组件
//   注意：统计的所有逻辑（数据加载、画图）都在那个文件里，
//   这里只负责"把它放在一个标签页里"。这就是组件拆分的意义 ——
//   App.vue 不用变长，只是多了一行 import 和一段标签。
import StatsPanel from './components/StatsPanel.vue'
// 【Day10】登录面板
import LoginPanel from './components/LoginPanel.vue'

// ============================================================
//  【Day10 新增】登录状态
// ============================================================
//  判断依据：**localStorage 里有没有 token**。
//    有 = 已登录，没有 = 未登录。
//
//  【为什么不用一个普通的 ref(false) 就好？】
//    因为 ref 的值存在 JS 内存里，**刷新页面就没了**，
//    用户一按 F5 就得重新登录，体验很差。
//    而 localStorage 里的 token 会一直留着，以它为准，刷新也不掉登录。
//
//  【这一步其实就是最简版的"路由守卫"】
//    真实项目用 vue-router 的全局前置守卫来做，原理完全相同：
//    "没有登录凭证，就不让看主界面"。
const loggedIn = ref(!!getToken())
const currentUser = ref(getUser())

// 子组件（LoginPanel）登录成功后通过 emit('success') 通知这里
function handleLoginSuccess(user) {
  loggedIn.value = true
  currentUser.value = user
  // 登录成功后立刻拉数据 —— 这样主界面一出现，数据就已经在眼前了
  loadAccounts()
  loadBills()
}

function handleLogout() {
  ElMessageBox.confirm('确定要退出登录吗？', '退出确认', {
    confirmButtonText: '退出',
    cancelButtonText: '取消',
    type: 'warning',
  })
    .then(() => {
      // ① 清掉 localStorage 里的通行证 —— 这一步是关键，
      //    清了之后 request.js 就不会再自动带 token 了
      clearAuth()
      // ② 把界面切回登录页
      loggedIn.value = false
      currentUser.value = {}
      // ③ 顺手清掉内存里的数据。
      //    【为什么这步不能省？】不然换个人登录后，
      //    在新数据加载出来之前，屏幕上会短暂闪过**上一个人的**账单。
      accounts.value = []
      bills.value = []
      ElMessage.success('已退出登录')
    })
    .catch(() => {
      // 用户点了"取消"会走到这里 —— 什么都不用做，忽略即可。
      // （ElMessageBox 点取消时是 reject，不 catch 会在控制台飘一条红字）
    })
}

// 页面顶部的标签页：'account' = 账户，'bill' = 账单
// 【这是今天新增的唯一一个新东西】
//   用了 Element Plus 的 el-tabs，本质就是"用一个变量决定显示哪一块内容"，
//   跟 Day2 讲的"数据驱动视图"是同一个原理 —— 改一下变量的值，页面自己切。
//   【顺便说一句】等这个文件长到五六百行时，就该把它拆成两个组件文件了。
//   这是真实开发里一定会遇到的事："一个文件太长怎么办" —— 我们下次专门学。
const activeTab = ref('account')

// ============================================================
//  账户部分（跟 Day5 完全一样，只是搬进了标签页）
// ============================================================
const accounts = ref([])
const loading = ref(false)
const errorMsg = ref('')

async function loadAccounts() {
  loading.value = true
  errorMsg.value = ''
  try {
    const resp = await request.get('/api/account/list')
    if (resp.data.code === 0) {
      accounts.value = resp.data.data
    } else {
      errorMsg.value = resp.data.msg
    }
  } catch (e) {
    errorMsg.value = e.message
  } finally {
    loading.value = false
  }
}

const dialogVisible = ref(false)
const submitting = ref(false)
const formRef = ref(null)
const editingId = ref(null)

const form = ref({ name: '', balance: 0 })

const rules = {
  name: [{ required: true, message: '请输入账户名称', trigger: 'blur' }],
}

function openCreateDialog() {
  editingId.value = null
  form.value = { name: '', balance: 0 }
  dialogVisible.value = true
}

function openEditDialog(row) {
  editingId.value = row.id
  form.value = { name: row.name, balance: Number(row.balance) }
  dialogVisible.value = true
}

function submitForm() {
  formRef.value.validate(async (valid) => {
    if (!valid) return
    submitting.value = true
    try {
      let resp
      if (editingId.value === null) {
        resp = await request.post('/api/account/create', form.value)
      } else {
        resp = await request.put('/api/account/' + editingId.value, form.value)
      }
      if (resp.data.code === 0) {
        ElMessage.success(editingId.value === null ? '新增成功' : '修改成功')
        dialogVisible.value = false
        await loadAccounts()
      } else {
        ElMessage.error(resp.data.msg)
      }
    } catch (e) {
      ElMessage.error(e.response?.data?.msg || ('提交失败：' + e.message))
    } finally {
      submitting.value = false
    }
  })
}

async function handleDelete(row) {
  try {
    await ElMessageBox.confirm(
      `确定要删除账户「${row.name}」吗？删除后无法恢复。`,
      '删除确认',
      { confirmButtonText: '确定删除', cancelButtonText: '取消', type: 'warning' }
    )
  } catch {
    return
  }
  try {
    const resp = await request.delete('/api/account/' + row.id)
    if (resp.data.code === 0) {
      ElMessage.success('删除成功')
      await loadAccounts()
    } else {
      ElMessage.error(resp.data.msg)
    }
  } catch (e) {
    ElMessage.error(e.response?.data?.msg || ('删除失败：' + e.message))
  }
}

// ============================================================
//  账单部分 —— 今天新增
// ============================================================
const bills = ref([])
const billLoading = ref(false)

async function loadBills() {
  billLoading.value = true
  try {
    const resp = await request.get('/api/bill/list')
    if (resp.data.code === 0) {
      bills.value = resp.data.data
    } else {
      ElMessage.error(resp.data.msg)
    }
  } catch (e) {
    ElMessage.error(e.response?.data?.msg || ('加载账单失败：' + e.message))
  } finally {
    billLoading.value = false
  }
}

const billDialogVisible = ref(false)
const billSubmitting = ref(false)
const billFormRef = ref(null)

// 取"今天"的日期，格式必须是 2026-10-10 这种（后端只认这个格式）
// 【为什么要自己拼？】因为 JS 的 Date 对象没有现成的"yyyy-MM-dd"输出方法，
//   toISOString() 会带上时区，可能出现"日期差一天"的诡异问题。
//   自己按年月日拼，最稳妥。
function today() {
  const d = new Date()
  const m = String(d.getMonth() + 1).padStart(2, '0')
  const day = String(d.getDate()).padStart(2, '0')
  return `${d.getFullYear()}-${m}-${day}`
}

const billForm = ref({
  accountId: null,
  type: 1,              // 默认支出（记账大部分是花钱）
  amount: 0,
  category: '',
  billDate: today(),
  remark: '',
})

// 账单表单的校验规则
const billRules = {
  accountId: [{ required: true, message: '请选择所属账户', trigger: 'change' }],
  amount: [{ required: true, message: '请输入金额', trigger: 'blur' }],
  category: [{ required: true, message: '请选择分类', trigger: 'change' }],
  billDate: [{ required: true, message: '请选择日期', trigger: 'change' }],
}

// 分类的可选值（先写死几个常见的，以后再做成可配置的）
const categories = ['餐饮', '交通', '购物', '居住', '娱乐', '医疗', '学习', '工资', '奖金', '其他']

function openBillDialog() {
  billForm.value = {
    accountId: accounts.value.length > 0 ? accounts.value[0].id : null,
    type: 1,
    amount: 0,
    category: '',
    billDate: today(),
    remark: '',
  }
  billDialogVisible.value = true
}

function submitBill() {
  billFormRef.value.validate(async (valid) => {
    if (!valid) return
    billSubmitting.value = true
    try {
      const resp = await request.post('/api/bill/create', billForm.value)
      if (resp.data.code === 0) {
        ElMessage.success('记账成功')
        billDialogVisible.value = false
        // 【Day7 改动】两处都要刷新
        //   loadBills()    —— 账单列表多了一条
        //   loadAccounts() —— 账户余额也变了！（这是今天新增的联动效果）
        // 【如果不刷新会怎样？】账户标签页还显示旧余额，用户切过去看到的是过期数据，
        //   会以为"钱没扣"或者"扣错了"。这类"界面没同步"的 bug 很常见，
        //   根源就是"改了数据却漏了刷新对应的界面"。
        await loadBills()
        await loadAccounts()
      } else {
        // 后端的业务错误，比如"账户余额不足，当前余额 ¥965.00，本次需要 ¥99999"
        // 会原样弹给用户 —— 这种提示比"操作失败"有用得多
        ElMessage.error(resp.data.msg)
      }
    } catch (e) {
      ElMessage.error(e.response?.data?.msg || ('记账失败：' + e.message))
    } finally {
      billSubmitting.value = false
    }
  })
}

// 显示辅助：把 type 数字变成文字和颜色
//   1 = 支出 → 红色（钱出去了），2 = 收入 → 绿色（钱进来了）
//   如果这个配色你不喜欢，改这两行就行 —— 前端的好处就是改样式不用动数据
function typeText(type) {
  return type === 2 ? '收入' : '支出'
}
function typeTagType(type) {
  return type === 2 ? 'success' : 'danger'
}
// 金额显示成 -35.00 / +8000.00，一眼能看出方向
function amountText(row) {
  const v = Number(row.amount).toFixed(2)
  return (row.type === 2 ? '+' : '-') + '¥' + v
}

onMounted(async () => {
  // ============================================================
  //  【Day10 修复】只"已登录"时才去拉数据 —— 这一行不加会出人命
  // ============================================================
  //  现象：页面疯狂刷新，用户连输入的 chance 都没有。
  //
  //  为什么？漏了这一个判断之后，链条是这样的：
  //      页面加载（还没有 token）
  //        → 渲染出登录页 ✓
  //        → 但 onMounted 照样发 loadAccounts / loadBills
  //        → 后端返回 401（拦截器上线了，没证不让进）
  //        → 前端响应拦截器 clearAuth + location.reload()
  //        → 页面重载 → 又发请求 → 又 401 → 又刷新 …… 死循环
  //
  //  【三条可复用的经验】
  //    ① 加了"全局自动跳转"之后，一定要回头检查**所有自动发请求的地方**
  //       （onMounted、watch、定时器…），它们都可能触发那个自动跳转。
  //    ② "自动重试 / 自动跳转"这类逻辑，**必须配一个次数上限**，
  //       否则一旦它的触发条件没被消除，就是死循环。见 request.js 里的处理。
  //    ③ 判断要写在**发请求之前**，而不是等收到 401 再补救 ——
  //       从源头不发，比发出去再拦更简单可靠。
  if (!loggedIn.value) return

  await loadAccounts()   // 账户列表（账单表单里选账户也要用）
  await loadBills()
})
</script>

<template>
  <!-- ==================== 未登录：只有登录面板 ====================
       这一行 v-if 就是"门禁"：没有 token 的话，下面的主界面
       **根本不会被渲染**（不是藏起来，是真的没生成）。 -->
  <LoginPanel v-if="!loggedIn" @success="handleLoginSuccess" />

  <!-- ==================== 已登录：主界面 ==================== -->
  <div v-else class="page">
    <header class="page-head">
      <div class="head-left">
        <h1>MoneyLog</h1>
        <p>个人记账本 · 记下每一笔，看清钱去哪了</p>
      </div>
      <div class="head-right">
        <span class="who">你好，{{ currentUser.nickname || currentUser.username }}</span>
        <el-button size="small" @click="handleLogout">退出登录</el-button>
      </div>
    </header>

    <el-alert
      v-if="errorMsg"
      :title="'请求失败：' + errorMsg"
      type="error"
      show-icon
      :closable="false"
      style="margin-bottom: 16px;"
    />

    <el-tabs v-model="activeTab" class="main-tabs">

      <!-- ==================== 标签页1：账户 ==================== -->
      <el-tab-pane label="账户" name="account">
        <el-card shadow="never" class="table-card">
          <template #header>
            <div class="card-head">
              <span class="card-title">账户列表</span>
              <div>
                <el-button type="primary" @click="openCreateDialog">新增账户</el-button>
                <el-button :loading="loading" @click="loadAccounts">刷新</el-button>
              </div>
            </div>
          </template>

          <el-table :data="accounts" v-loading="loading" stripe style="width: 100%">
            <el-table-column prop="id" label="ID" width="70" />
            <el-table-column prop="name" label="账户名称" min-width="130" />
            <el-table-column label="余额（元）" width="140" align="right">
              <template #default="scope">
                <span class="money">¥ {{ Number(scope.row.balance).toFixed(2) }}</span>
              </template>
            </el-table-column>
            <el-table-column label="创建时间" width="170">
              <template #default="scope">
                {{ scope.row.createdAt ? scope.row.createdAt.replace('T', ' ') : '-' }}
              </template>
            </el-table-column>
            <el-table-column label="操作" width="140" align="center">
              <template #default="scope">
                <el-button size="small" @click="openEditDialog(scope.row)">编辑</el-button>
                <el-button size="small" type="danger" @click="handleDelete(scope.row)">删除</el-button>
              </template>
            </el-table-column>
          </el-table>

          <el-empty
            v-if="!loading && accounts.length === 0 && !errorMsg"
            description="暂无账户数据"
          />
        </el-card>
      </el-tab-pane>

      <!-- ==================== 标签页2：账单 ==================== -->
      <el-tab-pane label="账单" name="bill">
        <el-card shadow="never" class="table-card">
          <template #header>
            <div class="card-head">
              <span class="card-title">账单明细</span>
              <div>
                <el-button type="primary" @click="openBillDialog">记一笔</el-button>
                <el-button :loading="billLoading" @click="loadBills">刷新</el-button>
              </div>
            </div>
          </template>

          <el-table :data="bills" v-loading="billLoading" stripe style="width: 100%">
            <el-table-column prop="id" label="ID" width="60" />
            <el-table-column label="日期" width="115">
              <template #default="scope">
                {{ scope.row.billDate }}
              </template>
            </el-table-column>
            <el-table-column label="类型" width="80" align="center">
              <template #default="scope">
                <el-tag :type="typeTagType(scope.row.type)" size="small" effect="light">
                  {{ typeText(scope.row.type) }}
                </el-tag>
              </template>
            </el-table-column>
            <el-table-column prop="category" label="分类" width="90" />

            <!-- 金额：带正负号，支出红、收入绿 -->
            <el-table-column label="金额" width="130" align="right">
              <template #default="scope">
                <span :class="scope.row.type === 2 ? 'income' : 'expense'">
                  {{ amountText(scope.row) }}
                </span>
              </template>
            </el-table-column>

            <!-- 账户名：这就是后端连表查询带回来的 accountName -->
            <el-table-column prop="accountName" label="账户" width="110" />
            <el-table-column prop="remark" label="备注" min-width="120">
              <template #default="scope">
                <span style="color:#9ca3af">{{ scope.row.remark || '-' }}</span>
              </template>
            </el-table-column>
          </el-table>

          <el-empty
            v-if="!billLoading && bills.length === 0"
            description="还没有账单，点「记一笔」开始记账"
          />
        </el-card>
      </el-tab-pane>

      <!-- ==================== 标签页3：统计 ====================
           整块内容都在 StatsPanel.vue 里，
           这里就一行 —— 组件拆分的直接好处 -->
      <el-tab-pane label="统计" name="stats">
        <StatsPanel />
      </el-tab-pane>

    </el-tabs>

    <!-- ==================== 新增/编辑账户 弹窗 ==================== -->
    <el-dialog
      v-model="dialogVisible"
      :title="editingId === null ? '新增账户' : '编辑账户'"
      width="440px"
      :close-on-click-modal="false"
    >
      <el-form ref="formRef" :model="form" :rules="rules" label-width="90px">
        <el-form-item label="账户名称" prop="name">
          <el-input v-model="form.name" placeholder="例如：招商银行、现金" maxlength="50" />
        </el-form-item>
        <el-form-item label="余额" prop="balance">
          <el-input-number v-model="form.balance" :precision="2" :min="0" :step="100" style="width: 100%;" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="submitting" @click="submitForm">确定</el-button>
      </template>
    </el-dialog>

    <!-- ==================== 记一笔 弹窗 ==================== -->
    <el-dialog
      v-model="billDialogVisible"
      title="记一笔"
      width="480px"
      :close-on-click-modal="false"
    >
      <el-form ref="billFormRef" :model="billForm" :rules="billRules" label-width="90px">

        <!-- 支出/收入 二选一：用单选框比下拉框更直观，因为只有两个选项 -->
        <el-form-item label="类型" prop="type">
          <el-radio-group v-model="billForm.type">
            <el-radio :value="1">支出</el-radio>
            <el-radio :value="2">收入</el-radio>
          </el-radio-group>
        </el-form-item>

        <el-form-item label="金额" prop="amount">
          <el-input-number
            v-model="billForm.amount"
            :precision="2"
            :min="0.01"
            :step="10"
            style="width: 100%;"
          />
        </el-form-item>

        <el-form-item label="分类" prop="category">
          <el-select v-model="billForm.category" placeholder="请选择分类" style="width: 100%;">
            <el-option v-for="c in categories" :key="c" :label="c" :value="c" />
          </el-select>
        </el-form-item>

        <!--
          日期选择器。有个关键设置必须注意：
              value-format="YYYY-MM-DD"
          它会决定"这个选择器交出来的字符串长什么样"。
          必须是大写 YYYY-MM-DD（前面用短横线），
          因为后端只认这种格式 —— 这就是前后端之间的"数据格式约定"。
          【如果写成 value-format="YYYY/MM/DD"】后端会返回
            "请求数据格式不正确"（今天刚修好的那个提示），
            你就知道该回来检查这一行了。
        -->
        <el-form-item label="日期" prop="billDate">
          <el-date-picker
            v-model="billForm.billDate"
            type="date"
            value-format="YYYY-MM-DD"
            placeholder="选择日期"
            style="width: 100%;"
          />
        </el-form-item>

        <el-form-item label="所属账户" prop="accountId">
          <el-select v-model="billForm.accountId" placeholder="请选择账户" style="width: 100%;">
            <el-option
              v-for="a in accounts"
              :key="a.id"
              :label="a.name + '（余额 ¥' + Number(a.balance).toFixed(2) + '）'"
              :value="a.id"
            />
          </el-select>
        </el-form-item>

        <el-form-item label="备注" prop="remark">
          <el-input
            v-model="billForm.remark"
            type="textarea"
            :rows="2"
            maxlength="200"
            show-word-limit
            placeholder="可选，例如：和朋友吃饭"
          />
        </el-form-item>
      </el-form>

      <template #footer>
        <el-button @click="billDialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="billSubmitting" @click="submitBill">保存</el-button>
      </template>
    </el-dialog>

    <p class="foot-note">
      数据来自 MySQL 的 account 和 bill 两张表 —— 账单列表里的「账户」列是连表查询带回来的
    </p>
  </div>
</template>

<style>
/* 全局基础样式（没有 scoped，所以作用于整个页面） */
body {
  margin: 0;
  background: #f5f6f8;
}
</style>

<style scoped>
.page {
  max-width: 1000px;
  margin: 0 auto;
  padding: 40px 24px;
  font-family: system-ui, 'Microsoft YaHei', sans-serif;
}

/* 头部：左边标题，右边"你好，xxx + 退出登录"（Day10 改的） */
.page-head {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 16px;
}

.head-left h1 {
  font-size: 24px;
  margin: 0 0 4px;
  color: #1f2328;
}

.head-left p {
  margin: 0 0 20px;
  color: #6b7280;
  font-size: 14px;
}

.head-right {
  display: flex;
  align-items: center;
  gap: 10px;
  padding-top: 4px;
  white-space: nowrap;
}

.who {
  font-size: 14px;
  color: #4b5563;
}

.main-tabs {
  margin-top: 8px;
}

.table-card {
  border-radius: 10px;
}

.card-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
}

.card-title {
  font-size: 15px;
  color: #1f2328;
}

/* 账户余额：中国习惯，红色表示"正" */
.money {
  color: #d92d20;
  font-weight: 500;
}

/* 账单金额：支出红、收入绿 */
.expense {
  color: #d92d20;
  font-weight: 500;
}
.income {
  color: #0f6e56;
  font-weight: 500;
}

.foot-note {
  margin-top: 16px;
  font-size: 13px;
  color: #9ca3af;
}
</style>
