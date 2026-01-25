<template>
  <div class="batch-test-page">
    <a-card title="创建批量测试任务" :bordered="false">
      <a-form :model="form" :label-col="{ span: 6 }" :wrapper-col="{ span: 18 }">
        <a-form-item label="任务名称" name="name">
          <a-input v-model:value="form.name" placeholder="请输入任务名称（可选）" />
        </a-form-item>

        <a-form-item label="选择场景" name="sceneId" :rules="[{ required: true, message: '请选择场景' }]">
          <div style="display: flex; align-items: center; gap: 8px; width: 100%">
            <a-select
              v-model:value="form.sceneId"
              placeholder="请选择测试场景"
              :loading="loadingScenes"
              show-search
              :filter-option="filterOption"
              style="flex: 1; min-width: 0"
            >
              <a-select-option v-for="scene in scenes" :key="scene.id" :value="scene.id">
                {{ scene.name }}
              </a-select-option>
            </a-select>
            <a-button type="link" style="flex-shrink: 0; white-space: nowrap" @click="handleManageScenes">
              管理场景
            </a-button>
          </div>
        </a-form-item>

        <a-form-item label="选择模型" name="models" :rules="[{ required: true, message: '请至少选择一个模型' }]">
          <a-select
            v-model:value="form.models"
            mode="multiple"
            placeholder="请选择要测试的模型（可多选）"
            :loading="loadingModels"
            :max-tag-count="3"
            style="width: 100%"
            show-search
            :filter-option="false"
            @search="handleSearchModel"
            @popup-scroll="handlePopupScroll"
          >
            <a-select-option v-for="model in modelOptions" :key="model.value" :value="model.value">
              {{ model.label }}
            </a-select-option>
          </a-select>
        </a-form-item>

        <a-divider>高级参数配置（可选）</a-divider>

        <a-form-item label="Temperature" name="temperature">
          <a-input-number
            v-model:value="form.temperature"
            :min="0"
            :max="2"
            :step="0.1"
            placeholder="0.7"
            style="width: 100%"
          />
          <div style="font-size: 12px; color: #999; margin-top: 4px">
            控制输出的随机性，范围 0.0-2.0，默认 0.7
          </div>
        </a-form-item>

        <a-form-item label="Top P" name="topP">
          <a-input-number
            v-model:value="form.topP"
            :min="0"
            :max="1"
            :step="0.1"
            placeholder="1.0"
            style="width: 100%"
          />
          <div style="font-size: 12px; color: #999; margin-top: 4px">
            核采样参数，范围 0.0-1.0，默认 1.0
          </div>
        </a-form-item>

        <a-form-item label="Max Tokens" name="maxTokens">
          <a-input-number
            v-model:value="form.maxTokens"
            :min="1"
            :max="32768"
            placeholder="4096"
            style="width: 100%"
          />
          <div style="font-size: 12px; color: #999; margin-top: 4px">
            最大生成Token数，默认 4096
          </div>
        </a-form-item>

        <a-form-item label="Top K" name="topK">
          <a-input-number
            v-model:value="form.topK"
            :min="1"
            placeholder="可选"
            style="width: 100%"
          />
          <div style="font-size: 12px; color: #999; margin-top: 4px">
            限制采样范围，仅考虑前K个最可能的Token
          </div>
        </a-form-item>

        <a-form-item label="Frequency Penalty" name="frequencyPenalty">
          <a-input-number
            v-model:value="form.frequencyPenalty"
            :min="-2"
            :max="2"
            :step="0.1"
            placeholder="0.0"
            style="width: 100%"
          />
          <div style="font-size: 12px; color: #999; margin-top: 4px">
            频率惩罚，范围 -2.0-2.0，默认 0.0
          </div>
        </a-form-item>

        <a-form-item label="Presence Penalty" name="presencePenalty">
          <a-input-number
            v-model:value="form.presencePenalty"
            :min="-2"
            :max="2"
            :step="0.1"
            placeholder="0.0"
            style="width: 100%"
          />
          <div style="font-size: 12px; color: #999; margin-top: 4px">
            存在惩罚，范围 -2.0-2.0，默认 0.0
          </div>
        </a-form-item>

        <a-form-item :wrapper-col="{ offset: 6, span: 18 }">
          <a-button type="primary" @click="handleCreate" :loading="creating">
            创建测试任务
          </a-button>
        </a-form-item>
      </a-form>
    </a-card>

    <!-- 任务进度监控 -->
    <a-card v-if="currentTask" title="任务进度" :bordered="false" style="margin-top: 16px">
      <a-descriptions :column="2" bordered>
        <a-descriptions-item label="任务ID">{{ currentTask.id }}</a-descriptions-item>
        <a-descriptions-item label="任务名称">{{ currentTask.name || '未命名' }}</a-descriptions-item>
        <a-descriptions-item label="状态">
          <a-tag :color="getStatusColor(currentTask.status)">
            {{ getStatusText(currentTask.status) }}
          </a-tag>
        </a-descriptions-item>
        <a-descriptions-item label="进度">
          {{ currentTask.completedSubtasks }} / {{ currentTask.totalSubtasks }}
        </a-descriptions-item>
      </a-descriptions>

      <a-progress
        :percent="progressPercentage"
        :status="currentTask.status === 'failed' ? 'exception' : 'active'"
        style="margin-top: 16px"
      />

      <div v-if="progressInfo.currentModel" style="margin-top: 16px">
        <a-typography-text type="secondary">
          当前测试: {{ progressInfo.currentModel }}
          <span v-if="progressInfo.currentPrompt"> - {{ progressInfo.currentPrompt }}</span>
        </a-typography-text>
      </div>

      <div style="margin-top: 16px">
        <a-button style="margin-left: 8px" @click="handleViewDetail">
          查看详情
        </a-button>
        <a-button
          v-if="currentTask && currentTask.status === 'completed'"
          type="primary"
          style="margin-left: 8px"
          @click="handleViewReport"
        >
          查看报告
        </a-button>
        <a-button style="margin-left: 8px" @click="handleBackToList">
          返回列表
        </a-button>
      </div>
    </a-card>

    <!-- 测试结果 -->
    <a-card
      v-if="currentTask && currentTask.status === 'completed' && results.length > 0"
      title="测试结果"
      :bordered="false"
      style="margin-top: 16px"
    >
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
import { ref, computed, onMounted, onUnmounted } from 'vue'
import { useRouter, useRoute } from 'vue-router'
import { message } from 'ant-design-vue'
import {
  createBatchTestTask,
  getTask,
  getTaskResults,
  updateTestResultRating,
  type CreateBatchTestRequest,
  type TestTask,
  type TestResult,
  type TaskProgressVO
} from '@/api/batchTestController'
import { listScenes, type Scene } from '@/api/sceneController'
import { listModels, type ModelVO } from '@/api/modelController'
import { createWebSocketClient } from '@/utils/websocketClient'
import { API_BASE_URL } from '@/config/env'
import MarkdownRenderer from '@/components/MarkdownRenderer.vue'

const router = useRouter()
const route = useRoute()

const form = ref<CreateBatchTestRequest>({
  name: '',
  sceneId: '',
  models: [],
  temperature: undefined,
  topP: undefined,
  maxTokens: undefined,
  topK: undefined,
  frequencyPenalty: undefined,
  presencePenalty: undefined
})

const scenes = ref<Scene[]>([])
const loadingScenes = ref(false)
const modelOptions = ref<{ label: string; value: string }[]>([])
const loadingModels = ref(false)
const creating = ref(false)

// 分页相关
const currentPage = ref(1)
const pageSize = 50
const hasMore = ref(true)
const currentSearchText = ref<string>()

const currentTask = ref<TestTask | null>(null)
const progressInfo = ref<TaskProgressVO>({
  taskId: '',
  percentage: 0,
  completedSubtasks: 0,
  totalSubtasks: 0,
  status: 'pending',
  timestamp: 0
})
const results = ref<TestResult[]>([])
const loadingResults = ref(false)
const modalVisible = ref(false)
const selectedRecord = ref<TestResult | null>(null)

const progressPercentage = computed(() => {
  if (!currentTask.value) return 0
  if (currentTask.value.totalSubtasks === 0) return 0
  return Math.round(
    (currentTask.value.completedSubtasks / currentTask.value.totalSubtasks) * 100
  )
})

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

let wsClient: ReturnType<typeof createWebSocketClient> | null = null

const filterOption = (input: string, option: any) => {
  return option.children.toLowerCase().indexOf(input.toLowerCase()) >= 0
}

const loadScenes = async () => {
  try {
    loadingScenes.value = true
    const res = await listScenes({ pageNum: 1, pageSize: 100 })
    if (res.data?.code === 0 && res.data.data) {
      scenes.value = res.data.data.records || []
    }
  } catch (error) {
    console.error('加载场景失败:', error)
    message.error('加载场景失败')
  } finally {
    loadingScenes.value = false
  }
}

const loadModels = async (searchText?: string, append: boolean = false) => {
  try {
    loadingModels.value = true

    // 如果是新搜索，重置分页
    if (!append) {
      currentPage.value = 1
      currentSearchText.value = searchText
    }

    const res: any = await listModels({
      pageNum: currentPage.value,
      pageSize,
      searchText: currentSearchText.value || undefined
    })

    if (res.data?.code === 0 && res.data.data?.records) {
      const models = res.data.data.records
      const newOptions = models.map((m: ModelVO) => ({
        label: m.name,
        value: m.id
      }))

      if (append) {
        // 追加模式：去重后添加
        const existingIds = new Set(modelOptions.value.map((o: any) => o.value))
        const uniqueNewOptions = newOptions.filter((o: any) => !existingIds.has(o.value))
        modelOptions.value = [...modelOptions.value, ...uniqueNewOptions]
      } else {
        // 覆盖模式：替换列表
        modelOptions.value = newOptions
      }

      // 判断是否还有更多数据
      hasMore.value = models.length >= pageSize
      console.log('模型加载:', append ? '追加' : '初始', newOptions.length, '个，总计', modelOptions.value.length)
    }
  } catch (error) {
    console.error('加载模型失败:', error)
    message.error('加载模型失败')
  } finally {
    loadingModels.value = false
  }
}

// 搜索模型
const handleSearchModel = (value: string) => {
  console.log('🔍 搜索模型:', value)

  // 如果搜索内容为空，清空搜索状态并重新加载所有模型
  if (!value || value.trim() === '') {
    console.log('🔄 清空搜索，重新加载所有模型')
    currentSearchText.value = undefined
    currentPage.value = 1
    loadModels(undefined, false)
  } else {
    currentSearchText.value = value
    currentPage.value = 1
    loadModels(value, false)
  }
}

// 下拉到底部加载更多
const handlePopupScroll = (e: Event) => {
  const target = e.target as HTMLElement
  const scrollTop = target.scrollTop
  const scrollHeight = target.scrollHeight
  const clientHeight = target.clientHeight

  // 滚动到底部且还有更多数据且不在加载中
  if (scrollHeight - scrollTop - clientHeight < 50 && hasMore.value && !loadingModels.value) {
    currentPage.value++
    loadModels(undefined, true)  // 追加模式，使用当前搜索词
  }
}

const handleCreate = async () => {
  if (!form.value.sceneId) {
    message.error('请选择场景')
    return
  }
  if (!form.value.models || form.value.models.length === 0) {
    message.error('请至少选择一个模型')
    return
  }

  try {
    creating.value = true
    const res = await createBatchTestTask(form.value)
    if (res.data?.code === 0) {
      const taskId = res.data.data
      message.success('任务创建成功')
      
      // 加载任务详情
      await loadTask(taskId)
      
      // 连接WebSocket监听进度
      connectWebSocket(taskId)
    } else {
      message.error(res.data?.message || '创建任务失败')
    }
  } catch (error: any) {
    console.error('创建任务失败:', error)
    message.error(error.message || '创建任务失败')
  } finally {
    creating.value = false
  }
}

const loadTask = async (taskId: string) => {
  try {
    const res = await getTask(taskId)
    if (res.data?.code === 0 && res.data.data) {
      currentTask.value = res.data.data
      // 如果任务已完成，自动加载测试结果
      if (currentTask.value.status === 'completed') {
        loadResults(taskId)
      }
    }
  } catch (error) {
    console.error('加载任务失败:', error)
  }
}

const loadResults = async (taskId: string) => {
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

const connectWebSocket = (taskId: string) => {
  if (wsClient) {
    wsClient.disconnect()
  }

  wsClient = createWebSocketClient(API_BASE_URL, {
    onConnect: () => {
      console.log('✅ WebSocket连接成功')
      wsClient?.subscribe(`/topic/task/${taskId}`)
    },
    onMessage: (topic: string, progressData: TaskProgressVO) => {
      console.log('📨 收到进度更新:', progressData)
      progressInfo.value = progressData
      
      // 更新任务信息
      if (currentTask.value) {
        currentTask.value.completedSubtasks = progressData.completedSubtasks
        currentTask.value.totalSubtasks = progressData.totalSubtasks
        currentTask.value.status = progressData.status
        
        if (progressData.status === 'completed') {
          message.success('测试任务完成！')
          // 任务完成后自动加载测试结果
          if (currentTask.value) {
            loadResults(currentTask.value.id)
          }
        } else if (progressData.status === 'failed') {
          message.error('测试任务失败')
        }
      }
    },
    onError: (error) => {
      console.error('❌ WebSocket错误:', error)
      message.error('WebSocket连接错误，请刷新页面重试')
    },
    onDisconnect: () => {
      console.log('🔌 WebSocket断开连接')
    }
  })

  wsClient.connect()
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

const handleViewDetail = () => {
  if (currentTask.value) {
    router.push(`/batch-test/detail/${currentTask.value.id}`)
  }
}

const handleViewReport = () => {
  if (!currentTask.value?.id) {
    message.error('任务ID不存在')
    return
  }
  if (currentTask.value.status !== 'completed') {
    message.warning('任务未完成，暂无报告')
    return
  }
  router.push(`/batch-test/report/${currentTask.value.id}`)
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

const handleBackToList = () => {
  router.push('/batch-test/list')
}

const handleManageScenes = () => {
  router.push('/scene/manage')
}

const loadTaskConfig = async (taskId: string) => {
  try {
    const res = await getTask(taskId)
    if (res.data?.code === 0 && res.data.data) {
      const task = res.data.data as any
      form.value.sceneId = task.sceneId
      try {
        const modelsJson = JSON.parse(task.models)
        form.value.models = Array.isArray(modelsJson) ? modelsJson : []
      } catch {
        form.value.models = []
      }
      if (task.name) {
        form.value.name = `${task.name} (复制)`
      }
      if (task.config) {
        try {
          const config = JSON.parse(task.config)
          form.value.temperature = config.temperature
          form.value.topP = config.topP
          form.value.maxTokens = config.maxTokens
          form.value.topK = config.topK
          form.value.frequencyPenalty = config.frequencyPenalty
          form.value.presencePenalty = config.presencePenalty
        } catch (e) {
          console.warn('解析任务配置失败:', e)
        }
      }
      message.success('已加载任务配置')
    }
  } catch (error) {
    console.error('加载任务配置失败:', error)
    message.error('加载任务配置失败')
  }
}

onMounted(async () => {
  await loadScenes()
  await loadModels(undefined, false)
  
  const copyFrom = route.query.copyFrom as string
  if (copyFrom) {
    await loadTaskConfig(copyFrom)
  }
})

onUnmounted(() => {
  if (wsClient) {
    wsClient.disconnect()
  }
})
</script>

<style scoped>
.batch-test-page {
  padding: 24px;
  max-width: 1400px;
  margin: 0 auto;
  min-height: 100%;
  padding-bottom: 48px;
  width: 100%;
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
