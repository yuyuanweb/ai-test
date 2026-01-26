<template>
  <div class="task-detail-page">
    <a-card title="任务详情" :bordered="false">
      <a-descriptions :column="2" bordered>
        <a-descriptions-item label="任务ID">{{ task?.id }}</a-descriptions-item>
        <a-descriptions-item label="任务名称">{{ task?.name || '未命名' }}</a-descriptions-item>
        <a-descriptions-item label="状态">
          <a-tag :color="getStatusColor(task?.status || '')">
            {{ getStatusText(task?.status || '') }}
          </a-tag>
        </a-descriptions-item>
        <a-descriptions-item label="进度">
          {{ task?.completedSubtasks }} / {{ task?.totalSubtasks }}
        </a-descriptions-item>
        <a-descriptions-item label="创建时间">{{ task?.createTime }}</a-descriptions-item>
        <a-descriptions-item label="完成时间">{{ task?.completedAt || '-' }}</a-descriptions-item>
      </a-descriptions>

      <a-progress
        v-if="task"
        :percent="getProgressPercent(task)"
        :status="task.status === 'failed' ? 'exception' : 'active'"
        style="margin-top: 16px"
      />

      <div style="margin-top: 16px">
        <a-button
          v-if="task?.status === 'completed'"
          type="primary"
          style="margin-left: 8px"
          @click="handleViewReport"
        >
          查看报告
        </a-button>
        <a-button style="margin-left: 8px" @click="handleCopyTask">
          重新测试
        </a-button>
        <a-button style="margin-left: 8px" @click="handleBack">
          返回列表
        </a-button>
      </div>
    </a-card>

    <a-card title="测试结果" :bordered="false" style="margin-top: 16px">
      <a-table
        :columns="resultColumns"
        :data-source="results"
        :loading="loadingResults"
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
            <template v-else-if="column.key === 'aiScore'">
              <div v-if="record.aiScore" class="ai-score-cell">
                <a-tag color="blue" @click="showAiScoreDetail(record)" style="cursor: pointer">
                  {{ getAiScoreRating(record.aiScore) }}/10
                </a-tag>
              </div>
              <span v-else style="color: #999">-</span>
            </template>
          </template>
        </a-table>
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
          <!-- AI评分（如果有） -->
          <div v-if="selectedRecord.aiScore" class="ai-score-section">
            <h4>AI评分</h4>
            <div class="ai-score-content">
              <div v-for="(judge, index) in getAiScoreData(selectedRecord.aiScore)?.judges" :key="index" class="judge-item">
                <div class="judge-header">
                  <span class="judge-model">评委模型: {{ judge.model }}</span>
                  <a-tag color="blue" style="font-size: 16px; padding: 4px 12px">
                    {{ judge.rating }}/10
                  </a-tag>
                </div>
                <div class="judge-scores">
                  <div class="score-item">
                    <span class="score-label">准确性:</span>
                    <span class="score-value">{{ judge.scores.accuracy }}/30</span>
                  </div>
                  <div class="score-item">
                    <span class="score-label">相关性:</span>
                    <span class="score-value">{{ judge.scores.relevance }}/20</span>
                  </div>
                  <div class="score-item">
                    <span class="score-label">完整性:</span>
                    <span class="score-value">{{ judge.scores.completeness }}/20</span>
                  </div>
                  <div class="score-item">
                    <span class="score-label">清晰度:</span>
                    <span class="score-value">{{ judge.scores.clarity }}/15</span>
                  </div>
                  <div class="score-item">
                    <span class="score-label">创意性:</span>
                    <span class="score-value">{{ judge.scores.creativity }}/15</span>
                  </div>
                  <div class="score-item total">
                    <span class="score-label">总分:</span>
                    <span class="score-value">{{ judge.totalScore }}/100</span>
                  </div>
                </div>
                <div v-if="judge.comment" class="judge-comment">
                  <strong>评价:</strong> {{ judge.comment }}
                </div>
              </div>
            </div>
          </div>
        </div>
      </div>
    </a-modal>

    <!-- AI评分详情模态框 -->
    <a-modal
      v-model:open="aiScoreModalVisible"
      title="AI评分详情"
      width="600px"
      :footer="null"
      @cancel="closeAiScoreModal"
    >
      <div v-if="selectedAiScoreRecord" class="ai-score-detail">
        <div v-for="(judge, index) in getAiScoreData(selectedAiScoreRecord.aiScore)?.judges" :key="index" class="judge-item">
          <div class="judge-header">
            <span class="judge-model">评委模型: {{ judge.model }}</span>
            <a-tag color="blue" style="font-size: 16px; padding: 4px 12px">
              {{ judge.rating }}/10
            </a-tag>
          </div>
          <div class="judge-scores">
            <div class="score-item">
              <span class="score-label">准确性:</span>
              <span class="score-value">{{ judge.scores.accuracy }}/30</span>
            </div>
            <div class="score-item">
              <span class="score-label">相关性:</span>
              <span class="score-value">{{ judge.scores.relevance }}/20</span>
            </div>
            <div class="score-item">
              <span class="score-label">完整性:</span>
              <span class="score-value">{{ judge.scores.completeness }}/20</span>
            </div>
            <div class="score-item">
              <span class="score-label">清晰度:</span>
              <span class="score-value">{{ judge.scores.clarity }}/15</span>
            </div>
            <div class="score-item">
              <span class="score-label">创意性:</span>
              <span class="score-value">{{ judge.scores.creativity }}/15</span>
            </div>
            <div class="score-item total">
              <span class="score-label">总分:</span>
              <span class="score-value">{{ judge.totalScore }}/100</span>
            </div>
          </div>
          <div v-if="judge.comment" class="judge-comment">
            <strong>评价:</strong> {{ judge.comment }}
          </div>
        </div>
      </div>
    </a-modal>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { useRouter, useRoute } from 'vue-router'
import { message } from 'ant-design-vue'
import {
  getTask,
  getTaskResults,
  updateTestResultRating,
  type TestTask,
  type TestResult
} from '@/api/batchTestController'
import MarkdownRenderer from '@/components/MarkdownRenderer.vue'

const router = useRouter()
const route = useRoute()

const task = ref<TestTask | null>(null)
const results = ref<TestResult[]>([])
const loadingResults = ref(false)
const modalVisible = ref(false)
const selectedRecord = ref<TestResult | null>(null)
const aiScoreModalVisible = ref(false)
const selectedAiScoreRecord = ref<TestResult | null>(null)

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
    title: 'AI评分',
    key: 'aiScore',
    width: 120
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

const loadResults = async () => {
  const taskId = route.params.id as string
  if (!taskId) {
    return
  }

  try {
    loadingResults.value = true
    const res = await getTaskResults(taskId)
    if (res.data?.code === 0 && res.data.data) {
      results.value = res.data.data || []
    } else {
      message.error(res.data?.message || '加载结果失败')
    }
  } catch (error: any) {
    console.error('加载结果失败:', error)
    message.error(error.message || '加载结果失败')
  } finally {
    loadingResults.value = false
  }
}

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

const getProgressPercent = (task: TestTask) => {
  if (task.totalSubtasks === 0) return 0
  return Math.round((task.completedSubtasks / task.totalSubtasks) * 100)
}

const handleBack = () => {
  router.push('/batch-test/list')
}

const handleCopyTask = () => {
  const taskId = route.params.id as string
  router.push({
    path: '/batch-test/create',
    query: {
      copyFrom: taskId
    }
  })
}

const handleViewReport = () => {
  const taskId = route.params.id as string
  if (!taskId) {
    message.error('任务ID不存在')
    return
  }
  if (task.value?.status !== 'completed') {
    message.warning('任务未完成，暂无报告')
    return
  }
  router.push(`/batch-test/report/${taskId}`)
}

const handleRatingChange = async (resultId: string, rating: number) => {
  try {
    const res = await updateTestResultRating({
      resultId,
      userRating: rating || undefined
    })
    if (res.data?.code === 0) {
      message.success('评分已更新')
      const result = results.value.find(r => r.id === resultId)
      if (result) {
        result.userRating = rating || undefined
      }
    } else {
      message.error(res.data?.message || '更新评分失败')
    }
  } catch (error: any) {
    console.error('更新评分失败:', error)
    message.error(error.message || '更新评分失败')
  }
}

const showFullContent = (record: TestResult) => {
  selectedRecord.value = record
  modalVisible.value = true
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

interface AIScoreData {
  judges: Array<{
    model: string
    scores: {
      accuracy: number
      relevance: number
      completeness: number
      clarity: number
      creativity: number
    }
    totalScore: number
    rating: number
    comment: string
  }>
  averageRating: number
}

const getAiScoreRating = (aiScoreJson: string | undefined): number => {
  if (!aiScoreJson) return 0
  try {
    const data: AIScoreData = JSON.parse(aiScoreJson)
    return data.averageRating ? Math.round(data.averageRating * 10) / 10 : 0
  } catch {
    return 0
  }
}

const showAiScoreDetail = (record: TestResult) => {
  selectedAiScoreRecord.value = record
  aiScoreModalVisible.value = true
}

const closeAiScoreModal = () => {
  aiScoreModalVisible.value = false
  selectedAiScoreRecord.value = null
}

const getAiScoreData = (aiScoreJson: string | undefined): AIScoreData | null => {
  if (!aiScoreJson) return null
  try {
    return JSON.parse(aiScoreJson) as AIScoreData
  } catch {
    return null
  }
}

onMounted(() => {
  loadTask()
  loadResults()
})
</script>

<style scoped>
.task-detail-page {
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

.ai-score-cell {
  display: flex;
  align-items: center;
}

.ai-score-section {
  margin-top: 24px;
  padding: 16px;
  background-color: #f0f9ff;
  border-radius: 8px;
  border-left: 3px solid #1890ff;
}

.ai-score-section h4 {
  margin: 0 0 12px 0;
  font-size: 14px;
  font-weight: 600;
  color: #1890ff;
}

.ai-score-content {
  font-size: 13px;
}

.judge-item {
  margin-bottom: 16px;
  padding: 12px;
  background-color: #fff;
  border-radius: 6px;
  border: 1px solid #e8e8e8;
}

.judge-item:last-child {
  margin-bottom: 0;
}

.judge-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 12px;
  padding-bottom: 8px;
  border-bottom: 1px solid #e8e8e8;
}

.judge-model {
  font-size: 13px;
  font-weight: 600;
  color: #333;
}

.judge-scores {
  display: grid;
  grid-template-columns: repeat(2, 1fr);
  gap: 8px;
  margin-bottom: 8px;
}

.score-item {
  display: flex;
  justify-content: space-between;
  padding: 6px 10px;
  background-color: #fafafa;
  border-radius: 4px;
  border: 1px solid #e8e8e8;
}

.score-item.total {
  grid-column: 1 / -1;
  background-color: #e6f7ff;
  border-color: #1890ff;
  font-weight: 600;
}

.score-label {
  font-size: 12px;
  color: #666;
}

.score-value {
  font-size: 12px;
  font-weight: 600;
  color: #333;
}

.judge-comment {
  padding: 10px;
  background-color: #fafafa;
  border-radius: 4px;
  border: 1px solid #e8e8e8;
  font-size: 12px;
  line-height: 1.6;
  color: #666;
  margin-top: 8px;
}

.judge-comment strong {
  color: #333;
  margin-right: 6px;
}

.ai-score-detail {
  padding: 16px 0;
}

.ai-score-detail .judge-item {
  margin-bottom: 24px;
  padding: 16px;
  background-color: #f9f9f9;
  border-radius: 8px;
  border-left: 3px solid #1890ff;
}

.ai-score-detail .judge-item:last-child {
  margin-bottom: 0;
}

.ai-score-detail .judge-header {
  margin-bottom: 16px;
  padding-bottom: 12px;
}

.ai-score-detail .judge-scores {
  gap: 12px;
  margin-bottom: 12px;
}

.ai-score-detail .score-item {
  padding: 8px 12px;
}

.ai-score-detail .judge-comment {
  padding: 12px;
  margin-top: 12px;
}
</style>
