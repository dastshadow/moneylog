<script setup>
import { ref, onMounted, nextTick } from 'vue'
// 【Day10 改动】改用统一的 request（自动带 token、统一处理 401）。
//   用 axios 直连的话，这里的每个请求都会因为缺 token 被后端返回 401。
import request from '../api/request'
// ECharts 画图库。注意引入方式：全量引入（简单）vs 按需引入（体积小）
//   全量引入：import * as echarts from 'echarts'  → 打包体积大（几百KB），但省事
//   按需引入：只引需要的图表类型和组件 → 体积小，但代码啰嗦
// 我们现在用全量引入，等以后要优化加载速度时再改成按需（那时你会看到明显差别）
import * as echarts from 'echarts'

// ============================================================
//  【组件拆分：为什么统计功能单独成一个文件？】
// ============================================================
//  App.vue 已经快 600 行了（账户 + 账单两套功能挤在一起）。
//  今天的统计功能再塞进去，就到 800+ 行了 —— 那时候：
//    · 找一个变量要滚半天
//    · 改统计的样式，风险是碰到账户的代码
//    · 多人协作时大家挤在一个文件里改，冲突不断
//
//  所以拆出来：新建 components/StatsPanel.vue，只负责"统计展示"。
//  App.vue 里只需要 import 进来、放在一个标签页里就行。
//
//  【拆分的判断标准】不是"代码行数够了就拆"，而是
//    **"这块东西能不能独立地说清楚它是什么？"**
//    能 → 它就是一个组件（例如"统计面板"）
//    不能 → 说明还没想清楚，先待在原地
//
//  【一个细节：子组件怎么和父组件配合？】
//    这个组件需要知道"有哪些账户"吗？不需要（统计只按分类和月份）。
//    所以它自给自足，不接收父组件传的任何数据 —— 这叫"无 props 组件"。
//    如果以后需要（比如"只统计某个账户"），就加一个 props 接收 accountId。
// ============================================================

// ---------- 月份选择 ----------
function currentMonth() {
  const d = new Date()
  return `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}`
}
const selectedMonth = ref(currentMonth())

// ---------- 数据 ----------
const summary = ref({ totalExpense: 0, totalIncome: 0, balance: 0, billCount: 0 })
const expenseByCategory = ref([])
const incomeByCategory = ref([])
const monthly = ref([])
const loading = ref(false)

// ---------- 加载数据 ----------
async function loadAll() {
  loading.value = true
  try {
    // 四个请求并发发出（不是在 await 里一个个排队）
    //   Promise.all 会等所有请求都完成，然后一起返回
    //   对比写法：一个个 await → 总耗时 = 四个之和
    //             并发           → 总耗时 ≈ 最慢的那个
    //   这种"能并发就并发"的习惯，在页面要拉多个接口时能明显提速
    const [s, e, i, m] = await Promise.all([
      request.get('/api/stats/summary', { params: { month: selectedMonth.value } }),
      request.get('/api/stats/category', { params: { month: selectedMonth.value, type: 1 } }),
      request.get('/api/stats/category', { params: { month: selectedMonth.value, type: 2 } }),
      request.get('/api/stats/monthly'),
    ])

    summary.value = s.data.data || { totalExpense: 0, totalIncome: 0, balance: 0, billCount: 0 }
    expenseByCategory.value = e.data.data || []
    incomeByCategory.value = i.data.data || []
    monthly.value = m.data.data || []

    // 【重点】等 DOM 更新完再画图
    await nextTick()
    renderPie()
    renderBar()
  } catch (err) {
    console.error(err)
  } finally {
    loading.value = false
  }
}

// ---------- 画图 ----------
// 两个图表实例要存在组件外面（用 let 而不是 ref）
//   因为它们是 ECharts 对象（不是响应式数据），不需要 Vue 追踪变化
let pieChart = null
let barChart = null

/**
 * 画饼图：支出分类占比
 */
function renderPie() {
  const el = document.getElementById('pieChart')
  if (!el) return

  // 【为什么每次都要 init 一下判断？】
  //   ECharts 的实例不能重复初始化同一个 DOM，
  //   所以我们存了引用，已经建过就复用（只更新数据、不重建）
  if (!pieChart) {
    pieChart = echarts.init(el)
  }

  // 【数据格式转换】后端给的是 [{category, total, billCount}]，
  //   而 ECharts 的饼图要的是 [{name, value}] 这种固定格式。
  //   这种"后端数据 → 图表要求格式"的转换，是前端画图的日常。
  const data = expenseByCategory.value.map(item => ({
    name: item.category,
    value: Number(item.total),
  }))

  pieChart.setOption({
    title: {
      text: '支出分类占比',
      left: 'center',
      textStyle: { fontSize: 14, fontWeight: 500 },
    },
    tooltip: {
      trigger: 'item',
      // {b} 是名称、{c} 是数值、{d} 是百分比 —— 这是 ECharts 的模板占位符
      formatter: '{b}: ¥{c} ({d}%)',
    },
    legend: { bottom: 0 },
    series: [
      {
        type: 'pie',
        radius: ['40%', '65%'],   // 环形图（内半径、外半径）
        data: data,
        label: { formatter: '{b}\n¥{c}' },
        emphasis: { itemStyle: { shadowBlur: 10, shadowColor: 'rgba(0,0,0,0.2)' } },
      },
    ],
  })

  // 【空数据处理】没有支出时给个提示文案
  //   如果不处理，用户会看到一个空白区域，不知道是"加载中"还是"没数据"
  if (data.length === 0) {
    pieChart.clear()
    pieChart.setOption({
      title: {
        text: '本月暂无支出记录',
        left: 'center',
        top: 'center',
        textStyle: { fontSize: 13, color: '#9ca3af', fontWeight: 400 },
      },
    })
  }
}

/**
 * 画柱状图：月度收支对比
 */
function renderBar() {
  const el = document.getElementById('barChart')
  if (!el) return

  if (!barChart) {
    barChart = echarts.init(el)
  }

  const months = monthly.value.map(m => m.month)
  const expenses = monthly.value.map(m => Number(m.expense))
  const incomes = monthly.value.map(m => Number(m.income))

  barChart.setOption({
    title: {
      text: '月度收支趋势',
      left: 'center',
      textStyle: { fontSize: 14, fontWeight: 500 },
    },
    tooltip: {
      trigger: 'axis',
      formatter: params => {
        // params 是这一列上所有系列的数据（支出和收入两条）
        let s = params[0].axisValue + '<br/>'
        params.forEach(p => { s += `${p.marker} ${p.seriesName}: ¥${p.value}<br/>` })
        return s
      },
    },
    legend: { bottom: 0, data: ['支出', '收入'] },
    grid: { left: '3%', right: '4%', bottom: '15%', top: '18%', containLabel: true },
    xAxis: { type: 'category', data: months },
    yAxis: { type: 'value', name: '金额（元）' },
    series: [
      // 支出用红色（中国习惯：红=出账/涨），收入用绿色
      { name: '支出', type: 'bar', data: expenses, itemStyle: { color: '#d92d20' }, barMaxWidth: 40 },
      { name: '收入', type: 'bar', data: incomes, itemStyle: { color: '#0f6e56' }, barMaxWidth: 40 },
    ],
  })

  if (months.length === 0) {
    barChart.clear()
    barChart.setOption({
      title: {
        text: '暂无账单数据',
        left: 'center',
        top: 'center',
        textStyle: { fontSize: 13, color: '#9ca3af', fontWeight: 400 },
      },
    })
  }
}

// 【窗口大小变化时，图表要跟着重画】
//   ECharts 画出来的图是固定尺寸的 canvas，
//   用户拖动浏览器窗口时它不会自动适应 —— 必须我们手动调用 resize()。
//   这是使用 ECharts 时最容易被忽略、但一定会遇到的问题。
function handleResize() {
  pieChart && pieChart.resize()
  barChart && barChart.resize()
}

onMounted(() => {
  loadAll()
  window.addEventListener('resize', handleResize)
})

// 【组件卸载时要清理】
//   如果不清掉监听，用户来回切换标签页会累积一堆无用的监听器，
//   时间长了会拖慢页面（这叫内存泄漏）。
//   Vue 的 onUnmounted 就是干这个的 —— 有加必有减，成对出现。
import { onUnmounted } from 'vue'
onUnmounted(() => {
  window.removeEventListener('resize', handleResize)
  // 销毁图表实例，释放内存
  pieChart && pieChart.dispose()
  barChart && barChart.dispose()
})
</script>

<template>
  <div class="stats-panel">
    <!-- 月份选择 -->
    <div class="toolbar">
      <span class="label">统计月份</span>
      <el-date-picker
        v-model="selectedMonth"
        type="month"
        value-format="YYYY-MM"
        placeholder="选择月份"
        @change="loadAll"
        style="width: 160px;"
      />
      <el-button :loading="loading" @click="loadAll">刷新</el-button>
    </div>

    <!-- 三个汇总卡片 -->
    <div class="cards" v-loading="loading">
      <div class="stat-card">
        <div class="stat-label">本月支出</div>
        <div class="stat-value expense">¥ {{ Number(summary.totalExpense).toFixed(2) }}</div>
      </div>
      <div class="stat-card">
        <div class="stat-label">本月收入</div>
        <div class="stat-value income">¥ {{ Number(summary.totalIncome).toFixed(2) }}</div>
      </div>
      <div class="stat-card">
        <div class="stat-label">本月结余</div>
        <div class="stat-value" :class="Number(summary.balance) >= 0 ? 'income' : 'expense'">
          ¥ {{ Number(summary.balance).toFixed(2) }}
        </div>
      </div>
      <div class="stat-card">
        <div class="stat-label">账单笔数</div>
        <div class="stat-value plain">{{ summary.billCount || 0 }} 笔</div>
      </div>
    </div>

    <!-- 两个图表 -->
    <div class="charts">
      <el-card shadow="never" class="chart-card">
        <div id="pieChart" class="chart-box"></div>
      </el-card>
      <el-card shadow="never" class="chart-card">
        <div id="barChart" class="chart-box"></div>
      </el-card>
    </div>

    <!-- 分类明细表（图表看不出的具体数字） -->
    <el-card shadow="never" class="table-card" v-loading="loading">
      <template #header>
        <span class="card-title">{{ selectedMonth }} 支出明细</span>
      </template>
      <el-table :data="expenseByCategory" stripe style="width: 100%">
        <el-table-column prop="category" label="分类" min-width="120" />
        <el-table-column prop="billCount" label="笔数" width="90" align="center" />
        <el-table-column label="金额" width="150" align="right">
          <template #default="scope">
            <span class="expense">¥ {{ Number(scope.row.total).toFixed(2) }}</span>
          </template>
        </el-table-column>
      </el-table>
      <el-empty
        v-if="!loading && expenseByCategory.length === 0"
        description="本月暂无支出记录"
      />
    </el-card>
  </div>
</template>

<style scoped>
.stats-panel {
  font-family: system-ui, 'Microsoft YaHei', sans-serif;
}

.toolbar {
  display: flex;
  align-items: center;
  gap: 12px;
  margin-bottom: 16px;
}

.toolbar .label {
  font-size: 14px;
  color: #5f5e5a;
}

.cards {
  display: grid;
  grid-template-columns: repeat(4, 1fr);
  gap: 16px;
  margin-bottom: 16px;
}

.stat-card {
  background: #fff;
  border: 1px solid #e5e7eb;
  border-radius: 10px;
  padding: 18px 20px;
}

.stat-label {
  font-size: 13px;
  color: #6b7280;
  margin-bottom: 8px;
}

.stat-value {
  font-size: 22px;
  font-variant-numeric: tabular-nums;
}

.stat-value.expense {
  color: #d92d20;
}
.stat-value.income {
  color: #0f6e56;
}
.stat-value.plain {
  color: #1f2328;
}

.charts {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 16px;
  margin-bottom: 16px;
}

.chart-card {
  border-radius: 10px;
}

.chart-box {
  width: 100%;
  height: 300px;
}

.table-card {
  border-radius: 10px;
}

.card-title {
  font-size: 15px;
  color: #1f2328;
}

.expense {
  color: #d92d20;
  font-weight: 500;
}
.income {
  color: #0f6e56;
  font-weight: 500;
}
</style>
