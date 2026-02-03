<template>
  <div class="statistics-page">
    <a-page-header title="数据分析" subtitle="查看成本、使用量和性能统计">
      <template #extra>
        <a-button type="primary" @click="showBudgetModal = true">
          <template #icon><SettingOutlined /></template>
          预算设置
        </a-button>
      </template>
    </a-page-header>

    <!-- 实时成本监控卡片 -->
    <a-card class="realtime-cost-card" :loading="realtimeLoading">
      <template #title>
        <span><DashboardOutlined /> 实时成本监控</span>
      </template>
      <template #extra>
        <a-button type="link" size="small" @click="loadRealtimeData">
          <template #icon><ReloadOutlined /></template>
          刷新
        </a-button>
      </template>
      <a-row :gutter="24">
        <a-col :xs="24" :sm="12" :md="6">
          <div class="realtime-item">
            <div class="realtime-label">今日消耗</div>
            <div class="realtime-value" :class="getDailyStatusClass()">
              ${{ realtimeCost?.todayCost?.toFixed(4) || '0.0000' }}
            </div>
            <a-progress
              v-if="realtimeCost?.dailyBudget"
              :percent="realtimeCost.dailyUsagePercent || 0"
              :status="getDailyProgressStatus()"
              :show-info="false"
              size="small"
            />
            <div v-if="realtimeCost?.dailyBudget" class="budget-limit-text">
              预算: ${{ realtimeCost.dailyBudget.toFixed(2) }}
            </div>
          </div>
        </a-col>
        <a-col :xs="24" :sm="12" :md="6">
          <div class="realtime-item">
            <div class="realtime-label">本月消耗</div>
            <div class="realtime-value" :class="getMonthlyStatusClass()">
              ${{ realtimeCost?.monthCost?.toFixed(4) || '0.0000' }}
            </div>
            <a-progress
              v-if="realtimeCost?.monthlyBudget"
              :percent="realtimeCost.monthlyUsagePercent || 0"
              :status="getMonthlyProgressStatus()"
              :show-info="false"
              size="small"
            />
            <div v-if="realtimeCost?.monthlyBudget" class="budget-limit-text">
              预算: ${{ realtimeCost.monthlyBudget.toFixed(2) }}
            </div>
          </div>
        </a-col>
        <a-col :xs="24" :sm="12" :md="6">
          <div class="realtime-item">
            <div class="realtime-label">今日Token</div>
            <div class="realtime-value token-value">
              {{ formatNumber(realtimeCost?.todayTokens || 0) }}
            </div>
            <div class="realtime-sub">API调用: {{ realtimeCost?.todayApiCalls || 0 }}次</div>
          </div>
        </a-col>
        <a-col :xs="24" :sm="12" :md="6">
          <div class="realtime-item">
            <div class="realtime-label">平均成本/次</div>
            <div class="realtime-value avg-cost-value">
              ${{ realtimeCost?.avgCostPerCall?.toFixed(6) || '0.000000' }}
            </div>
            <div class="realtime-sub">
              <a-tag :color="getBudgetStatusColor()">{{ realtimeCost?.budgetMessage || '预算充足' }}</a-tag>
            </div>
          </div>
        </a-col>
      </a-row>
    </a-card>

    <!-- 概览卡片 -->
    <a-row :gutter="16" class="overview-cards">
      <a-col :xs="24" :sm="12" :md="6">
        <a-card class="stat-card">
          <a-statistic
            title="总花费"
            :value="costStats?.totalCost || 0"
            :precision="4"
            prefix="$"
            :value-style="{ color: '#1890ff' }"
          />
        </a-card>
      </a-col>
      <a-col :xs="24" :sm="12" :md="6">
        <a-card class="stat-card">
          <a-statistic
            title="今日花费"
            :value="costStats?.todayCost || 0"
            :precision="4"
            prefix="$"
            :value-style="{ color: todayBudgetColor }"
          />
          <div v-if="budgetStatus?.dailyBudget" class="budget-info">
            预算: ${{ budgetStatus.dailyBudget?.toFixed(2) }}
            ({{ budgetStatus.dailyUsagePercent?.toFixed(0) || 0 }}%)
          </div>
        </a-card>
      </a-col>
      <a-col :xs="24" :sm="12" :md="6">
        <a-card class="stat-card">
          <a-statistic
            title="本月花费"
            :value="costStats?.monthCost || 0"
            :precision="4"
            prefix="$"
            :value-style="{ color: monthBudgetColor }"
          />
          <div v-if="budgetStatus?.monthlyBudget" class="budget-info">
            预算: ${{ budgetStatus.monthlyBudget?.toFixed(2) }}
            ({{ budgetStatus.monthlyUsagePercent?.toFixed(0) || 0 }}%)
          </div>
        </a-card>
      </a-col>
      <a-col :xs="24" :sm="12" :md="6">
        <a-card class="stat-card">
          <a-statistic
            title="总API调用"
            :value="usageStats?.totalApiCalls || 0"
            :value-style="{ color: '#52c41a' }"
          />
        </a-card>
      </a-col>
    </a-row>

    <!-- Token 统计 -->
    <a-row :gutter="16" class="overview-cards">
      <a-col :xs="24" :sm="12" :md="6">
        <a-card class="stat-card">
          <a-statistic
            title="总Token消耗"
            :value="Number(usageStats?.totalTokens) || 0"
            :value-style="{ color: '#722ed1' }"
          />
        </a-card>
      </a-col>
      <a-col :xs="24" :sm="12" :md="6">
        <a-card class="stat-card">
          <a-statistic
            title="输入Token"
            :value="Number(usageStats?.totalInputTokens) || 0"
          />
        </a-card>
      </a-col>
      <a-col :xs="24" :sm="12" :md="6">
        <a-card class="stat-card">
          <a-statistic
            title="输出Token"
            :value="Number(usageStats?.totalOutputTokens) || 0"
          />
        </a-card>
      </a-col>
      <a-col :xs="24" :sm="12" :md="6">
        <a-card class="stat-card">
          <a-statistic
            title="平均响应时间"
            :value="performanceStats?.avgResponseTime || 0"
            :precision="0"
            suffix="ms"
            :value-style="{ color: '#fa8c16' }"
          />
        </a-card>
      </a-col>
    </a-row>

    <!-- 图表区域 -->
    <a-row :gutter="16" class="chart-section">
      <!-- 成本趋势图 -->
      <a-col :xs="24" :lg="12">
        <a-card title="成本趋势" :loading="loading">
          <template #extra>
            <a-radio-group v-model:value="trendDays" size="small" @change="loadData">
              <a-radio-button :value="7">7天</a-radio-button>
              <a-radio-button :value="14">14天</a-radio-button>
              <a-radio-button :value="30">30天</a-radio-button>
            </a-radio-group>
          </template>
          <div id="cost-trend-chart" ref="costTrendChart" class="chart-container"></div>
          <a-empty v-if="!loading && (!costStats?.costTrend || costStats.costTrend.length === 0)" description="暂无成本数据" />
        </a-card>
      </a-col>

      <!-- 使用趋势图 -->
      <a-col :xs="24" :lg="12">
        <a-card title="API调用趋势" :loading="loading">
          <div id="usage-trend-chart" ref="usageTrendChart" class="chart-container"></div>
          <a-empty v-if="!loading && (!usageStats?.usageTrend || usageStats.usageTrend.length === 0)" description="暂无使用数据" />
        </a-card>
      </a-col>
    </a-row>

    <a-row :gutter="16" class="chart-section">
      <!-- 模型成本分布 -->
      <a-col :xs="24" :lg="12">
        <a-card title="模型成本分布" :loading="loading">
          <div id="model-cost-chart" ref="modelCostChart" class="chart-container"></div>
          <a-empty v-if="!loading && (!costStats?.costByModel || costStats.costByModel.length === 0)" description="暂无模型成本数据" />
        </a-card>
      </a-col>

      <!-- 模型使用频率 -->
      <a-col :xs="24" :lg="12">
        <a-card title="模型使用频率" :loading="loading">
          <div id="model-usage-chart" ref="modelUsageChart" class="chart-container"></div>
          <a-empty v-if="!loading && (!usageStats?.usageByModel || usageStats.usageByModel.length === 0)" description="暂无模型使用数据" />
        </a-card>
      </a-col>
    </a-row>

    <!-- 模型性能表格 -->
    <a-card title="模型性能统计" :loading="loading" class="performance-table">
      <a-table
        v-if="performanceStats?.performanceByModel && performanceStats.performanceByModel.length > 0"
        :columns="performanceColumns"
        :data-source="performanceStats.performanceByModel"
        :pagination="false"
        row-key="modelName"
        size="middle"
      >
        <template #bodyCell="{ column, record }">
          <template v-if="column.key === 'modelName'">
            <span class="model-name">{{ record.modelName }}</span>
          </template>
          <template v-else-if="column.key === 'avgResponseTime'">
            <span :style="{ color: getResponseTimeColor(record.avgResponseTime) }">
              {{ record.avgResponseTime?.toFixed(0) }} ms
            </span>
          </template>
          <template v-else-if="column.key === 'callCount'">
            {{ formatNumber(record.callCount || 0) }}
          </template>
          <template v-else-if="column.key === 'avgInputTokens'">
            {{ record.avgInputTokens?.toFixed(0) }}
          </template>
          <template v-else-if="column.key === 'avgOutputTokens'">
            {{ record.avgOutputTokens?.toFixed(0) }}
          </template>
        </template>
      </a-table>
      <a-empty v-else-if="!loading" description="暂无性能数据，请先进行一些对话测试" />
    </a-card>

    <!-- 预算设置模态框 -->
    <BudgetSettingModal
      v-model:open="showBudgetModal"
      :initial-data="budgetInitialData"
      @success="handleBudgetSuccess"
    />
  </div>
</template>

<script setup lang="ts">
import { ref, computed, onMounted, onUnmounted, watch, nextTick } from 'vue'
import { message } from 'ant-design-vue'
import { SettingOutlined, DashboardOutlined, ReloadOutlined } from '@ant-design/icons-vue'
import {
  getCostStatistics,
  getUsageStatistics,
  getPerformanceStatistics,
  getRealtimeCost,
} from '@/api/statisticsController'
import { getBudgetStatus } from '@/api/userController'
import BudgetSettingModal from '@/components/BudgetSettingModal.vue'

const loading = ref(false)
const realtimeLoading = ref(false)
const trendDays = ref(7)
const showBudgetModal = ref(false)

const costStats = ref<API.CostStatisticsVO | null>(null)
const usageStats = ref<API.UsageStatisticsVO | null>(null)
const performanceStats = ref<API.PerformanceStatisticsVO | null>(null)
const budgetStatus = ref<API.BudgetStatusVO | null>(null)
const realtimeCost = ref<API.RealtimeCostVO | null>(null)

const budgetInitialData = computed(() => ({
  dailyBudget: budgetStatus.value?.dailyBudget,
  monthlyBudget: budgetStatus.value?.monthlyBudget,
  alertThreshold: 80,
}))

const costTrendChart = ref<HTMLDivElement | null>(null)
const usageTrendChart = ref<HTMLDivElement | null>(null)
const modelCostChart = ref<HTMLDivElement | null>(null)
const modelUsageChart = ref<HTMLDivElement | null>(null)

let costTrendInstance: any = null
let usageTrendInstance: any = null
let modelCostInstance: any = null
let modelUsageInstance: any = null

const todayBudgetColor = computed(() => {
  if (!budgetStatus.value?.dailyBudget) return '#1890ff'
  const percent = budgetStatus.value.dailyUsagePercent || 0
  if (percent >= 100) return '#ff4d4f'
  if (percent >= 80) return '#faad14'
  return '#52c41a'
})

const monthBudgetColor = computed(() => {
  if (!budgetStatus.value?.monthlyBudget) return '#1890ff'
  const percent = budgetStatus.value.monthlyUsagePercent || 0
  if (percent >= 100) return '#ff4d4f'
  if (percent >= 80) return '#faad14'
  return '#52c41a'
})

const performanceColumns = [
  { title: '模型', key: 'modelName', dataIndex: 'modelName' },
  { title: '调用次数', key: 'callCount', dataIndex: 'callCount', sorter: (a: any, b: any) => a.callCount - b.callCount },
  { title: '平均响应时间', key: 'avgResponseTime', dataIndex: 'avgResponseTime', sorter: (a: any, b: any) => a.avgResponseTime - b.avgResponseTime },
  { title: '最快响应', key: 'minResponseTime', dataIndex: 'minResponseTime' },
  { title: '最慢响应', key: 'maxResponseTime', dataIndex: 'maxResponseTime' },
  { title: '平均输入Token', key: 'avgInputTokens', dataIndex: 'avgInputTokens' },
  { title: '平均输出Token', key: 'avgOutputTokens', dataIndex: 'avgOutputTokens' },
]

const formatNumber = (value: number | string | undefined | null) => {
  const num = Number(value) || 0
  if (num >= 1000000) {
    return (num / 1000000).toFixed(2) + 'M'
  }
  if (num >= 1000) {
    return (num / 1000).toFixed(1) + 'K'
  }
  return num.toString()
}

const getResponseTimeColor = (time: number) => {
  if (time < 1000) return '#52c41a'
  if (time < 3000) return '#faad14'
  return '#ff4d4f'
}

const getDailyStatusClass = () => {
  if (!realtimeCost.value?.dailyBudget) return ''
  const percent = realtimeCost.value.dailyUsagePercent || 0
  if (percent >= 100) return 'status-exceeded'
  if (percent >= 80) return 'status-warning'
  return 'status-normal'
}

const getMonthlyStatusClass = () => {
  if (!realtimeCost.value?.monthlyBudget) return ''
  const percent = realtimeCost.value.monthlyUsagePercent || 0
  if (percent >= 100) return 'status-exceeded'
  if (percent >= 80) return 'status-warning'
  return 'status-normal'
}

const getDailyProgressStatus = () => {
  const percent = realtimeCost.value?.dailyUsagePercent || 0
  if (percent >= 100) return 'exception'
  if (percent >= 80) return 'active'
  return 'success'
}

const getMonthlyProgressStatus = () => {
  const percent = realtimeCost.value?.monthlyUsagePercent || 0
  if (percent >= 100) return 'exception'
  if (percent >= 80) return 'active'
  return 'success'
}

const getBudgetStatusColor = () => {
  const status = realtimeCost.value?.budgetStatus
  if (status === 'exceeded') return 'error'
  if (status === 'warning') return 'warning'
  return 'success'
}

const loadRealtimeData = async () => {
  realtimeLoading.value = true
  try {
    const res = await getRealtimeCost()
    if (res.data.code === 0) {
      realtimeCost.value = res.data.data || null
    }
  } catch (error) {
    console.error('加载实时成本数据失败', error)
  } finally {
    realtimeLoading.value = false
  }
}

const handleBudgetSuccess = () => {
  loadData()
  loadRealtimeData()
}

const loadData = async () => {
  loading.value = true
  try {
    const [costRes, usageRes, perfRes, budgetRes] = await Promise.all([
      getCostStatistics({ days: trendDays.value }),
      getUsageStatistics({ days: trendDays.value }),
      getPerformanceStatistics(),
      getBudgetStatus(),
    ])

    if (costRes.data.code === 0) {
      costStats.value = costRes.data.data || null
    }
    if (usageRes.data.code === 0) {
      usageStats.value = usageRes.data.data || null
    }
    if (perfRes.data.code === 0) {
      performanceStats.value = perfRes.data.data || null
    }
    if (budgetRes.data.code === 0) {
      budgetStatus.value = budgetRes.data.data || null
    }

    await nextTick()
    // 延迟一小段时间确保 DOM 完全渲染
    setTimeout(() => {
      renderCharts()
    }, 100)
  } catch (error) {
    message.error('加载数据失败')
    console.error('Load data error:', error)
  } finally {
    loading.value = false
  }
}

const initCharts = () => {
  // 图表初始化已移到 renderCharts 中，此函数保留用于兼容
}

const getEcharts = () => (window as any).echarts

let renderRetryCount = 0
const maxRenderRetry = 30

const renderCharts = () => {
  const echarts = getEcharts()
  if (!echarts) {
    // ECharts 还没加载，延迟重试
    if (renderRetryCount < maxRenderRetry) {
      renderRetryCount++
      setTimeout(renderCharts, 200)
    } else {
      console.error('ECharts not loaded after max retries')
    }
    return
  }
  renderRetryCount = 0 // 重置重试计数
  
  // 成本趋势图
  const costTrendEl = document.getElementById('cost-trend-chart')
  if (costTrendEl) {
    // 总是重新创建实例，确保没有 HMR 残留问题
    if (costTrendInstance) {
      try { costTrendInstance.dispose() } catch (e) { /* ignore */ }
    }
    costTrendInstance = echarts.init(costTrendEl)
    const data = costStats.value?.costTrend || []
    costTrendInstance.setOption({
      tooltip: {
        trigger: 'axis',
        formatter: (params: any) => {
          const p = params[0]
          return `${p.name}<br/>成本: $${Number(p.value)?.toFixed(4) || 0}`
        },
      },
      grid: { left: '10%', right: '5%', bottom: '15%', top: '10%' },
      xAxis: {
        type: 'category',
        data: data.map((d) => d.date?.slice(5) || ''),
        axisLabel: { rotate: 45 },
      },
      yAxis: {
        type: 'value',
        axisLabel: { formatter: (v: number) => '$' + v.toFixed(2) },
      },
      series: [
        {
          type: 'line',
          data: data.map((d) => Number(d.cost) || 0),
          smooth: true,
          areaStyle: { opacity: 0.3 },
          itemStyle: { color: '#1890ff' },
        },
      ],
    })
    costTrendInstance.resize()
  }

  // 使用趋势图
  const usageTrendEl = document.getElementById('usage-trend-chart')
  if (usageTrendEl) {
    if (!usageTrendInstance || !echarts.getInstanceByDom(usageTrendEl)) {
      usageTrendInstance = echarts.init(usageTrendEl)
    }
    const data = usageStats.value?.usageTrend || []
    usageTrendInstance.setOption({
      tooltip: { trigger: 'axis' },
      legend: { data: ['API调用', 'Token消耗'], bottom: 0 },
      grid: { left: '10%', right: '10%', bottom: '20%', top: '10%' },
      xAxis: {
        type: 'category',
        data: data.map((d) => d.date?.slice(5) || ''),
        axisLabel: { rotate: 45 },
      },
      yAxis: [
        { type: 'value', name: 'API调用', position: 'left' },
        { type: 'value', name: 'Token', position: 'right' },
      ],
      series: [
        {
          name: 'API调用',
          type: 'bar',
          data: data.map((d) => Number(d.apiCalls) || 0),
          itemStyle: { color: '#52c41a' },
        },
        {
          name: 'Token消耗',
          type: 'line',
          yAxisIndex: 1,
          data: data.map((d) => Number(d.tokens) || 0),
          smooth: true,
          itemStyle: { color: '#722ed1' },
        },
      ],
    })
    usageTrendInstance.resize()
  }

  // 模型成本分布
  const modelCostEl = document.getElementById('model-cost-chart')
  if (modelCostEl) {
    if (!modelCostInstance || !echarts.getInstanceByDom(modelCostEl)) {
      modelCostInstance = echarts.init(modelCostEl)
    }
    const costByModelData = costStats.value?.costByModel || []
    if (costByModelData.length > 0) {
      const data = costByModelData.slice(0, 10)
      modelCostInstance.setOption({
        tooltip: {
          trigger: 'item',
          formatter: (params: any) => `${params.name}<br/>$${Number(params.value)?.toFixed(4)} (${params.percent}%)`,
        },
        series: [
          {
            type: 'pie',
            radius: ['40%', '70%'],
            data: data.map((d) => ({
              name: d.modelName?.split('/').pop() || d.modelName,
              value: Number(d.cost) || 0,
            })),
            emphasis: {
              itemStyle: {
                shadowBlur: 10,
                shadowOffsetX: 0,
                shadowColor: 'rgba(0, 0, 0, 0.5)',
              },
            },
            label: {
              formatter: '{b}: {d}%',
            },
          },
        ],
      })
      modelCostInstance.resize()
    }
  }

  // 模型使用频率
  const modelUsageEl = document.getElementById('model-usage-chart')
  if (modelUsageEl) {
    if (!modelUsageInstance || !echarts.getInstanceByDom(modelUsageEl)) {
      modelUsageInstance = echarts.init(modelUsageEl)
    }
    const usageByModelData = usageStats.value?.usageByModel || []
    if (usageByModelData.length > 0) {
      const data = usageByModelData.slice(0, 10)
      modelUsageInstance.setOption({
        tooltip: { trigger: 'axis', axisPointer: { type: 'shadow' } },
        grid: { left: '30%', right: '10%', bottom: '5%', top: '5%' },
        xAxis: { type: 'value' },
        yAxis: {
          type: 'category',
          data: data.map((d) => d.modelName?.split('/').pop() || d.modelName).reverse(),
          axisLabel: {
            width: 100,
            overflow: 'truncate',
          },
        },
        series: [
          {
            type: 'bar',
            data: data.map((d) => Number(d.callCount) || 0).reverse(),
            itemStyle: {
              color: new ((window as any).echarts.graphic.LinearGradient)(0, 0, 1, 0, [
                { offset: 0, color: '#1890ff' },
                { offset: 1, color: '#52c41a' },
              ]),
            },
          },
        ],
      })
      modelUsageInstance.resize()
    }
  }

  // 确保图表正确显示
  handleResize()
}

const handleResize = () => {
  costTrendInstance?.resize()
  usageTrendInstance?.resize()
  modelCostInstance?.resize()
  modelUsageInstance?.resize()
}

onMounted(() => {
  loadData()
  loadRealtimeData()
  window.addEventListener('resize', handleResize)
})

onUnmounted(() => {
  window.removeEventListener('resize', handleResize)
  costTrendInstance?.dispose()
  usageTrendInstance?.dispose()
  modelCostInstance?.dispose()
  modelUsageInstance?.dispose()
})

// 监听数据变化，当数据加载完成后渲染图表
watch(
  [costStats, usageStats],
  () => {
    if (costStats.value || usageStats.value) {
      nextTick(() => {
        setTimeout(() => {
          renderCharts()
        }, 100)
      })
    }
  },
  { deep: true }
)
</script>

<style scoped>
.statistics-page {
  padding: 24px;
  max-width: 1600px;
  margin: 0 auto;
}

.overview-cards {
  margin-bottom: 16px;
}

.stat-card {
  height: 100%;
}

.stat-card :deep(.ant-card-body) {
  padding: 20px;
}

.budget-info {
  font-size: 12px;
  color: #999;
  margin-top: 8px;
}

.realtime-cost-card {
  margin-bottom: 16px;
}

.realtime-item {
  text-align: center;
  padding: 12px;
}

.realtime-label {
  font-size: 14px;
  color: #666;
  margin-bottom: 8px;
}

.realtime-value {
  font-size: 24px;
  font-weight: 600;
  color: #1890ff;
  margin-bottom: 8px;
}

.realtime-value.token-value {
  color: #722ed1;
}

.realtime-value.avg-cost-value {
  color: #13c2c2;
}

.realtime-value.status-normal {
  color: #52c41a;
}

.realtime-value.status-warning {
  color: #faad14;
}

.realtime-value.status-exceeded {
  color: #ff4d4f;
}

.realtime-sub {
  font-size: 12px;
  color: #999;
  margin-top: 4px;
}

.budget-limit-text {
  font-size: 12px;
  color: #999;
  margin-top: 4px;
}

.chart-section {
  margin-bottom: 16px;
}

.chart-container {
  width: 100%;
  height: 300px;
}

.performance-table {
  margin-top: 16px;
}

.model-name {
  font-family: monospace;
  font-size: 12px;
}

@media (max-width: 768px) {
  .statistics-page {
    padding: 12px;
  }

  .chart-container {
    height: 250px;
  }
}
</style>
