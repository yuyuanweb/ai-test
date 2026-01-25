<template>
  <div class="task-report-page">
    <a-card title="测试报告" :bordered="false">
      <template #extra>
        <a-button type="primary" @click="handleExportPDF" :loading="exportingPDF">
          <template #icon>
            <DownloadOutlined />
          </template>
          导出PDF
        </a-button>
      </template>

      <a-descriptions :column="2" bordered style="margin-bottom: 24px">
        <a-descriptions-item label="任务ID">{{ report?.taskId || task?.id }}</a-descriptions-item>
        <a-descriptions-item label="任务名称">{{ report?.taskName || task?.name || '未命名' }}</a-descriptions-item>
        <a-descriptions-item label="状态">
          <a-tag :color="getStatusColor(task?.status || '')">
            {{ getStatusText(task?.status || '') }}
          </a-tag>
        </a-descriptions-item>
        <a-descriptions-item label="完成时间">{{ task?.completedAt || '-' }}</a-descriptions-item>
      </a-descriptions>

      <!-- 统计摘要 -->
      <a-row :gutter="16" style="margin-bottom: 24px">
        <a-col :span="8">
          <a-statistic 
            title="总成本" 
            :value="report?.summary?.totalCost || 0" 
            prefix="$" 
            :precision="6" 
          />
        </a-col>
        <a-col :span="8">
          <a-statistic 
            title="平均响应时间" 
            :value="report?.summary?.avgResponseTimeMs || 0" 
            suffix="ms" 
            :precision="2"
          />
        </a-col>
        <a-col :span="8">
          <a-statistic 
            title="总Token消耗" 
            :value="report?.summary?.totalTokens || 0" 
          />
        </a-col>
      </a-row>

      <!-- 雷达图 -->
      <a-card title="多维度能力对比（雷达图）" :bordered="false" style="margin-bottom: 24px">
        <div ref="radarChartRef" style="width: 100%; height: 400px"></div>
      </a-card>

      <!-- 柱状图 -->
      <a-card title="性能指标对比（柱状图）" :bordered="false" style="margin-bottom: 24px">
        <a-radio-group v-model:value="barChartType" style="margin-bottom: 16px">
          <a-radio-button value="responseTime">响应时间</a-radio-button>
          <a-radio-button value="tokens">Token消耗</a-radio-button>
          <a-radio-button value="cost">成本</a-radio-button>
        </a-radio-group>
        <div ref="barChartRef" style="width: 100%; height: 400px"></div>
      </a-card>

      <!-- 模型对比表格 -->
      <a-card title="模型统计对比" :bordered="false" style="margin-bottom: 24px">
        <a-table
          :columns="summaryColumns"
          :data-source="report?.modelStatistics || []"
          :pagination="false"
          row-key="modelName"
          :loading="loadingReport"
        >
          <template #bodyCell="{ column, record }">
            <template v-if="column.key === 'modelName'">
              <div class="model-cell">
                <img
                  :src="getProviderIcon(record.modelName)"
                  :alt="getModelName(record.modelName)"
                  class="model-icon"
                  @error="handleImageError"
                />
                <span class="model-name">{{ getModelName(record.modelName) }}</span>
              </div>
            </template>
            <template v-else-if="column.key === 'avgResponseTimeMs'">
              {{ record.avgResponseTimeMs?.toFixed(2) || '-' }}ms
            </template>
            <template v-else-if="column.key === 'totalCost'">
              ${{ record.totalCost?.toFixed(6) || '0.000000' }}
            </template>
            <template v-else-if="column.key === 'avgUserRating'">
              <a-rate :value="record.avgUserRating" disabled :count="5" />
            </template>
          </template>
        </a-table>
      </a-card>

      <!-- 详细结果 -->
      <a-card title="详细测试结果" :bordered="false">
        <a-table
          :columns="resultColumns"
          :data-source="report?.testResults || []"
          :loading="loadingReport"
          row-key="id"
          :pagination="{ pageSize: 20 }"
          :scroll="{ x: 'max-content' }"
        >
            <template #bodyCell="{ column, record }">
              <template v-if="column.key === 'modelName'">
                <div class="model-cell">
                  <img
                    :src="getProviderIcon(record.modelName)"
                    :alt="getModelName(record.modelName)"
                    class="model-icon"
                    @error="handleImageError"
                  />
                  <span class="model-name">{{ getModelName(record.modelName) }}</span>
                </div>
              </template>
              <template v-else-if="column.key === 'outputText'">
                <div class="output-cell" @click="showFullContent(record)">
                  <a-typography-paragraph
                    :ellipsis="{ rows: 2, expandable: false }"
                    class="output-preview"
                  >
                    {{ record.outputText }}
                  </a-typography-paragraph>
                  <a-button type="link" size="small" class="view-full-btn">
                    查看全部
                  </a-button>
                </div>
              </template>
              <template v-else-if="column.key === 'metrics'">
                <div class="metrics-cell">
                  <div>响应时间: {{ record.responseTimeMs }}ms</div>
                  <div>Token: {{ record.inputTokens }}/{{ record.outputTokens }}</div>
                  <div>成本: ${{ record.cost?.toFixed(6) || '0.000000' }}</div>
                </div>
              </template>
              <template v-else-if="column.key === 'userRating'">
                <a-rate
                  :value="record.userRating"
                  :count="5"
                  @change="(value) => handleRatingChange(record.id, value)"
                  allow-clear
                />
            </template>
          </template>
        </a-table>
      </a-card>

      <div style="margin-top: 16px">
        <a-button @click="handleBack">返回</a-button>
      </div>
    </a-card>

    <!-- 完整内容模态框 -->
    <a-modal
      v-model:open="modalVisible"
      :title="selectedRecord ? getModelName(selectedRecord.modelName) : ''"
      width="80%"
      :footer="null"
      @cancel="closeModal"
    >
      <div v-if="selectedRecord" class="modal-content">
        <div class="modal-header">
            <img
              :src="getProviderIcon(selectedRecord.modelName)"
              :alt="getModelName(selectedRecord.modelName)"
              class="modal-icon"
              @error="handleImageError"
            />
          <div class="modal-info">
            <h3>{{ getModelName(selectedRecord.modelName) }}</h3>
            <div class="modal-metrics">
              <span>响应时间: {{ selectedRecord.responseTimeMs }}ms</span>
              <span>Token: {{ selectedRecord.inputTokens }}/{{ selectedRecord.outputTokens }}</span>
              <span>成本: ${{ selectedRecord.cost?.toFixed(6) || '0.000000' }}</span>
            </div>
          </div>
        </div>
        <div class="modal-body">
          <!-- 输入提示词 -->
          <div v-if="selectedRecord.inputPrompt" class="input-section">
            <h4>输入提示词</h4>
            <div class="input-content">
              <MarkdownRenderer :content="selectedRecord.inputPrompt" />
            </div>
          </div>
          <!-- 思考过程（如果有） -->
          <div v-if="selectedRecord.reasoning" class="thinking-section">
            <h4>思考过程</h4>
            <div class="thinking-content">
              <MarkdownRenderer :content="selectedRecord.reasoning" />
            </div>
          </div>
          <!-- 输出内容 -->
          <div class="output-section">
            <h4>输出内容</h4>
            <div class="output-content">
              <MarkdownRenderer :content="selectedRecord.outputText || ''" />
            </div>
          </div>
        </div>
      </div>
    </a-modal>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, onMounted, watch, nextTick } from 'vue'
import { useRouter, useRoute } from 'vue-router'
import { message } from 'ant-design-vue'
import { DownloadOutlined } from '@ant-design/icons-vue'
import * as echarts from 'echarts'
import jsPDF from 'jspdf'
import html2canvas from 'html2canvas'
import {
  getTask,
  updateTestResultRating,
  type TestTask,
  type TestResult
} from '@/api/batchTestController'
import { generateReport, type ReportVO, type TestResultVO } from '@/api/reportController'
import MarkdownRenderer from '@/components/MarkdownRenderer.vue'
import { marked } from 'marked'

marked.setOptions({
  breaks: true,
  gfm: true
})

const router = useRouter()
const route = useRoute()

const task = ref<TestTask | null>(null)
const report = ref<ReportVO | null>(null)
const loadingReport = ref(false)
const modalVisible = ref(false)
const selectedRecord = ref<TestResultVO | null>(null)
const exportingPDF = ref(false)
const barChartType = ref('responseTime')

const radarChartRef = ref<HTMLDivElement | null>(null)
const barChartRef = ref<HTMLDivElement | null>(null)
let radarChartInstance: echarts.ECharts | null = null
let barChartInstance: echarts.ECharts | null = null

const summaryColumns = [
  {
    title: '模型',
    dataIndex: 'modelName',
    key: 'modelName',
    width: 200
  },
  {
    title: '测试次数',
    dataIndex: 'testCount',
    key: 'testCount'
  },
  {
    title: '平均响应时间',
    key: 'avgResponseTimeMs'
  },
  {
    title: '总Token',
    dataIndex: 'totalTokens',
    key: 'totalTokens'
  },
  {
    title: '总成本',
    key: 'totalCost'
  },
  {
    title: '平均用户评分',
    key: 'avgUserRating'
  }
]

const resultColumns = [
  {
    title: '模型',
    key: 'modelName',
    width: 200,
    fixed: 'left'
  },
  {
    title: '输出内容',
    key: 'outputText',
    ellipsis: true,
    minWidth: 300,
    maxWidth: 600
  },
  {
    title: '性能指标',
    key: 'metrics',
    width: 220
  },
  {
    title: '用户评分',
    key: 'userRating',
    width: 150
  },
  {
    title: '创建时间',
    dataIndex: 'createTime',
    key: 'createTime',
    width: 180
  }
]


const loadTask = async () => {
  const taskId = route.params.id as string
  if (!taskId) {
    message.error('任务ID不存在')
    return
  }

  try {
    const res = await getTask(taskId)
    if (res.data?.code === 0 && res.data.data) {
      task.value = res.data.data
    } else {
      message.error(res.data?.message || '加载任务失败')
    }
  } catch (error: any) {
    console.error('加载任务失败:', error)
    message.error(error.message || '加载任务失败')
  }
}

const loadReport = async () => {
  const taskId = route.params.id as string
  if (!taskId) {
    message.error('任务ID不存在')
    return
  }

  try {
    loadingReport.value = true
    const res = await generateReport(taskId)
    if (res.data?.code === 0 && res.data.data) {
      report.value = res.data.data
      await nextTick()
      initRadarChart()
      initBarChart()
    } else {
      message.error(res.data?.message || '加载报告失败')
    }
  } catch (error: any) {
    console.error('加载报告失败:', error)
    message.error(error.message || '加载报告失败')
  } finally {
    loadingReport.value = false
  }
}

const initRadarChart = () => {
  if (!radarChartRef.value || !report.value?.radarChart) return

  if (radarChartInstance) {
    radarChartInstance.dispose()
  }

  radarChartInstance = echarts.init(radarChartRef.value)
  
  const colors = ['#5470c6', '#91cc75', '#fac858', '#ee6666', '#73c0de', '#3ba272', '#fc8452', '#9a60b4']
  
  const option = {
    title: {
      text: '多维度能力对比',
      left: 'center'
    },
    tooltip: {
      trigger: 'item'
    },
    legend: {
      data: report.value.radarChart.series.map(s => s.modelName),
      bottom: 10
    },
    radar: {
      indicator: report.value.radarChart.dimensions.map(dim => ({
        name: dim,
        max: 100
      })),
      center: ['50%', '55%'],
      radius: '65%'
    },
    series: [{
      type: 'radar',
      data: report.value.radarChart.series.map((series, index) => ({
        value: series.values,
        name: series.modelName,
        itemStyle: {
          color: colors[index % colors.length]
        },
        areaStyle: {
          opacity: 0.3
        },
        lineStyle: {
          width: 2
        }
      }))
    }]
  }

  radarChartInstance.setOption(option)

  window.addEventListener('resize', () => {
    radarChartInstance?.resize()
  })
}

const initBarChart = () => {
  if (!barChartRef.value || !report.value?.barChart) return

  if (barChartInstance) {
    barChartInstance.dispose()
  }

  barChartInstance = echarts.init(barChartRef.value)
  updateBarChart()

  window.addEventListener('resize', () => {
    barChartInstance?.resize()
  })
}

const updateBarChart = () => {
  if (!barChartInstance || !report.value?.barChart) return

  let selectedSeries: any = null
  let yAxisName = ''

  switch (barChartType.value) {
    case 'responseTime':
      selectedSeries = report.value.barChart.series.find(s => s.name === '平均响应时间')
      yAxisName = '响应时间 (ms)'
      break
    case 'tokens':
      selectedSeries = report.value.barChart.series.find(s => s.name === '总Token消耗')
      yAxisName = 'Token数量'
      break
    case 'cost':
      selectedSeries = report.value.barChart.series.find(s => s.name === '总成本')
      yAxisName = '成本 (USD)'
      break
  }

  if (!selectedSeries) return

  const option = {
    title: {
      text: selectedSeries.name,
      left: 'center'
    },
    tooltip: {
      trigger: 'axis',
      axisPointer: {
        type: 'shadow'
      },
      formatter: (params: any) => {
        const param = params[0]
        return `${param.name}<br/>${param.seriesName}: ${param.value}${selectedSeries.unit || ''}`
      }
    },
    grid: {
      left: '3%',
      right: '4%',
      bottom: '3%',
      containLabel: true
    },
    xAxis: {
      type: 'category',
      data: report.value.barChart.categories,
      axisLabel: {
        rotate: 45,
        interval: 0
      }
    },
    yAxis: {
      type: 'value',
      name: yAxisName
    },
    series: [{
      name: selectedSeries.name,
      type: 'bar',
      data: selectedSeries.data,
      itemStyle: {
        color: new echarts.graphic.LinearGradient(0, 0, 0, 1, [
          { offset: 0, color: '#83bff6' },
          { offset: 0.5, color: '#188df0' },
          { offset: 1, color: '#188df0' }
        ])
      },
      emphasis: {
        itemStyle: {
          color: new echarts.graphic.LinearGradient(0, 0, 0, 1, [
            { offset: 0, color: '#2378f7' },
            { offset: 0.7, color: '#2378f7' },
            { offset: 1, color: '#83bff6' }
          ])
        }
      },
      label: {
        show: true,
        position: 'top',
        formatter: (params: any) => {
          return `${params.value}${selectedSeries.unit || ''}`
        }
      }
    }]
  }

  barChartInstance.setOption(option)
}

watch(barChartType, () => {
  updateBarChart()
})

const getStatusColor = (status: string) => {
  const colorMap: Record<string, string> = {
    pending: 'default',
    running: 'processing',
    completed: 'success',
    failed: 'error',
    cancelled: 'warning'
  }
  return colorMap[status] || 'default'
}

const getStatusText = (status: string) => {
  const textMap: Record<string, string> = {
    pending: '等待中',
    running: '运行中',
    completed: '已完成',
    failed: '失败',
    cancelled: '已取消'
  }
  return textMap[status] || status
}

const handleBack = () => {
  router.push('/batch-test/list')
}

const handleRatingChange = async (resultId: string, rating: number) => {
  try {
    const res = await updateTestResultRating({
      resultId,
      userRating: rating || undefined
    })
    if (res.data?.code === 0) {
      message.success('评分已更新')
      const result = report.value?.testResults.find(r => r.id === resultId)
      if (result) {
        result.userRating = rating || undefined
      }
      await loadReport()
    } else {
      message.error(res.data?.message || '更新评分失败')
    }
  } catch (error: any) {
    console.error('更新评分失败:', error)
    message.error(error.message || '更新评分失败')
  }
}

const showFullContent = (record: TestResultVO) => {
  selectedRecord.value = record
  modalVisible.value = true
}

const handleExportPDF = async () => {
  if (!report.value) {
    message.warning('报告数据未加载完成')
    return
  }

  try {
    exportingPDF.value = true
    const hideMessage = message.loading('正在生成PDF，请稍候...', 0)

    const doc = new jsPDF('p', 'mm', 'a4')
    const pageWidth = doc.internal.pageSize.getWidth()
    const pageHeight = doc.internal.pageSize.getHeight()
    let yPos = 20

    const addImageFromCanvas = async (canvas: HTMLCanvasElement, y: number, maxHeight: number = 0) => {
      const imgData = canvas.toDataURL('image/png')
      const imgWidth = pageWidth - 40
      const imgHeight = (canvas.height * imgWidth) / canvas.width
      
      if (maxHeight > 0 && imgHeight > maxHeight) {
        const scale = maxHeight / imgHeight
        const scaledWidth = imgWidth * scale
        const scaledHeight = imgHeight * scale
        doc.addImage(imgData, 'PNG', 20, y, scaledWidth, scaledHeight)
        return scaledHeight + 10
      } else {
        if (y + imgHeight > pageHeight - 20) {
          doc.addPage()
          y = 20
        }
        doc.addImage(imgData, 'PNG', 20, y, imgWidth, imgHeight)
        return imgHeight + 10
      }
    }

    const createTextCanvas = (text: string, fontSize: number, width: number) => {
      const div = document.createElement('div')
      div.style.position = 'absolute'
      div.style.left = '-9999px'
      div.style.top = '-9999px'
      div.style.width = `${width}mm`
      div.style.fontSize = `${fontSize}px`
      div.style.fontFamily = 'Arial, "Microsoft YaHei", "SimSun", sans-serif'
      div.style.color = '#000000'
      div.style.lineHeight = '1.5'
      div.style.whiteSpace = 'pre-wrap'
      div.style.wordBreak = 'break-word'
      div.textContent = text
      document.body.appendChild(div)
      
      return html2canvas(div, {
        backgroundColor: '#ffffff',
        scale: 2,
        width: width * 3.779527559,
        height: div.offsetHeight
      }).then(canvas => {
        document.body.removeChild(div)
        return canvas
      })
    }

    const createMetricsCanvas = async (result: TestResultVO, width: number) => {
      const div = document.createElement('div')
      div.style.position = 'absolute'
      div.style.left = '-9999px'
      div.style.top = '-9999px'
      div.style.width = `${width}mm`
      div.style.padding = '8px 12px'
      div.style.backgroundColor = '#f5f7fa'
      div.style.borderRadius = '6px'
      div.style.border = '1px solid #e4e7ed'
      div.style.boxSizing = 'border-box'
      
      const metricsHtml = `
        <div style="display: flex; gap: 16px; flex-wrap: wrap; align-items: center;">
          <div style="display: flex; align-items: center; gap: 6px;">
            <span style="color: #909399; font-size: 9px;">⏱️</span>
            <span style="color: #606266; font-size: 9px;">
              <strong style="color: #303133;">响应时间:</strong> 
              <span style="color: #409eff; font-weight: 600;">${result.responseTimeMs || '-'}ms</span>
            </span>
          </div>
          <div style="display: flex; align-items: center; gap: 6px;">
            <span style="color: #909399; font-size: 9px;">🔢</span>
            <span style="color: #606266; font-size: 9px;">
              <strong style="color: #303133;">Token:</strong> 
              <span style="color: #67c23a; font-weight: 600;">${result.inputTokens || 0}</span>
              <span style="color: #909399;">/</span>
              <span style="color: #e6a23c; font-weight: 600;">${result.outputTokens || 0}</span>
            </span>
          </div>
          <div style="display: flex; align-items: center; gap: 6px;">
            <span style="color: #909399; font-size: 9px;">💰</span>
            <span style="color: #606266; font-size: 9px;">
              <strong style="color: #303133;">成本:</strong> 
              <span style="color: #f56c6c; font-weight: 600;">$${result.cost?.toFixed(6) || '0.000000'}</span>
            </span>
          </div>
        </div>
      `
      div.innerHTML = metricsHtml
      document.body.appendChild(div)
      
      await nextTick()
      
      const canvas = await html2canvas(div, {
        backgroundColor: '#f5f7fa',
        scale: 2,
        width: width * 3.779527559,
        useCORS: true,
        logging: false,
        allowTaint: true
      })
      
      document.body.removeChild(div)
      return canvas
    }

    const createSectionCanvas = async (title: string, content: string, width: number, isInput: boolean = false) => {
      const div = document.createElement('div')
      div.style.position = 'absolute'
      div.style.left = '-9999px'
      div.style.top = '-9999px'
      div.style.width = `${width}mm`
      div.style.boxSizing = 'border-box'
      
      const borderColor = isInput ? '#52c41a' : '#409eff'
      const bgColor = isInput ? '#f0f9ff' : '#f5f7fa'
      
      div.style.padding = '12px'
      div.style.backgroundColor = bgColor
      div.style.borderRadius = '8px'
      div.style.border = `2px solid ${borderColor}`
      div.style.borderLeftWidth = '4px'
      div.style.boxShadow = '0 2px 4px rgba(0, 0, 0, 0.1)'
      
      const titleDiv = document.createElement('div')
      titleDiv.style.marginBottom = '10px'
      titleDiv.style.paddingBottom = '8px'
      titleDiv.style.borderBottom = `1px solid ${borderColor}40`
      titleDiv.innerHTML = `
        <h4 style="margin: 0; padding: 0; font-size: 11px; font-weight: 600; color: ${borderColor};">
          ${isInput ? '【输入提示词】' : '【输出内容】'} ${title}
        </h4>
      `
      
      const contentDiv = document.createElement('div')
      contentDiv.style.fontSize = '9px'
      contentDiv.style.lineHeight = '1.6'
      contentDiv.style.color = '#303133'
      contentDiv.style.wordBreak = 'break-word'
      contentDiv.className = 'markdown-body'
      
      try {
        const html = marked(content) as string
        contentDiv.innerHTML = html
      } catch (error) {
        console.error('Markdown转换失败:', error)
        contentDiv.textContent = content
      }
      
      div.appendChild(titleDiv)
      div.appendChild(contentDiv)
      
      const style = document.createElement('style')
      style.textContent = `
        .markdown-body {
          margin: 0;
        }
        .markdown-body p {
          margin: 4px 0;
        }
        .markdown-body p:first-child {
          margin-top: 0;
        }
        .markdown-body p:last-child {
          margin-bottom: 0;
        }
        .markdown-body code {
          background-color: rgba(0, 0, 0, 0.08);
          padding: 2px 4px;
          border-radius: 3px;
          font-family: 'Courier New', monospace;
          font-size: 8px;
          color: #e83e8c;
        }
        .markdown-body pre {
          background-color: rgba(0, 0, 0, 0.05);
          padding: 8px;
          border-radius: 4px;
          overflow-x: auto;
          margin: 6px 0;
          border: 1px solid rgba(0, 0, 0, 0.1);
        }
        .markdown-body pre code {
          background-color: transparent;
          padding: 0;
          color: #333;
        }
        .markdown-body strong {
          font-weight: 600;
          color: #1f2937;
        }
        .markdown-body em {
          font-style: italic;
        }
        .markdown-body ul, .markdown-body ol {
          margin: 4px 0;
          padding-left: 20px;
        }
        .markdown-body li {
          margin: 2px 0;
        }
        .markdown-body blockquote {
          margin: 4px 0;
          padding-left: 10px;
          border-left: 3px solid ${borderColor};
          color: #666;
        }
      `
      div.appendChild(style)
      
      document.body.appendChild(div)
      
      await nextTick()
      
      const canvas = await html2canvas(div, {
        backgroundColor: bgColor,
        scale: 2,
        width: width * 3.779527559,
        useCORS: true,
        logging: false,
        allowTaint: true,
        windowWidth: width * 3.779527559,
        windowHeight: div.scrollHeight
      })
      
      document.body.removeChild(div)
      return canvas
    }

    const createMarkdownCanvas = async (markdown: string, fontSize: number, width: number) => {
      const div = document.createElement('div')
      div.style.position = 'absolute'
      div.style.left = '-9999px'
      div.style.top = '-9999px'
      div.style.width = `${width}mm`
      div.style.fontSize = `${fontSize}px`
      div.style.fontFamily = 'Arial, "Microsoft YaHei", "SimSun", sans-serif'
      div.style.color = '#000000'
      div.style.lineHeight = '1.6'
      div.style.wordBreak = 'break-word'
      div.style.padding = '5px'
      div.style.backgroundColor = '#ffffff'
      div.style.boxSizing = 'border-box'
      
      div.className = 'markdown-body'
      
      try {
        const html = marked(markdown) as string
        div.innerHTML = html
        
        const style = document.createElement('style')
        style.textContent = `
          .markdown-body {
            margin: 0;
            padding: 5px;
          }
          .markdown-body h1, .markdown-body h2, .markdown-body h3, .markdown-body h4, .markdown-body h5, .markdown-body h6 {
            margin-top: 10px;
            margin-bottom: 6px;
            font-weight: 600;
            color: #000000;
          }
          .markdown-body h1:first-child, .markdown-body h2:first-child, .markdown-body h3:first-child {
            margin-top: 0;
          }
          .markdown-body p {
            margin: 6px 0;
          }
          .markdown-body p:first-child {
            margin-top: 0;
          }
          .markdown-body p:last-child {
            margin-bottom: 0;
          }
          .markdown-body ul, .markdown-body ol {
            margin: 6px 0;
            padding-left: 20px;
          }
          .markdown-body code {
            background-color: #f5f5f5;
            padding: 2px 4px;
            border-radius: 3px;
            font-family: monospace;
            font-size: ${fontSize * 0.9}px;
          }
          .markdown-body pre {
            background-color: #f5f5f5;
            padding: 6px;
            border-radius: 4px;
            overflow-x: auto;
            margin: 6px 0;
          }
          .markdown-body pre code {
            background-color: transparent;
            padding: 0;
          }
          .markdown-body blockquote {
            margin: 6px 0;
            padding-left: 10px;
            border-left: 3px solid #ddd;
            color: #666;
          }
          .markdown-body strong {
            font-weight: 600;
          }
          .markdown-body em {
            font-style: italic;
          }
          .markdown-body table {
            border-collapse: collapse;
            width: 100%;
            margin: 6px 0;
          }
          .markdown-body table th, .markdown-body table td {
            border: 1px solid #ddd;
            padding: 4px;
          }
        `
        div.appendChild(style)
      } catch (error) {
        console.error('Markdown转换失败:', error)
        div.textContent = markdown
      }
      
      document.body.appendChild(div)
      
      await nextTick()
      
      const canvas = await html2canvas(div, {
        backgroundColor: '#ffffff',
        scale: 2,
        width: width * 3.779527559,
        useCORS: true,
        logging: false,
        allowTaint: true,
        windowWidth: width * 3.779527559,
        windowHeight: div.scrollHeight
      })
      
      document.body.removeChild(div)
      return canvas
    }

    const titleCanvas = await createTextCanvas('AI大模型评测报告', 18, pageWidth - 40)
    const titleImg = titleCanvas.toDataURL('image/png')
    const titleHeight = (titleCanvas.height * (pageWidth - 40)) / titleCanvas.width
    const titleY = yPos
    doc.addImage(titleImg, 'PNG', 20, titleY, pageWidth - 40, titleHeight)
    yPos += titleHeight + 10

    doc.setFontSize(12)
    doc.setFont('helvetica', 'normal')
    const taskNameText = `任务名称: ${report.value.taskName || '未命名'}`
    const taskNameCanvas = await createTextCanvas(taskNameText, 12, pageWidth - 40)
    const taskNameImg = taskNameCanvas.toDataURL('image/png')
    const taskNameHeight = (taskNameCanvas.height * (pageWidth - 40)) / taskNameCanvas.width
    doc.addImage(taskNameImg, 'PNG', 20, yPos, pageWidth - 40, taskNameHeight)
    yPos += taskNameHeight + 5
    
    const taskIdText = `任务ID: ${report.value.taskId}`
    const taskIdCanvas = await createTextCanvas(taskIdText, 12, pageWidth - 40)
    const taskIdImg = taskIdCanvas.toDataURL('image/png')
    const taskIdHeight = (taskIdCanvas.height * (pageWidth - 40)) / taskIdCanvas.width
    doc.addImage(taskIdImg, 'PNG', 20, yPos, pageWidth - 40, taskIdHeight)
    yPos += taskIdHeight + 5
    
    const timeText = `生成时间: ${new Date().toLocaleString('zh-CN')}`
    const timeCanvas = await createTextCanvas(timeText, 12, pageWidth - 40)
    const timeImg = timeCanvas.toDataURL('image/png')
    const timeHeight = (timeCanvas.height * (pageWidth - 40)) / timeCanvas.width
    doc.addImage(timeImg, 'PNG', 20, yPos, pageWidth - 40, timeHeight)
    yPos += timeHeight + 10

    if (yPos > pageHeight - 30) {
      doc.addPage()
      yPos = 20
    }

    const summaryTitleCanvas = await createTextCanvas('报告摘要', 14, pageWidth - 40)
    const summaryTitleImg = summaryTitleCanvas.toDataURL('image/png')
    const summaryTitleHeight = (summaryTitleCanvas.height * (pageWidth - 40)) / summaryTitleCanvas.width
    doc.addImage(summaryTitleImg, 'PNG', 20, yPos, pageWidth - 40, summaryTitleHeight)
    yPos += summaryTitleHeight + 8
    
    doc.setFontSize(11)
    doc.setFont('helvetica', 'normal')
    const summary = report.value.summary
    
    const costText = `总成本: $${summary.totalCost?.toFixed(6) || '0.000000'}`
    const costCanvas = await createTextCanvas(costText, 11, pageWidth - 40)
    const costImg = costCanvas.toDataURL('image/png')
    const costHeight = (costCanvas.height * (pageWidth - 40)) / costCanvas.width
    doc.addImage(costImg, 'PNG', 20, yPos, pageWidth - 40, costHeight)
    yPos += costHeight + 5
    
    const responseTimeText = `平均响应时间: ${summary.avgResponseTimeMs?.toFixed(2) || '0'}ms`
    const responseTimeCanvas = await createTextCanvas(responseTimeText, 11, pageWidth - 40)
    const responseTimeImg = responseTimeCanvas.toDataURL('image/png')
    const responseTimeHeight = (responseTimeCanvas.height * (pageWidth - 40)) / responseTimeCanvas.width
    doc.addImage(responseTimeImg, 'PNG', 20, yPos, pageWidth - 40, responseTimeHeight)
    yPos += responseTimeHeight + 5
    
    const tokensText = `总Token消耗: ${summary.totalTokens || 0}`
    const tokensCanvas = await createTextCanvas(tokensText, 11, pageWidth - 40)
    const tokensImg = tokensCanvas.toDataURL('image/png')
    const tokensHeight = (tokensCanvas.height * (pageWidth - 40)) / tokensCanvas.width
    doc.addImage(tokensImg, 'PNG', 20, yPos, pageWidth - 40, tokensHeight)
    yPos += tokensHeight + 5
    
    const resultsText = `测试结果总数: ${summary.totalResults || 0}`
    const resultsCanvas = await createTextCanvas(resultsText, 11, pageWidth - 40)
    const resultsImg = resultsCanvas.toDataURL('image/png')
    const resultsHeight = (resultsCanvas.height * (pageWidth - 40)) / resultsCanvas.width
    doc.addImage(resultsImg, 'PNG', 20, yPos, pageWidth - 40, resultsHeight)
    yPos += resultsHeight + 5
    
    const modelCountText = `参与模型数: ${summary.modelCount || 0}`
    const modelCountCanvas = await createTextCanvas(modelCountText, 11, pageWidth - 40)
    const modelCountImg = modelCountCanvas.toDataURL('image/png')
    const modelCountHeight = (modelCountCanvas.height * (pageWidth - 40)) / modelCountCanvas.width
    doc.addImage(modelCountImg, 'PNG', 20, yPos, pageWidth - 40, modelCountHeight)
    yPos += modelCountHeight + 10

    if (radarChartRef.value && radarChartInstance) {
      if (yPos > pageHeight - 80) {
        doc.addPage()
        yPos = 20
      }
      const radarTitleCanvas = await createTextCanvas('多维度能力对比（雷达图）', 14, pageWidth - 40)
      const radarTitleImg = radarTitleCanvas.toDataURL('image/png')
      const radarTitleHeight = (radarTitleCanvas.height * (pageWidth - 40)) / radarTitleCanvas.width
      doc.addImage(radarTitleImg, 'PNG', 20, yPos, pageWidth - 40, radarTitleHeight)
      yPos += radarTitleHeight + 10
      
      const radarCanvas = await html2canvas(radarChartRef.value, {
        backgroundColor: '#ffffff',
        scale: 2
      })
      yPos += await addImageFromCanvas(radarCanvas, yPos, pageHeight - yPos - 20)
    }

    if (barChartRef.value && barChartInstance) {
      if (yPos > pageHeight - 80) {
        doc.addPage()
        yPos = 20
      }
      const barTitleCanvas = await createTextCanvas('性能指标对比（柱状图）', 14, pageWidth - 40)
      const barTitleImg = barTitleCanvas.toDataURL('image/png')
      const barTitleHeight = (barTitleCanvas.height * (pageWidth - 40)) / barTitleCanvas.width
      doc.addImage(barTitleImg, 'PNG', 20, yPos, pageWidth - 40, barTitleHeight)
      yPos += barTitleHeight + 10
      
      const barCanvas = await html2canvas(barChartRef.value, {
        backgroundColor: '#ffffff',
        scale: 2
      })
      yPos += await addImageFromCanvas(barCanvas, yPos, pageHeight - yPos - 20)
    }

    if (yPos > pageHeight - 30) {
      doc.addPage()
      yPos = 20
    }

    const modelStatsTitleCanvas = await createTextCanvas('模型统计对比', 14, pageWidth - 40)
    const modelStatsTitleImg = modelStatsTitleCanvas.toDataURL('image/png')
    const modelStatsTitleHeight = (modelStatsTitleCanvas.height * (pageWidth - 40)) / modelStatsTitleCanvas.width
    doc.addImage(modelStatsTitleImg, 'PNG', 20, yPos, pageWidth - 40, modelStatsTitleHeight)
    yPos += modelStatsTitleHeight + 8

    doc.setFontSize(10)
    doc.setFont('helvetica', 'normal')
    const tableHeaders = ['模型', '测试次数', '平均响应时间', '总Token', '总成本']
    const colWidths = [50, 25, 35, 30, 35]
    let xPos = 20

    for (const header of tableHeaders) {
      const headerCanvas = await createTextCanvas(header, 10, colWidths[tableHeaders.indexOf(header)])
      const headerImg = headerCanvas.toDataURL('image/png')
      const headerHeight = (headerCanvas.height * colWidths[tableHeaders.indexOf(header)]) / headerCanvas.width
      doc.addImage(headerImg, 'PNG', xPos, yPos, colWidths[tableHeaders.indexOf(header)], headerHeight)
      xPos += colWidths[tableHeaders.indexOf(header)]
    }
    yPos += 10

    for (const stat of report.value.modelStatistics) {
      if (yPos > pageHeight - 30) {
        doc.addPage()
        yPos = 20
      }
      xPos = 20
      
      const modelName = getModelName(stat.modelName)
      const modelNameCanvas = await createTextCanvas(modelName.length > 20 ? modelName.substring(0, 20) + '...' : modelName, 9, colWidths[0])
      const modelNameImg = modelNameCanvas.toDataURL('image/png')
      const modelNameHeight = (modelNameCanvas.height * colWidths[0]) / modelNameCanvas.width
      doc.addImage(modelNameImg, 'PNG', xPos, yPos, colWidths[0], modelNameHeight)
      xPos += colWidths[0]
      
      doc.text(String(stat.testCount), xPos, yPos + 3)
      xPos += colWidths[1]
      doc.text(`${stat.avgResponseTimeMs?.toFixed(2) || '-'}ms`, xPos, yPos + 3)
      xPos += colWidths[2]
      doc.text(String(stat.totalTokens || 0), xPos, yPos + 3)
      xPos += colWidths[3]
      doc.text(`$${stat.totalCost?.toFixed(6) || '0.000000'}`, xPos, yPos + 3)
      yPos += 8
    }

    yPos += 10
    if (yPos > pageHeight - 30) {
      doc.addPage()
      yPos = 20
    }

    const detailTitleCanvas = await createTextCanvas('详细测试结果', 14, pageWidth - 40)
    const detailTitleImg = detailTitleCanvas.toDataURL('image/png')
    const detailTitleHeight = (detailTitleCanvas.height * (pageWidth - 40)) / detailTitleCanvas.width
    doc.addImage(detailTitleImg, 'PNG', 20, yPos, pageWidth - 40, detailTitleHeight)
    yPos += detailTitleHeight + 8

    doc.setFontSize(10)
    doc.setFont('helvetica', 'normal')
    for (let index = 0; index < Math.min(15, report.value.testResults.length); index++) {
      const result = report.value.testResults[index]
      if (yPos > pageHeight - 40) {
        doc.addPage()
        yPos = 20
      }
      
      const modelName = getModelName(result.modelName)
      const resultTitle = `结果 ${index + 1}: ${modelName}`
      const titleCanvas = await createTextCanvas(resultTitle, 11, pageWidth - 40)
      const titleImg = titleCanvas.toDataURL('image/png')
      const titleHeight = (titleCanvas.height * (pageWidth - 40)) / titleCanvas.width
      doc.addImage(titleImg, 'PNG', 20, yPos, pageWidth - 40, titleHeight)
      yPos += titleHeight + 5
      
      if (yPos > pageHeight - 50) {
        doc.addPage()
        yPos = 20
      }
      
      const metricsCanvas = await createMetricsCanvas(result, pageWidth - 40)
      const metricsImg = metricsCanvas.toDataURL('image/png')
      const metricsHeight = (metricsCanvas.height * (pageWidth - 40)) / metricsCanvas.width
      doc.addImage(metricsImg, 'PNG', 20, yPos, pageWidth - 40, metricsHeight)
      yPos += metricsHeight + 8
      
      if (result.inputPrompt) {
        if (yPos > pageHeight - 50) {
          doc.addPage()
          yPos = 20
        }
        
        const inputCanvas = await createSectionCanvas('输入提示词', result.inputPrompt, pageWidth - 40, true)
        const inputImg = inputCanvas.toDataURL('image/png')
        const inputImgWidth = pageWidth - 40
        const inputImgHeight = (inputCanvas.height * inputImgWidth) / inputCanvas.width
        
        const inputMaxHeightPerPage = pageHeight - 40
        
        if (inputImgHeight <= inputMaxHeightPerPage - (yPos - 20)) {
          doc.addImage(inputImg, 'PNG', 20, yPos, inputImgWidth, inputImgHeight)
          yPos += inputImgHeight + 10
        } else {
          const inputTotalPages = Math.ceil(inputImgHeight / inputMaxHeightPerPage)
          let inputAccumulatedHeight = 0
          
          for (let page = 0; page < inputTotalPages; page++) {
            if (page > 0) {
              doc.addPage()
              yPos = 20
            }
            
            const inputRemainingContentHeight = inputImgHeight - inputAccumulatedHeight
            const inputCurrentPageAvailableHeight = page === 0 
              ? pageHeight - yPos - 20 
              : inputMaxHeightPerPage
            
            const inputDestHeight = Math.min(inputCurrentPageAvailableHeight, inputRemainingContentHeight)
            const inputSourceY = (inputAccumulatedHeight * inputCanvas.height) / inputImgHeight
            const inputSourceHeight = (inputDestHeight * inputCanvas.height) / inputImgHeight
            
            if (inputSourceHeight > 0 && inputDestHeight > 0) {
              const inputTempCanvas = document.createElement('canvas')
              inputTempCanvas.width = inputCanvas.width
              inputTempCanvas.height = Math.ceil(inputSourceHeight)
              const inputTempCtx = inputTempCanvas.getContext('2d')!
              
              inputTempCtx.fillStyle = '#ffffff'
              inputTempCtx.fillRect(0, 0, inputTempCanvas.width, inputTempCanvas.height)
              
              inputTempCtx.drawImage(
                inputCanvas,
                0,
                Math.floor(inputSourceY),
                inputCanvas.width,
                Math.ceil(inputSourceHeight),
                0,
                0,
                inputCanvas.width,
                Math.ceil(inputSourceHeight)
              )
              
              const inputTempImg = inputTempCanvas.toDataURL('image/png')
              doc.addImage(inputTempImg, 'PNG', 20, yPos, inputImgWidth, inputDestHeight)
              yPos += inputDestHeight
              inputAccumulatedHeight += inputDestHeight
            }
          }
          yPos += 10
        }
      }
      
      if (result.outputText) {
        if (yPos > pageHeight - 50) {
          doc.addPage()
          yPos = 20
        }
        
        const outputCanvas = await createSectionCanvas('输出内容', result.outputText, pageWidth - 40, false)
        const outputImg = outputCanvas.toDataURL('image/png')
        const imgWidth = pageWidth - 40
        const imgHeight = (outputCanvas.height * imgWidth) / outputCanvas.width
        
        const maxHeightPerPage = pageHeight - 40
        
        if (imgHeight <= maxHeightPerPage - (yPos - 20)) {
          doc.addImage(outputImg, 'PNG', 20, yPos, imgWidth, imgHeight)
          yPos += imgHeight + 10
        } else {
          const totalPages = Math.ceil(imgHeight / maxHeightPerPage)
          let accumulatedHeight = 0
          
          for (let page = 0; page < totalPages; page++) {
            if (page > 0) {
              doc.addPage()
              yPos = 20
            }
            
            const remainingContentHeight = imgHeight - accumulatedHeight
            const currentPageAvailableHeight = page === 0 
              ? pageHeight - yPos - 20 
              : maxHeightPerPage
            
            const destHeight = Math.min(currentPageAvailableHeight, remainingContentHeight)
            const sourceY = (accumulatedHeight * outputCanvas.height) / imgHeight
            const sourceHeight = (destHeight * outputCanvas.height) / imgHeight
            
            if (sourceHeight > 0 && destHeight > 0) {
              const tempCanvas = document.createElement('canvas')
              tempCanvas.width = outputCanvas.width
              tempCanvas.height = Math.ceil(sourceHeight)
              const tempCtx = tempCanvas.getContext('2d')!
              
              tempCtx.fillStyle = '#ffffff'
              tempCtx.fillRect(0, 0, tempCanvas.width, tempCanvas.height)
              
              tempCtx.drawImage(
                outputCanvas,
                0,
                Math.floor(sourceY),
                outputCanvas.width,
                Math.ceil(sourceHeight),
                0,
                0,
                outputCanvas.width,
                Math.ceil(sourceHeight)
              )
              
              const tempImg = tempCanvas.toDataURL('image/png')
              doc.addImage(tempImg, 'PNG', 20, yPos, imgWidth, destHeight)
              yPos += destHeight
              accumulatedHeight += destHeight
            }
          }
          yPos += 10
        }
      } else {
        yPos += 5
      }
    }

    hideMessage()
    const fileName = `测试报告_${report.value.taskName || report.value.taskId}_${new Date().toISOString().split('T')[0]}.pdf`
    doc.save(fileName)
    message.success('PDF导出成功')
  } catch (error: any) {
    message.destroy()
    console.error('导出PDF失败:', error)
    message.error('导出PDF失败: ' + (error.message || '未知错误'))
  } finally {
    exportingPDF.value = false
  }
}

const closeModal = () => {
  modalVisible.value = false
  selectedRecord.value = null
}

const getModelName = (id: string | undefined) => {
  if (!id) return ''
  return id.split('/').pop() || id
}

const getIconFile = (modelId: string) => {
  const provider = modelId.split('/')[0]?.toLowerCase() || ''
  const iconMap: Record<string, string> = {
    'openai': 'openai.png',
    'anthropic': 'anthropic.png',
    'google': 'google.png',
    'meta-llama': 'meta-llama.png',
    'qwen': 'qwen.png',
    'alibaba': 'alibaba.png',
    'deepseek': 'deepseek.png',
    'baidu': 'baidu.png',
    'zhipu': 'zhipu.png',
    'z-ai': 'zhipu.png',
    'moonshot': 'moonshot.png',
    'moonshotai': 'moonshot.png',
    'tencent': 'tencent.png',
    'bytedance': 'bytedance.png',
    'bytedance-seed': 'bytedance.png',
    'meituan': 'meituan.png',
  }
  return iconMap[provider] || 'default.png'
}

const getDefaultIconUrl = () => {
  return new URL('../assets/provider-icons/default.png', import.meta.url).href
}

const getProviderIcon = (modelId: string | undefined) => {
  if (!modelId) {
    return getDefaultIconUrl()
  }
  const iconFile = getIconFile(modelId)
  return new URL(`../assets/provider-icons/${iconFile}`, import.meta.url).href
}

const handleImageError = (e: Event) => {
  const img = e.target as HTMLImageElement
  if (img.src !== getDefaultIconUrl()) {
    img.src = getDefaultIconUrl()
    img.onerror = null
  }
}

onMounted(() => {
  loadTask()
  loadReport()
})

watch(() => route.params.id, () => {
  loadTask()
  loadReport()
})
</script>

<style scoped>
.task-report-page {
  padding: 24px;
  padding-bottom: 48px;
  max-width: 1400px;
  margin: 0 auto;
  min-height: 100%;
}


.model-cell {
  display: flex;
  align-items: center;
  gap: 8px;
}

.model-icon {
  width: 24px;
  height: 24px;
  border-radius: 4px;
  object-fit: contain;
}

.model-name {
  font-size: 14px;
  color: #333;
}

.output-cell {
  cursor: pointer;
  padding: 8px;
  border-radius: 4px;
  transition: background-color 0.2s;
}

.output-cell:hover {
  background-color: #f5f5f5;
}

.output-preview {
  margin: 0;
  max-width: 600px;
  word-break: break-word;
  overflow-wrap: break-word;
}

.view-full-btn {
  padding: 0;
  margin-top: 4px;
  font-size: 12px;
}

.metrics-cell {
  font-size: 12px;
  line-height: 1.8;
  color: #666;
}

.modal-content {
  padding: 16px 0;
}

.modal-header {
  display: flex;
  align-items: center;
  gap: 12px;
  padding-bottom: 16px;
  border-bottom: 1px solid #f0f0f0;
  margin-bottom: 16px;
}

.modal-icon {
  width: 32px;
  height: 32px;
  border-radius: 4px;
  object-fit: contain;
}

.modal-info h3 {
  margin: 0 0 8px 0;
  font-size: 16px;
  font-weight: 600;
}

.modal-metrics {
  display: flex;
  gap: 16px;
  font-size: 12px;
  color: #666;
}

.modal-body {
  max-height: 60vh;
  overflow-y: auto;
}

.input-section {
  margin-bottom: 24px;
  padding: 16px;
  background-color: #f0f9ff;
  border-radius: 8px;
  border-left: 3px solid #52c41a;
}

.input-section h4 {
  margin: 0 0 12px 0;
  font-size: 14px;
  font-weight: 600;
  color: #52c41a;
}

.input-content {
  font-size: 13px;
  line-height: 1.6;
  color: #333;
}

.thinking-section {
  margin-bottom: 24px;
  padding: 16px;
  background-color: #f9f9f9;
  border-radius: 8px;
  border-left: 3px solid #1890ff;
}

.thinking-section h4 {
  margin: 0 0 12px 0;
  font-size: 14px;
  font-weight: 600;
  color: #1890ff;
}

.thinking-content {
  font-size: 13px;
  line-height: 1.6;
  color: #666;
}

.output-section {
  padding: 16px;
  background-color: #fff;
  border-radius: 8px;
  border: 1px solid #e8e8e8;
}

.output-section h4 {
  margin: 0 0 12px 0;
  font-size: 14px;
  font-weight: 600;
  color: #333;
}

.output-content {
  font-size: 14px;
  line-height: 1.7;
  color: #333;
  max-width: 800px;
  word-break: break-word;
  overflow-wrap: break-word;
}
</style>
