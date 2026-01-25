<template>
  <div class="task-compare-page">
    <a-card title="任务对比" :bordered="false">
      <!-- 任务选择区域 -->
      <div class="task-select-section">
        <a-row :gutter="16">
          <a-col :span="12">
            <a-card size="small" title="任务A">
              <a-select
                v-model:value="taskAId"
                placeholder="选择任务A"
                show-search
                :filter-option="filterTaskOption"
                style="width: 100%"
                @change="handleTaskAChange"
              >
                <a-select-option v-for="task in completedTasks" :key="task.id" :value="task.id">
                  {{ task.name || '未命名' }} ({{ task.createTime }})
                </a-select-option>
              </a-select>
              <div v-if="taskA" class="task-info">
                <div><strong>场景:</strong> {{ taskA.sceneName || '-' }}</div>
                <div><strong>模型:</strong> {{ getModelsList(taskA.models).join(', ') }}</div>
                <div><strong>创建时间:</strong> {{ taskA.createTime }}</div>
              </div>
            </a-card>
          </a-col>
          <a-col :span="12">
            <a-card size="small" title="任务B">
              <a-select
                v-model:value="taskBId"
                placeholder="选择任务B"
                show-search
                :filter-option="filterTaskOption"
                style="width: 100%"
                @change="handleTaskBChange"
              >
                <a-select-option v-for="task in completedTasks" :key="task.id" :value="task.id">
                  {{ task.name || '未命名' }} ({{ task.createTime }})
                </a-select-option>
              </a-select>
              <div v-if="taskB" class="task-info">
                <div><strong>场景:</strong> {{ taskB.sceneName || '-' }}</div>
                <div><strong>模型:</strong> {{ getModelsList(taskB.models).join(', ') }}</div>
                <div><strong>创建时间:</strong> {{ taskB.createTime }}</div>
              </div>
            </a-card>
          </a-col>
        </a-row>
      </div>

      <!-- 对比模式选择 -->
      <div v-if="taskA && taskB" class="compare-mode-section">
        <a-radio-group v-model:value="compareMode" @change="handleModeChange">
          <a-radio-button value="full">完整对比</a-radio-button>
          <a-radio-button value="cross-model">跨模型对比</a-radio-button>
          <a-radio-button value="cross-time">跨时间对比</a-radio-button>
        </a-radio-group>
      </div>

      <!-- 对比结果 -->
      <div v-if="compareData.length > 0" class="compare-results">
        <!-- 统计摘要对比 -->
        <a-card title="统计摘要对比" :bordered="false" style="margin-top: 16px">
          <a-row :gutter="16">
            <a-col :span="8">
              <a-statistic title="任务A总成本" :value="statsA.totalCost" prefix="$" :precision="6" />
            </a-col>
            <a-col :span="8">
              <a-statistic title="任务B总成本" :value="statsB.totalCost" prefix="$" :precision="6" />
            </a-col>
            <a-col :span="8">
              <a-statistic
                title="成本差异"
                :value="statsB.totalCost - statsA.totalCost"
                prefix="$"
                :precision="6"
                :value-style="getDiffStyle(statsB.totalCost - statsA.totalCost)"
              />
            </a-col>
          </a-row>
          <a-row :gutter="16" style="margin-top: 16px">
            <a-col :span="8">
              <a-statistic title="任务A平均响应时间" :value="statsA.avgResponseTime" suffix="ms" />
            </a-col>
            <a-col :span="8">
              <a-statistic title="任务B平均响应时间" :value="statsB.avgResponseTime" suffix="ms" />
            </a-col>
            <a-col :span="8">
              <a-statistic
                title="响应时间差异"
                :value="statsB.avgResponseTime - statsA.avgResponseTime"
                suffix="ms"
                :value-style="getDiffStyle(statsA.avgResponseTime - statsB.avgResponseTime)"
              />
            </a-col>
          </a-row>
        </a-card>

        <!-- 详细对比表格 -->
        <a-card title="详细对比" :bordered="false" style="margin-top: 16px">
          <a-table
            :columns="compareColumns"
            :data-source="compareData"
            :loading="loading"
            row-key="key"
            :pagination="{ pageSize: 20 }"
            :scroll="{ x: 'max-content' }"
          >
              <template #bodyCell="{ column, record }">
                <template v-if="column.key === 'promptTitle'">
                  <div class="prompt-title">{{ record.promptTitle }}</div>
                </template>
                <template v-else-if="column.key === 'modelA'">
                  <div v-if="record.resultA" class="model-cell">
                    <img
                      :src="getProviderIcon(record.resultA.modelName)"
                      :alt="getModelName(record.resultA.modelName)"
                      class="model-icon"
                      @error="handleImageError"
                    />
                    <span class="model-name">{{ getModelName(record.resultA.modelName) }}</span>
                  </div>
                  <span v-else class="no-data">-</span>
                </template>
                <template v-else-if="column.key === 'outputA'">
                  <div v-if="record.resultA" class="output-cell" @click="showFullContent(record.resultA, 'A')">
                    <a-typography-paragraph
                      :ellipsis="{ rows: 2, expandable: false }"
                      class="output-preview"
                    >
                      {{ record.resultA.outputText }}
                    </a-typography-paragraph>
                    <a-button type="link" size="small" class="view-full-btn">查看全部</a-button>
                  </div>
                  <span v-else class="no-data">-</span>
                </template>
                <template v-else-if="column.key === 'metricsA'">
                  <div v-if="record.resultA" class="metrics-cell">
                    <div>响应时间: {{ record.resultA.responseTimeMs }}ms</div>
                    <div>Token: {{ record.resultA.inputTokens }}/{{ record.resultA.outputTokens }}</div>
                    <div>成本: ${{ record.resultA.cost?.toFixed(6) || '0.000000' }}</div>
                  </div>
                  <span v-else class="no-data">-</span>
                </template>
                <template v-else-if="column.key === 'modelB'">
                  <div v-if="record.resultB" class="model-cell">
                    <img
                      :src="getProviderIcon(record.resultB.modelName)"
                      :alt="getModelName(record.resultB.modelName)"
                      class="model-icon"
                      @error="handleImageError"
                    />
                    <span class="model-name">{{ getModelName(record.resultB.modelName) }}</span>
                  </div>
                  <span v-else class="no-data">-</span>
                </template>
                <template v-else-if="column.key === 'outputB'">
                  <div v-if="record.resultB" class="output-cell" @click="showFullContent(record.resultB, 'B')">
                    <a-typography-paragraph
                      :ellipsis="{ rows: 2, expandable: false }"
                      class="output-preview"
                    >
                      {{ record.resultB.outputText }}
                    </a-typography-paragraph>
                    <a-button type="link" size="small" class="view-full-btn">查看全部</a-button>
                  </div>
                  <span v-else class="no-data">-</span>
                </template>
                <template v-else-if="column.key === 'metricsB'">
                  <div v-if="record.resultB" class="metrics-cell">
                    <div>响应时间: {{ record.resultB.responseTimeMs }}ms</div>
                    <div>Token: {{ record.resultB.inputTokens }}/{{ record.resultB.outputTokens }}</div>
                    <div>成本: ${{ record.resultB.cost?.toFixed(6) || '0.000000' }}</div>
                  </div>
                  <span v-else class="no-data">-</span>
                </template>
            </template>
          </a-table>
      </a-card>
      </div>

      <div v-else-if="taskA && taskB && !loading" class="empty-state">
        <a-empty description="暂无对比数据" />
      </div>
    </a-card>

    <!-- 完整内容模态框 -->
    <a-modal
      v-model:open="modalVisible"
      :title="selectedRecord ? `${selectedTaskLabel} - ${getModelName(selectedRecord.modelName)}` : ''"
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
          <div class="modal-title">{{ getModelName(selectedRecord.modelName) }}</div>
        </div>
        <div class="modal-section">
          <h4>输入提示词</h4>
          <MarkdownRenderer :content="selectedRecord.inputPrompt" />
        </div>
        <div class="modal-section">
          <h4>输出内容</h4>
          <MarkdownRenderer :content="selectedRecord.outputText" />
        </div>
        <div v-if="selectedRecord.reasoning" class="modal-section">
          <h4>思考过程</h4>
          <MarkdownRenderer :content="selectedRecord.reasoning" />
        </div>
        <div class="modal-section">
          <h4>性能指标</h4>
          <a-descriptions :column="2" bordered>
            <a-descriptions-item label="响应时间">{{ selectedRecord.responseTimeMs }}ms</a-descriptions-item>
            <a-descriptions-item label="输入Token">{{ selectedRecord.inputTokens }}</a-descriptions-item>
            <a-descriptions-item label="输出Token">{{ selectedRecord.outputTokens }}</a-descriptions-item>
            <a-descriptions-item label="成本">${{ selectedRecord.cost?.toFixed(6) || '0.000000' }}</a-descriptions-item>
          </a-descriptions>
        </div>
      </div>
    </a-modal>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, watch, onMounted } from 'vue'
import { useRouter, useRoute } from 'vue-router'
import { message } from 'ant-design-vue'
import { listTasks, getTask, getTaskResults, type TestTask, type TestResult, type TaskQueryRequest } from '@/api/batchTestController'
import { getScene, type Scene } from '@/api/sceneController'
import MarkdownRenderer from '@/components/MarkdownRenderer.vue'

const router = useRouter()
const route = useRoute()

const taskAId = ref<string>()
const taskBId = ref<string>()
const taskA = ref<TestTask & { sceneName?: string } | null>(null)
const taskB = ref<TestTask & { sceneName?: string } | null>(null)
const resultsA = ref<TestResult[]>([])
const resultsB = ref<TestResult[]>([])
const completedTasks = ref<TestTask[]>([])
const loading = ref(false)
const compareMode = ref<'full' | 'cross-model' | 'cross-time'>('full')
const modalVisible = ref(false)
const selectedRecord = ref<TestResult | null>(null)
const selectedTaskLabel = ref('')

const compareData = computed(() => {
  if (resultsA.value.length === 0 && resultsB.value.length === 0) {
    return []
  }

  const dataMap = new Map<string, { resultA?: TestResult; resultB?: TestResult; promptTitle?: string }>()

  resultsA.value.forEach((result) => {
    const key = result.promptId
    if (!dataMap.has(key)) {
      dataMap.set(key, {})
    }
    dataMap.get(key)!.resultA = result
    dataMap.get(key)!.promptTitle = result.inputPrompt.substring(0, 50) + '...'
  })

  resultsB.value.forEach((result) => {
    const key = result.promptId
    if (!dataMap.has(key)) {
      dataMap.set(key, {})
    }
    dataMap.get(key)!.resultB = result
    if (!dataMap.get(key)!.promptTitle) {
      dataMap.get(key)!.promptTitle = result.inputPrompt.substring(0, 50) + '...'
    }
  })

  return Array.from(dataMap.entries()).map(([key, value]) => ({
    key,
    promptTitle: value.promptTitle || '-',
    resultA: value.resultA,
    resultB: value.resultB
  }))
})

const statsA = computed(() => {
  if (resultsA.value.length === 0) {
    return { totalCost: 0, avgResponseTime: 0, totalTokens: 0 }
  }
  const totalCost = resultsA.value.reduce((sum, r) => sum + (r.cost || 0), 0)
  const avgResponseTime = Math.round(
    resultsA.value.reduce((sum, r) => sum + (r.responseTimeMs || 0), 0) / resultsA.value.length
  )
  const totalTokens = resultsA.value.reduce(
    (sum, r) => sum + (r.inputTokens || 0) + (r.outputTokens || 0),
    0
  )
  return { totalCost, avgResponseTime, totalTokens }
})

const statsB = computed(() => {
  if (resultsB.value.length === 0) {
    return { totalCost: 0, avgResponseTime: 0, totalTokens: 0 }
  }
  const totalCost = resultsB.value.reduce((sum, r) => sum + (r.cost || 0), 0)
  const avgResponseTime = Math.round(
    resultsB.value.reduce((sum, r) => sum + (r.responseTimeMs || 0), 0) / resultsB.value.length
  )
  const totalTokens = resultsB.value.reduce(
    (sum, r) => sum + (r.inputTokens || 0) + (r.outputTokens || 0),
    0
  )
  return { totalCost, avgResponseTime, totalTokens }
})

const compareColumns = computed(() => {
  const baseColumns = [
    {
      title: '提示词',
      key: 'promptTitle',
      width: 200,
      fixed: 'left'
    },
    {
      title: '任务A - 模型',
      key: 'modelA',
      width: 200
    },
    {
      title: '任务A - 输出',
      key: 'outputA',
      minWidth: 300
    },
    {
      title: '任务A - 性能指标',
      key: 'metricsA',
      width: 200
    },
    {
      title: '任务B - 模型',
      key: 'modelB',
      width: 200
    },
    {
      title: '任务B - 输出',
      key: 'outputB',
      minWidth: 300
    },
    {
      title: '任务B - 性能指标',
      key: 'metricsB',
      width: 200
    }
  ]
  return baseColumns
})

const loadCompletedTasks = async () => {
  try {
    const queryRequest: TaskQueryRequest = {
      pageNum: 1,
      pageSize: 1000,
      status: 'completed'
    }
    const res = await listTasks(queryRequest)
    if (res.data?.code === 0 && res.data.data) {
      completedTasks.value = res.data.data.records || []
    }
  } catch (error) {
    console.error('加载任务列表失败:', error)
  }
}

const loadTaskA = async () => {
  if (!taskAId.value) {
    taskA.value = null
    resultsA.value = []
    return
  }
  try {
    loading.value = true
    const [taskRes, resultsRes] = await Promise.all([
      getTask(taskAId.value),
      getTaskResults(taskAId.value)
    ])

    if (taskRes.data?.code === 0 && taskRes.data.data) {
      const task = taskRes.data.data
      taskA.value = task

      if (task.sceneId) {
        try {
          const sceneRes = await getScene(task.sceneId)
          if (sceneRes.data?.code === 0 && sceneRes.data.data) {
            taskA.value.sceneName = sceneRes.data.data.name
          }
        } catch (e) {
          console.error('加载场景信息失败:', e)
        }
      }
    }

    if (resultsRes.data?.code === 0 && resultsRes.data.data) {
      let results = resultsRes.data.data || []
      
      if (compareMode.value === 'cross-model' || compareMode.value === 'cross-time') {
        if (taskA.value && taskB.value) {
          const modelsA = getModelsList(taskA.value.models)
          const modelsB = getModelsList(taskB.value.models)
          const commonModels = modelsA.filter((m) => modelsB.includes(m))
          results = results.filter((r) => commonModels.includes(r.modelName))
        }
      }
      
      resultsA.value = results
    }
  } catch (error) {
    console.error('加载任务A失败:', error)
    message.error('加载任务A失败')
  } finally {
    loading.value = false
  }
}

const loadTaskB = async () => {
  if (!taskBId.value) {
    taskB.value = null
    resultsB.value = []
    return
  }
  try {
    loading.value = true
    const [taskRes, resultsRes] = await Promise.all([
      getTask(taskBId.value),
      getTaskResults(taskBId.value)
    ])

    if (taskRes.data?.code === 0 && taskRes.data.data) {
      const task = taskRes.data.data
      taskB.value = task

      if (task.sceneId) {
        try {
          const sceneRes = await getScene(task.sceneId)
          if (sceneRes.data?.code === 0 && sceneRes.data.data) {
            taskB.value.sceneName = sceneRes.data.data.name
          }
        } catch (e) {
          console.error('加载场景信息失败:', e)
        }
      }
    }

    if (resultsRes.data?.code === 0 && resultsRes.data.data) {
      let results = resultsRes.data.data || []
      
      if (compareMode.value === 'cross-model' || compareMode.value === 'cross-time') {
        if (taskA.value && taskB.value) {
          const modelsA = getModelsList(taskA.value.models)
          const modelsB = getModelsList(taskB.value.models)
          const commonModels = modelsA.filter((m) => modelsB.includes(m))
          results = results.filter((r) => commonModels.includes(r.modelName))
        }
      }
      
      resultsB.value = results
    }
  } catch (error) {
    console.error('加载任务B失败:', error)
    message.error('加载任务B失败')
  } finally {
    loading.value = false
  }
}

const handleTaskAChange = () => {
  loadTaskA()
}

const handleTaskBChange = () => {
  loadTaskB()
}

const handleModeChange = () => {
  if (taskAId.value && taskBId.value) {
    if (compareMode.value === 'cross-model') {
      filterCrossModel()
    } else if (compareMode.value === 'cross-time') {
      filterCrossTime()
    } else {
      loadTaskA()
      loadTaskB()
    }
  }
}

const filterCrossModel = () => {
  if (!taskA.value || !taskB.value) {
    loadTaskA()
    loadTaskB()
    return
  }

  const modelsA = getModelsList(taskA.value.models)
  const modelsB = getModelsList(taskB.value.models)

  const commonModels = modelsA.filter((m) => modelsB.includes(m))

  if (commonModels.length === 0) {
    message.warning('两个任务没有共同的模型，无法进行跨模型对比')
    return
  }

  loadTaskA()
  loadTaskB()
}

const filterCrossTime = () => {
  if (!taskA.value || !taskB.value) {
    loadTaskA()
    loadTaskB()
    return
  }

  const modelsA = getModelsList(taskA.value.models)
  const modelsB = getModelsList(taskB.value.models)

  const commonModels = modelsA.filter((m) => modelsB.includes(m))

  if (commonModels.length === 0) {
    message.warning('两个任务没有共同的模型，无法进行跨时间对比')
    return
  }

  loadTaskA()
  loadTaskB()
}

const filterTaskOption = (input: string, option: any) => {
  return option.children.toLowerCase().indexOf(input.toLowerCase()) >= 0
}

const getModelsList = (modelsJson: string): string[] => {
  try {
    return JSON.parse(modelsJson)
  } catch {
    return []
  }
}

const getModelName = (modelId: string) => {
  const parts = modelId.split('/')
  return parts[parts.length - 1] || modelId
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

const getProviderIcon = (modelId: string) => {
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

const showFullContent = (record: TestResult, taskLabel: string) => {
  selectedRecord.value = record
  selectedTaskLabel.value = taskLabel
  modalVisible.value = true
}

const closeModal = () => {
  modalVisible.value = false
  selectedRecord.value = null
  selectedTaskLabel.value = ''
}

const getDiffStyle = (diff: number) => {
  if (diff > 0) {
    return { color: '#52c41a' }
  } else if (diff < 0) {
    return { color: '#ff4d4f' }
  }
  return {}
}

watch([taskAId, taskBId], () => {
  if (taskAId.value && taskBId.value && taskAId.value === taskBId.value) {
    message.warning('请选择不同的任务进行对比')
    taskBId.value = undefined
  }
})

onMounted(async () => {
  await loadCompletedTasks()

  const taskA = route.query.taskA as string
  const taskB = route.query.taskB as string

  if (taskA) {
    taskAId.value = taskA
    await loadTaskA()
  }
  if (taskB) {
    taskBId.value = taskB
    await loadTaskB()
  }
})
</script>

<style scoped>
.task-compare-page {
  padding: 24px;
  max-width: 1400px;
  margin: 0 auto;
  padding-bottom: 48px;
  min-height: 100%;
  width: 100%;
}

.task-select-section {
  margin-bottom: 24px;
}

.task-info {
  margin-top: 12px;
  padding: 8px;
  background-color: #f5f5f5;
  border-radius: 4px;
  font-size: 12px;
}

.compare-mode-section {
  margin-bottom: 16px;
  text-align: center;
}

.compare-results {
  margin-top: 24px;
}


.model-cell {
  display: flex;
  align-items: center;
  gap: 8px;
}

.model-icon {
  width: 20px;
  height: 20px;
  border-radius: 4px;
}

.model-name {
  font-weight: 500;
}

.output-cell {
  cursor: pointer;
  position: relative;
}

.output-preview {
  margin: 0;
}

.view-full-btn {
  padding: 0;
  height: auto;
}

.metrics-cell {
  font-size: 12px;
  line-height: 1.6;
}

.no-data {
  color: #999;
}

.prompt-title {
  font-weight: 500;
  max-width: 200px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.modal-content {
  padding: 16px 0;
}

.modal-header {
  display: flex;
  align-items: center;
  gap: 12px;
  margin-bottom: 24px;
  padding-bottom: 16px;
  border-bottom: 1px solid #f0f0f0;
}

.modal-icon {
  width: 32px;
  height: 32px;
  border-radius: 4px;
}

.modal-title {
  font-size: 18px;
  font-weight: 600;
}

.modal-section {
  margin-bottom: 24px;
}

.modal-section h4 {
  margin-bottom: 12px;
  font-size: 16px;
  font-weight: 600;
}

.empty-state {
  margin-top: 48px;
  text-align: center;
}
</style>
