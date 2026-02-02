<template>
  <div class="prompt-lab-page">
    <!-- 顶部栏（统一布局） -->
    <header class="top-header">
      <div class="header-content">
        <!-- 模式选择下拉框 -->
        <a-select
          v-model:value="currentMode"
          style="width: 200px; flex-shrink: 0;"
          @change="handleModeChange"
        >
          <a-select-option value="side-by-side">
            <SwapOutlined style="margin-right: 8px" />
            模型对比
          </a-select-option>
          <a-select-option value="prompt-lab">
            <ExperimentOutlined style="margin-right: 8px" />
            提示词实验
          </a-select-option>
        </a-select>

        <!-- 模型选择器 -->
        <a-select
          v-model:value="selectedModel"
          placeholder="选择模型"
          style="width: 220px; flex-shrink: 0;"
          show-search
          :options="modelOptions"
          :loading="loadingModels"
          :filter-option="false"
          @search="handleSearchModel"
        />

        <span class="vs-label">单模型多提示词对比</span>
      </div>
    </header>

    <!-- 主内容区 -->
    <div class="content-area">
      <!-- 欢迎界面 -->
      <div v-if="messages.length === 0" class="welcome-section">
        <h1 class="welcome-title">提示词实验室</h1>
        <p class="welcome-desc">测试同一个模型对不同提示词的响应效果</p>
      </div>

      <!-- 消息列表 -->
      <div v-else class="messages-wrapper" ref="messagesWrapper">
        <div v-for="(msg, idx) in messages" :key="`msg-${idx}-${msg.type}-${msg.messageIndex || 0}`" class="msg-block">
          <!-- 用户消息 - 显示多个变体 -->
          <div v-if="msg.type === 'user'" class="user-msg">
            <div
              class="user-variants"
              :style="{
                display: 'flex',
                flexDirection: 'row',
                flexWrap: 'nowrap',
                gap: '16px',
                width: '100%',
                overflowX: 'auto'
              }"
            >
        <div
                v-for="(variant, vIdx) in msg.variants"
                :key="vIdx"
                class="variant-bubble"
                :style="{
                  flex: '1 1 0%',
                  minWidth: '300px',
                  boxSizing: 'border-box'
                }"
              >
                <span class="variant-label">变体 {{ vIdx + 1 }}:</span>
                <span class="variant-content">{{ variant }}</span>
              </div>
            </div>
          </div>

          <!-- AI响应 - 显示多个变体的结果 -->
          <div v-if="msg.type === 'assistant' && msg.results" class="ai-responses-wrapper">
            <!-- 评分按钮 -->
            <div
              v-if="msg.results.length > 0 && msg.results.every(r => r.done)"
          class="rating-section"
        >
          <div class="rating-title">选择最佳变体：</div>
          <div class="rating-buttons">
            <button
                  v-for="(result, rIdx) in msg.results"
                  :key="rIdx"
              class="rating-btn variant-rating-btn"
                  :class="{ 'rating-selected': msg.rating?.winnerVariantIndex === rIdx }"
                  @click="handleVariantRating(msg, rIdx)"
            >
                  变体 {{ rIdx + 1 }}
            </button>
            <button
              class="rating-btn"
                  :class="{ 'rating-selected': msg.rating?.ratingType === 'both_bad' }"
                  @click="handleVariantRating(msg, -1)"
            >
              都不好 👎
            </button>
          </div>
        </div>

            <!-- 变体结果网格 -->
            <div
              class="results-grid"
              :style="{
                display: 'flex',
                flexDirection: 'row',
                flexWrap: 'nowrap',
                gap: '20px',
                width: '100%',
                overflowX: 'auto',
                alignItems: 'stretch'
              }"
            >
              <div
                v-for="(result, rIdx) in msg.results"
                :key="rIdx"
                class="result-card"
                :style="{
                  flex: '1 1 0%',
                  minWidth: '400px',
                  boxSizing: 'border-box'
                }"
              >
            <div class="result-header">
                  <span class="variant-badge">变体 {{ rIdx + 1 }}</span>
              <div class="stats-row">
                <span v-if="result.responseTimeMs">⏱ {{ (result.responseTimeMs / 1000).toFixed(2) }}s</span>
                <span v-if="result.totalTokens">📊 {{ result.totalTokens }}t</span>
                <span v-if="result.cost">💰 ${{ result.cost.toFixed(4) }}</span>
              </div>
            </div>

            <div class="result-content">
                  <div v-if="result.hasError" class="error-msg">{{ result.error }}</div>
              <template v-else>
                <!-- 思考过程 -->
                <details v-if="result.hasReasoning && result.reasoning" class="thinking-details" open>
                  <summary class="thinking-summary">
                    思考了 {{ result.thinkingTime || 1 }} 秒
                  </summary>
                  <div class="thinking-content">
                    <MarkdownRenderer :content="result.reasoning || ''" />
                  </div>
                </details>
                <!-- 最终回答 -->
                <MarkdownRenderer :content="result.fullContent || ''" />
              </template>

              <div v-if="!result.done && !result.hasError" class="typing-dots">
                <span></span><span></span><span></span>
                  </div>
                </div>
              </div>
            </div>
          </div>
        </div>
      </div>

      <!-- 变体编辑区域（底部） - 水平平铺布局 -->
      <div class="variants-panel">
        <div class="panel-header">
          <span class="panel-title">提示词变体 ({{ variants.length }}/5)</span>
          <div class="header-actions">
            <a-button
              size="small"
              type="primary"
              @click="showTemplateLibrary"
              style="flex-shrink: 0;"
            >
              <template #icon><AppstoreOutlined /></template>
              模板库
            </a-button>
            <a-button
              size="small"
              type="dashed"
              @click="addVariant"
              :disabled="variants.length >= 5"
              style="flex-shrink: 0;"
            >
              + 添加变体
            </a-button>
            <a-button
              v-if="variants.length > 2"
              size="small"
              type="dashed"
              danger
              @click="removeVariant(variants.length - 1)"
              style="flex-shrink: 0;"
            >
              - 移除
            </a-button>
          </div>
        </div>

        <!-- 水平平铺的提示词输入框 -->
        <div class="variants-horizontal">
          <div
            v-for="(variant, idx) in variants"
            :key="idx"
            class="variant-card"
            :class="{ 'variant-card-selected': selectedVariantIndex === idx }"
            @click="selectVariant(idx)"
          >
            <div class="variant-card-header">
              <span class="variant-label">变体 {{ idx + 1 }}</span>
              <div class="variant-actions" @click.stop>
                <a-button
                  size="small"
                  type="link"
                  :loading="optimizingIndex === idx"
                  @click="handleOptimizePrompt(idx)"
                  :disabled="!variants[idx] || variants[idx].trim() === '' || isStreaming"
                  style="padding: 0 4px; height: 24px; font-size: 12px;"
                >
                  <template #icon><ThunderboltOutlined /></template>
                  优化
                </a-button>
                <button class="expand-btn" @click="expandVariant(idx)" title="放大编辑">
                  <ExpandOutlined />
                </button>
              </div>
            </div>
            <textarea
              v-model="variants[idx]"
              placeholder="输入提示词变体..."
              :disabled="isStreaming"
              class="variant-input-inline"
              rows="3"
              @focus="selectVariant(idx)"
              @click.stop
            ></textarea>
          </div>
        </div>

        <button
          class="submit-btn"
          :class="{ disabled: !canSubmit }"
          :disabled="!canSubmit"
          @click="handleSubmit"
        >
          {{ isStreaming ? '运行中...' : '开始实验' }}
        </button>
      </div>

      <!-- 放大编辑对话框 -->
      <a-modal
        :open="expandedVariantIndex !== null"
        :title="`编辑变体 ${(expandedVariantIndex || 0) + 1}`"
        width="800px"
        @cancel="closeExpandedVariant"
        @ok="closeExpandedVariant"
      >
        <textarea
          v-if="expandedVariantIndex !== null"
          v-model="variants[expandedVariantIndex]"
          placeholder="输入提示词变体..."
          class="expanded-textarea"
          rows="15"
        ></textarea>
      </a-modal>

      <!-- 模板库抽屉 -->
      <a-drawer
        v-model:open="templateLibraryVisible"
        title="提示词模板库"
        width="700px"
        placement="right"
      >
        <div class="template-library">
          <!-- 策略筛选 -->
          <div class="strategy-filter">
            <a-radio-group v-model:value="selectedStrategy" @change="loadTemplates">
              <a-radio-button value="">全部</a-radio-button>
              <a-radio-button value="direct">直接提问</a-radio-button>
              <a-radio-button value="cot">CoT</a-radio-button>
              <a-radio-button value="role_play">角色扮演</a-radio-button>
              <a-radio-button value="few_shot">Few-shot</a-radio-button>
            </a-radio-group>
          </div>

          <!-- 模板列表 -->
          <div class="template-list" v-if="templates.length > 0">
            <div
              v-for="template in templates"
              :key="template.id"
              class="template-item"
              @click="selectTemplate(template)"
            >
              <div class="template-header">
                <div class="template-title-row">
                  <span class="template-name">{{ template.name }}</span>
                  <a-tag v-if="template.isPreset" color="blue">预设</a-tag>
                  <a-tag v-else color="green">自定义</a-tag>
                </div>
                <a-tag color="purple">{{ template.strategyName }}</a-tag>
              </div>
              <div class="template-description" v-if="template.description">
                {{ template.description }}
              </div>
              <div class="template-preview">
                {{ template.content?.substring(0, 100) }}{{ template.content && template.content.length > 100 ? '...' : '' }}
              </div>
              <div class="template-footer">
                <span class="template-usage">使用 {{ template.usageCount || 0 }} 次</span>
              </div>
            </div>
          </div>
          <a-empty v-else description="暂无模板" />

          <!-- 创建模板按钮 -->
          <div class="template-actions">
            <a-button type="dashed" block @click="showCreateTemplateModal">
              <template #icon><PlusOutlined /></template>
              创建自定义模板
            </a-button>
          </div>
        </div>
      </a-drawer>

      <!-- 创建模板对话框 -->
      <a-modal
        v-model:open="createTemplateModalVisible"
        title="创建提示词模板"
        width="700px"
        @ok="handleCreateTemplate"
        @cancel="resetCreateTemplateForm"
      >
        <a-form :model="createTemplateForm" layout="vertical">
          <a-form-item label="模板名称" required>
            <a-input v-model:value="createTemplateForm.name" placeholder="请输入模板名称" />
          </a-form-item>
          <a-form-item label="策略类型" required>
            <a-select v-model:value="createTemplateForm.strategy" placeholder="请选择策略类型">
              <a-select-option value="direct">直接提问</a-select-option>
              <a-select-option value="cot">CoT (思维链)</a-select-option>
              <a-select-option value="role_play">角色扮演</a-select-option>
              <a-select-option value="few_shot">Few-shot (示例学习)</a-select-option>
            </a-select>
          </a-form-item>
          <a-form-item label="模板描述">
            <a-textarea v-model:value="createTemplateForm.description" :rows="2" placeholder="请输入模板描述" />
          </a-form-item>
          <a-form-item label="模板内容" required>
            <a-textarea
              v-model:value="createTemplateForm.content"
              :rows="8"
              placeholder="请输入模板内容，支持使用 {变量名} 作为占位符"
            />
          </a-form-item>
          <a-form-item label="分类">
            <a-input v-model:value="createTemplateForm.category" placeholder="请输入分类（可选）" />
          </a-form-item>
        </a-form>
      </a-modal>

      <!-- 优化结果抽屉 -->
      <a-drawer
        v-model:open="optimizationDrawerVisible"
        title="提示词优化建议"
        width="600px"
        placement="right"
      >
        <div v-if="optimizationResult" class="optimization-result">
          <!-- 问题列表 -->
          <div class="optimization-section">
            <h3 class="section-title">发现的问题</h3>
            <ul class="issues-list">
              <li v-for="(issue, idx) in optimizationResult.issues" :key="idx" class="issue-item">
                {{ issue }}
              </li>
            </ul>
          </div>

          <!-- 优化后的提示词 -->
          <div class="optimization-section">
            <h3 class="section-title">优化后的提示词</h3>
            <div class="optimized-prompt-box">
              <pre class="optimized-prompt-text">{{ optimizationResult.optimizedPrompt }}</pre>
              <a-button
                type="primary"
                size="small"
                @click="applyOptimizedPrompt"
                style="margin-top: 12px;"
              >
                应用优化
              </a-button>
            </div>
          </div>

          <!-- 改进点 -->
          <div class="optimization-section">
            <h3 class="section-title">改进说明</h3>
            <ul class="improvements-list">
              <li v-for="(improvement, idx) in optimizationResult.improvements" :key="idx" class="improvement-item">
                {{ improvement }}
              </li>
            </ul>
          </div>
        </div>
        <a-empty v-else description="暂无优化结果" />
      </a-drawer>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, onMounted, onUnmounted, watch, nextTick } from 'vue'
import { useRouter, useRoute } from 'vue-router'
import { message } from 'ant-design-vue'
import { SwapOutlined, ExperimentOutlined, ExpandOutlined, ThunderboltOutlined, AppstoreOutlined, PlusOutlined } from '@ant-design/icons-vue'
import { listModels } from '@/api/modelController'
import { getConversation, getConversationMessages, type StreamChunkVO } from '@/api/conversationController'
import { createPostSSE } from '@/utils/sseClient'
import { API_BASE_URL } from '@/config/env'
import MarkdownRenderer from '@/components/MarkdownRenderer.vue'
import { addRating, getRating, getRatingsByConversationId, type RatingVO } from '@/api/ratingController'
import { optimizePrompt, type PromptOptimizationVO } from '@/api/promptOptimizationController'
import { listTemplates, createTemplate, incrementUsage, type PromptTemplateVO } from '@/api/promptTemplateController'

const router = useRouter()
const route = useRoute()
const currentMode = ref('prompt-lab')
const selectedModel = ref<string>()
const variants = ref<string[]>(['', ''])
const isStreaming = ref(false)
const loadingModels = ref(false)
const modelOptions = ref<{ label: string; value: string }[]>([])
interface PromptLabMsg {
  type: 'user' | 'assistant'
  variants?: string[]  // 用户消息的变体列表
  results?: StreamChunkVO[]  // AI响应的结果列表
  messageIndex?: number  // 消息索引
  rating?: RatingVO  // 评分信息
}

const messages = ref<PromptLabMsg[]>([])  // 消息列表
const sseController = ref<any>(null)
const expandedVariantIndex = ref<number | null>(null)
const originalVariants = ref<string[]>([])  // 会话创建时的原始变体
const messagesWrapper = ref<HTMLElement | null>(null)  // 消息列表容器引用
const optimizingIndex = ref<number | null>(null)  // 正在优化的变体索引
const optimizationDrawerVisible = ref(false)  // 优化结果抽屉显示状态
const optimizationResult = ref<PromptOptimizationVO | null>(null)  // 优化结果
const currentOptimizingVariantIndex = ref<number | null>(null)  // 当前正在优化的变体索引
const templateLibraryVisible = ref(false)  // 模板库抽屉显示状态
const templates = ref<PromptTemplateVO[]>([])  // 模板列表
const selectedStrategy = ref<string>('')  // 选中的策略类型
const createTemplateModalVisible = ref(false)  // 创建模板对话框显示状态
const createTemplateForm = ref({
  name: '',
  strategy: '',
  description: '',
  content: '',
  category: ''
})  // 创建模板表单
const selectedVariantIndex = ref<number>(0)  // 当前选中的变体索引

const handleModeChange = (mode: string) => {
  router.push(`/${mode}`)
}

const canSubmit = computed(() => {
  return (
    selectedModel.value &&
    variants.value.length >= 2 &&
    variants.value.every((v) => v.trim()) &&
    !isStreaming.value
  )
})

const loadModels = async (searchText?: string) => {
  try {
    loadingModels.value = true
    const res: any = await listModels({
      pageNum: 1,
      pageSize: 50,
      searchText: searchText || undefined
    })

    if (res.data && res.data.code === 0 && res.data.data && res.data.data.records) {
      const models = res.data.data.records
      modelOptions.value = models.map((m: any) => ({
        label: m.name,
        value: m.id,
      }))
      if (!selectedModel.value && models.length > 0) {
        selectedModel.value = models[0].id
      }
    }
  } catch (error) {
    console.error('加载模型失败:', error)
  } finally {
    loadingModels.value = false
  }
}

const handleSearchModel = (value: string) => {
  loadModels(value)
}

const addVariant = () => {
  if (variants.value.length < 5) {
    variants.value.push('')
  }
}

const removeVariant = (idx: number) => {
  if (variants.value.length > 2) {
    variants.value.splice(idx, 1)
  }
}

const handleSubmit = async () => {
  if (!canSubmit.value) return

  isStreaming.value = true

  // 始终使用当前输入的变体（用户可能已经修改了）
  const promptVariantsToUse = variants.value.filter(v => v.trim() !== '')

  // 如果是新会话，保存原始变体（用于后续轮次保持变体数量一致）
  if (!route.query.conversationId && originalVariants.value.length === 0) {
    originalVariants.value = [...promptVariantsToUse]
  }

  // 添加用户消息
  messages.value.push({
    type: 'user',
    variants: [...promptVariantsToUse]
  })

  // 添加AI响应占位
  const assistantMsgIndex = messages.value.length
  const initialResults = promptVariantsToUse.map((_, idx) => ({
      variantIndex: idx,
      fullContent: '',
      done: false,
      hasError: false,
    }))

  messages.value.push({
    type: 'assistant',
    results: initialResults
  })

  // 滚动到底部
  scrollToBottom()

  try {
    const conversationId = route.query.conversationId as string
    const url = `${API_BASE_URL}/conversation/prompt-lab/stream`
    sseController.value = await createPostSSE(
      url,
      {
        model: selectedModel.value,
        promptVariants: promptVariantsToUse,
        conversationId: conversationId || undefined,
        stream: true,
      },
      {
        onMessage: (chunk: StreamChunkVO) => {
          console.log('📨 Prompt Lab收到:', chunk.variantIndex, chunk.fullContent?.substring(0, 20))

          // 如果是新会话，保存conversationId到URL
          if (chunk.conversationId && !route.query.conversationId) {
            console.log('💾 保存新会话ID到URL:', chunk.conversationId)
            router.replace({
              path: '/prompt-lab',
              query: { conversationId: chunk.conversationId }
            })
          }

          // 更新AI响应消息（从messages数组中重新获取，确保获取最新状态）
          const assistantMsg = messages.value[assistantMsgIndex]
          if (assistantMsg && assistantMsg.results && chunk.variantIndex !== undefined) {
            const idx = chunk.variantIndex
            if (assistantMsg.results[idx]) {
              assistantMsg.results[idx] = { ...assistantMsg.results[idx], ...chunk }
              // 如果chunk中包含messageIndex，更新消息的messageIndex
              if (chunk.messageIndex !== undefined) {
                assistantMsg.messageIndex = chunk.messageIndex
              }
              // 强制响应式更新
              messages.value = [...messages.value]
            }
          }
        },
        onError: () => {
          isStreaming.value = false
          message.error('实验失败')
        },
        onBusinessError: (data) => {
          isStreaming.value = false
          message.error(data.message || '请求过于频繁，请稍后再试')
        },
        onComplete: () => {
          isStreaming.value = false

          // 加载评分
          const assistantMsg = messages.value[assistantMsgIndex]
          if (assistantMsg && assistantMsg.messageIndex !== undefined) {
          const conversationId = route.query.conversationId as string
          if (conversationId) {
              loadRatingForMessage(conversationId, assistantMsg)
          }
          }

          // 流式传输完成后，不需要重新加载历史会话
          // 因为消息已经在流式传输过程中实时更新了
          // 重新加载会导致布局变化，所以注释掉
          // const conversationId = route.query.conversationId as string
          // if (conversationId && messages.value.length > 0) {
          //   setTimeout(() => {
          //     loadConversationHistory(false)
          //   }, 500)
          // }

          // 滚动到底部
          scrollToBottom()
        },
      }
    )
  } catch (error) {
    isStreaming.value = false
    console.error('提交失败:', error)
    message.error('提交失败: ' + (error instanceof Error ? error.message : '未知错误'))
  }
}

// 处理变体评分
const handleVariantRating = async (msg: PromptLabMsg, variantIndex: number) => {
  const conversationId = route.query.conversationId as string
  if (!conversationId || !msg.messageIndex) {
    message.warning('请先开始实验')
    return
  }

  try {
    const ratingType = variantIndex === -1 ? 'both_bad' : `variant_${variantIndex}`

    const res: any = await addRating({
      conversationId,
      messageIndex: msg.messageIndex,
      ratingType,
      winnerVariantIndex: variantIndex === -1 ? undefined : variantIndex,
    })

    if (res.data && res.data.code === 0) {
      // 直接使用本地数据更新，避免额外请求
      msg.rating = {
        id: '',
        conversationId,
        messageIndex: msg.messageIndex!,
        userId: 0,
        ratingType,
        winnerVariantIndex: variantIndex === -1 ? undefined : variantIndex,
        createTime: new Date().toISOString()
      }
      // 强制响应式更新
      messages.value = [...messages.value]
      message.success('评分成功')
    }
  } catch (error) {
    console.error('评分失败:', error)
    message.error('评分失败')
  }
}

// 加载消息的评分（单个消息，用于按需加载）
const loadRatingForMessage = async (conversationId: string, msg: PromptLabMsg) => {
  if (!msg.messageIndex) return

  try {
    const res: any = await getRating(conversationId, msg.messageIndex)
    if (res.data && res.data.code === 0 && res.data.data) {
      msg.rating = res.data.data
      // 强制响应式更新
      messages.value = [...messages.value]
    } else {
      msg.rating = undefined
      messages.value = [...messages.value]
    }
  } catch (error) {
    console.error('加载评分失败:', error)
    msg.rating = undefined
    messages.value = [...messages.value]
  }
}

// 批量加载所有评分（用于加载历史会话时）
const loadRatings = async (conversationId: string) => {
  try {
    const res: any = await getRatingsByConversationId(conversationId)
    if (res.data && res.data.code === 0 && res.data.data) {
      const ratings = res.data.data as RatingVO[]
      // 创建评分映射表，以messageIndex为key
      const ratingMap = new Map<number, RatingVO>()
      ratings.forEach(rating => {
        ratingMap.set(rating.messageIndex, rating)
      })

      // 更新所有assistant消息的评分
      for (let i = 0; i < messages.value.length; i++) {
        const msg = messages.value[i]
        if (msg.type === 'assistant' && msg.messageIndex !== undefined) {
          const rating = ratingMap.get(msg.messageIndex)
          if (rating) {
            messages.value[i] = {
              ...messages.value[i],
              rating: rating
            }
          }
        }
      }
      // 触发响应式更新
      messages.value = [...messages.value]
    }
  } catch (error) {
    console.error('加载评分失败:', error)
  }
}

// 滚动到底部
const scrollToBottom = async () => {
  await nextTick()
  if (messagesWrapper.value) {
    messagesWrapper.value.scrollTo({
      top: messagesWrapper.value.scrollHeight,
      behavior: 'smooth'
    })
  }
}

// 加载当前评分
const switchToHistory = (messageIndex: number) => {
  const history = conversationHistory.value.find(h => h.messageIndex === messageIndex)
  if (history) {
    experimentResults.value = [...history.results]
    currentMessageIndex.value = messageIndex
    // 加载该轮次的评分
    const conversationId = route.query.conversationId as string
    if (conversationId) {
      loadCurrentRating(conversationId, messageIndex)
    }
  }
}

const loadCurrentRating = async (conversationId: string, messageIndex: number) => {
  try {
    const res: any = await getRating(conversationId, messageIndex)
    if (res.data && res.data.code === 0 && res.data.data) {
      currentRating.value = res.data.data
    } else {
      currentRating.value = null
    }
  } catch (error) {
    console.error('加载评分失败:', error)
    currentRating.value = null
  }
}

const getPromptPreview = (text: string) => {
  if (!text) return '未输入提示词'
  return text.length > 80 ? text.substring(0, 80) + '...' : text
}

// 放大编辑提示词
const expandVariant = (index: number) => {
  expandedVariantIndex.value = index
}

// 关闭放大编辑
const closeExpandedVariant = () => {
  expandedVariantIndex.value = null
}

// 优化提示词
const handleOptimizePrompt = async (variantIndex: number) => {
  const prompt = variants.value[variantIndex]
  if (!prompt || prompt.trim() === '') {
    message.warning('请先输入提示词')
    return
  }

  optimizingIndex.value = variantIndex
  currentOptimizingVariantIndex.value = variantIndex
  optimizationResult.value = null

  try {
    // 查找对应的AI回答（如果有）
    let aiResponse: string | undefined = undefined
    if (messages.value.length > 0) {
      const lastAssistantMsg = messages.value
        .filter(m => m.type === 'assistant')
        .pop()
      if (lastAssistantMsg && lastAssistantMsg.results && lastAssistantMsg.results[variantIndex]) {
        aiResponse = lastAssistantMsg.results[variantIndex].fullContent
      }
    }

    const res: any = await optimizePrompt({
      originalPrompt: prompt,
      aiResponse: aiResponse
    })

    if (res.data && res.data.code === 0 && res.data.data) {
      optimizationResult.value = res.data.data
      optimizationDrawerVisible.value = true
      message.success('优化分析完成')
    } else {
      message.error(res.data?.message || '优化失败')
    }
  } catch (error) {
    console.error('优化提示词失败:', error)
    message.error('优化失败: ' + (error instanceof Error ? error.message : '未知错误'))
  } finally {
    optimizingIndex.value = null
  }
}

// 应用优化后的提示词
const applyOptimizedPrompt = () => {
  if (optimizationResult.value && currentOptimizingVariantIndex.value !== null) {
    variants.value[currentOptimizingVariantIndex.value] = optimizationResult.value.optimizedPrompt
    message.success('已应用优化后的提示词')
    optimizationDrawerVisible.value = false
  }
}

// 选中变体
const selectVariant = (index: number) => {
  selectedVariantIndex.value = index
}

// 显示模板库
const showTemplateLibrary = () => {
  templateLibraryVisible.value = true
  loadTemplates()
  // 如果没有选中的变体，默认选中第一个
  if (selectedVariantIndex.value === null || selectedVariantIndex.value < 0 || selectedVariantIndex.value >= variants.value.length) {
    selectedVariantIndex.value = 0
  }
}

// 加载模板列表
const loadTemplates = async () => {
  try {
    const res: any = await listTemplates({
      strategy: selectedStrategy.value || undefined
    })
    if (res.data && res.data.code === 0 && res.data.data) {
      templates.value = res.data.data
    }
  } catch (error) {
    console.error('加载模板列表失败:', error)
    message.error('加载模板列表失败')
  }
}

// 选择模板
const selectTemplate = async (template: PromptTemplateVO) => {
  if (!template.content) {
    message.warning('模板内容为空')
    return
  }

  try {
    // 增加使用次数
    if (template.id) {
      await incrementUsage(template.id)
    }

    // 应用到选中的变体，如果没有选中或索引无效，默认应用到第一个
    const targetIndex = (selectedVariantIndex.value !== null &&
      selectedVariantIndex.value >= 0 &&
      selectedVariantIndex.value < variants.value.length)
      ? selectedVariantIndex.value
      : 0

    // 应用模板内容
    variants.value[targetIndex] = template.content

    message.success(`模板已应用到变体 ${targetIndex + 1}`)
    templateLibraryVisible.value = false
  } catch (error) {
    console.error('应用模板失败:', error)
    message.error('应用模板失败')
  }
}

// 显示创建模板对话框
const showCreateTemplateModal = () => {
  createTemplateModalVisible.value = true
  resetCreateTemplateForm()
}

// 重置创建模板表单
const resetCreateTemplateForm = () => {
  createTemplateForm.value = {
    name: '',
    strategy: '',
    description: '',
    content: '',
    category: ''
  }
}

// 创建模板
const handleCreateTemplate = async () => {
  if (!createTemplateForm.value.name || !createTemplateForm.value.strategy || !createTemplateForm.value.content) {
    message.warning('请填写必填项')
    return
  }

  try {
    const res: any = await createTemplate({
      name: createTemplateForm.value.name,
      strategy: createTemplateForm.value.strategy,
      description: createTemplateForm.value.description,
      content: createTemplateForm.value.content,
      category: createTemplateForm.value.category
    })

    if (res.data && res.data.code === 0) {
      message.success('模板创建成功')
      createTemplateModalVisible.value = false
      resetCreateTemplateForm()
      loadTemplates()
    } else {
      message.error(res.data?.message || '创建模板失败')
    }
  } catch (error) {
    console.error('创建模板失败:', error)
    message.error('创建模板失败')
  }
}

// 加载历史会话
const loadConversationHistory = async (skipIfStreaming: boolean = true) => {
  const conversationId = route.query.conversationId as string
  if (!conversationId) return

  // 如果正在流式传输且要求跳过，则不加载
  if (skipIfStreaming && isStreaming.value) {
    console.log('⏸️ 正在流式传输，跳过加载历史会话')
    return
  }

  try {
    console.log('📡 加载提示词实验会话:', conversationId)

    // 获取会话详情
    const conversationRes: any = await getConversation({ conversationId })
    if (conversationRes.data && conversationRes.data.code === 0 && conversationRes.data.data) {
      const conversation = conversationRes.data.data

      // 检查会话类型，如果是side_by_side，跳转到对应页面
      if (conversation.conversationType === 'side_by_side') {
        console.log('🔄 检测到模型对比会话，跳转到side-by-side页面')
        router.replace(`/side-by-side?conversationId=${conversationId}`)
        return
      }

      // 解析models获取单个模型
      let modelsList = conversation.models
      if (typeof modelsList === 'string') {
        modelsList = JSON.parse(modelsList)
      }
      if (modelsList && modelsList.length > 0) {
        selectedModel.value = modelsList[0]
      }
    }

    // 获取消息历史
    const messagesRes: any = await getConversationMessages({ conversationId })
    console.log('📡 消息接口响应:', messagesRes)
    if (messagesRes.data && messagesRes.data.code === 0 && messagesRes.data.data) {
      const historyMessages = messagesRes.data.data
      console.log('📨 获取到历史消息数量:', historyMessages.length)
      console.log('📨 历史消息详情:', historyMessages.map((m: any) => ({
        role: m.role,
        messageIndex: m.messageIndex,
        content: m.content?.substring(0, 50),
        variantIndex: m.variantIndex
      })))

      // 提取第一轮的提示词变体（会话创建时的原始变体）
      // 在Prompt Lab中，每个变体都会创建一个用户消息，messageIndex是连续的
      // 第一轮的所有变体应该是前N个连续的用户消息，其中N是变体数量
      const userMessages = historyMessages
        .filter((m: any) => m.role === 'user')
        .sort((a: any, b: any) => (a.messageIndex || 0) - (b.messageIndex || 0))

      if (userMessages.length > 0) {
        // 提取第一轮的所有变体
        // 第一轮的特征：所有消息都包含"变体X: "格式，且变体索引从0开始连续
        const firstRoundVariants: string[] = []
        let expectedVariantIndex = 0

        for (const msg of userMessages) {
          const content = msg.content || ''
          // 检查是否是变体格式（包含"变体X: "前缀）
          const variantMatch = content.match(/^变体(\d+):\s*(.+)$/)
          if (variantMatch) {
            const variantIndex = parseInt(variantMatch[1], 10)
            const variantContent = variantMatch[2]

            // 如果是第一个变体（索引为0），开始收集第一轮
            if (variantIndex === 0 && firstRoundVariants.length === 0) {
              firstRoundVariants.push(variantContent)
              expectedVariantIndex = 1
            }
            // 如果当前变体索引等于期望的索引，说明是第一轮的连续变体
            else if (variantIndex === expectedVariantIndex && firstRoundVariants.length > 0) {
              firstRoundVariants.push(variantContent)
              expectedVariantIndex++
            }
            // 如果变体索引不连续，说明第一轮已经结束
            else if (firstRoundVariants.length > 0) {
              break
            }
          } else {
            // 如果遇到非变体格式的消息，说明第一轮已经结束
            if (firstRoundVariants.length > 0) {
              break
            }
          }
        }

        if (firstRoundVariants.length > 0) {
          // 保存原始变体
          originalVariants.value = [...firstRoundVariants]
          // 设置当前变体（用于显示）
          variants.value = [...firstRoundVariants]
          console.log('📝 提取到原始变体:', firstRoundVariants.length, '个')
        }
      }

      // 按messageIndex分组，构建消息列表
      const allMessages = historyMessages.filter((m: any) => m.role === 'user' || m.role === 'assistant')
      console.log('📋 过滤后的消息数量:', allMessages.length, '用户消息:', allMessages.filter((m: any) => m.role === 'user').length, '助手消息:', allMessages.filter((m: any) => m.role === 'assistant').length)
      const messagesByIndex = new Map<number, { users: any[], assistants: any[] }>()

      allMessages.forEach((m: any) => {
        const msgIndex = m.messageIndex || 0
        if (!messagesByIndex.has(msgIndex)) {
          messagesByIndex.set(msgIndex, { users: [], assistants: [] })
        }
        const entry = messagesByIndex.get(msgIndex)!
        if (m.role === 'user') {
          entry.users.push(m)
        } else {
          entry.assistants.push(m)
        }
      })

      console.log('📊 消息分组结果:', Array.from(messagesByIndex.entries()).map(([idx, entry]) => ({
        messageIndex: idx,
        userCount: entry.users.length,
        assistantCount: entry.assistants.length
      })))

      // 构建消息列表
      const sortedIndexes = Array.from(messagesByIndex.keys()).sort((a, b) => a - b)
      const newMessages: PromptLabMsg[] = []

      // 记录最后一轮的变体数量（用于设置输入框）
      let lastRoundVariantCount = 0

      for (const msgIndex of sortedIndexes) {
        const entry = messagesByIndex.get(msgIndex)!

        // 添加用户消息
        // 对于同一轮次的所有用户消息，提取所有变体
        let currentRoundVariantCount = 0
        if (entry.users.length > 0) {
          const userVariants: string[] = []
          const variantMap = new Map<number, string>() // 用于按索引排序

          console.log('🔍 处理用户消息，数量:', entry.users.length, 'messageIndex:', msgIndex)
          console.log('🔍 所有用户消息详情:', entry.users.map((m: any, idx: number) => ({
            index: idx,
            content: m.content,
            messageIndex: m.messageIndex,
            variantIndex: m.variantIndex
          })))

          entry.users.forEach((m: any, idx: number) => {
          const content = m.content || ''
            console.log(`  📝 用户消息[${idx}]:`, content)

            // 匹配"变体X:内容"或"变体X: 内容"（冒号后可能有空格）
            // 使用更宽松的正则，允许前后有空白字符，并且匹配整个字符串
            const match = content.match(/^变体\s*(\d+)\s*:\s*(.+)$/s)
            if (match) {
              const variantIndex = parseInt(match[1], 10)
              const variantContent = match[2].trim()
              variantMap.set(variantIndex, variantContent)
              console.log(`  ✅ 匹配到变体 ${variantIndex}:`, variantContent)
            } else {
              console.log(`  ⚠️ 未匹配到变体格式，原始内容:`, content)
              // 如果没有匹配到变体格式，尝试使用variantIndex（如果存在）
              if (m.variantIndex !== undefined && m.variantIndex !== null) {
                variantMap.set(m.variantIndex, content.trim())
                console.log(`  ✅ 使用variantIndex ${m.variantIndex}:`, content.trim())
              } else if (content.trim()) {
                // 最后回退：使用数组索引
                variantMap.set(idx, content.trim())
                console.log(`  ✅ 使用数组索引 ${idx}:`, content.trim())
              }
            }
          })

          // 按变体索引排序
          const sortedVariants = Array.from(variantMap.entries())
            .sort((a, b) => a[0] - b[0])
            .map(([_, content]) => content)

          currentRoundVariantCount = sortedVariants.length
          // 更新最后一轮的变体数量
          lastRoundVariantCount = sortedVariants.length
          console.log('📊 提取到的变体:', sortedVariants.length, '个', sortedVariants)

          if (sortedVariants.length > 0) {
            newMessages.push({
              type: 'user',
              variants: sortedVariants,
              messageIndex: msgIndex
        })
            console.log('✅ 添加用户消息，变体数量:', sortedVariants.length, 'messageIndex:', msgIndex, '变体:', sortedVariants)
          }
      }

        // 添加AI响应
        if (entry.assistants.length > 0) {
          console.log('🔍 处理AI响应，数量:', entry.assistants.length, 'messageIndex:', msgIndex)
          entry.assistants.forEach((m: any, idx: number) => {
            console.log(`  📝 AI响应[${idx}]: variantIndex=${m.variantIndex}, content长度=${(m.content || '').length}`)
          })

          const results = entry.assistants
            .map((m: any) => ({
          variantIndex: m.variantIndex,
          fullContent: m.content,
          done: true,
          hasError: false,
          responseTimeMs: m.responseTimeMs,
          inputTokens: m.inputTokens,
          outputTokens: m.outputTokens,
          totalTokens: (m.inputTokens || 0) + (m.outputTokens || 0),
          cost: m.cost,
          reasoning: m.reasoning,
          hasReasoning: !!m.reasoning,
          thinkingTime: m.reasoning ? Math.max(1, Math.min(Math.ceil(m.reasoning.length / 200), 60)) : undefined
        }))
            .sort((a, b) => (a.variantIndex || 0) - (b.variantIndex || 0))

          console.log('📊 排序后的AI响应:', results.length, '个', results.map(r => `variantIndex=${r.variantIndex}`))

          // 使用当前轮次的变体数量，而不是原始变体数量
          // 这样即使第一轮只有1个变体，后续轮次有2个变体时也能正确显示
          const finalResults = currentRoundVariantCount > 0
            ? results.slice(0, currentRoundVariantCount)
            : results

          console.log('📊 最终AI响应数量:', finalResults.length, '当前轮次变体数量:', currentRoundVariantCount)

          if (finalResults.length > 0) {
            const assistantMsg: PromptLabMsg = {
              type: 'assistant',
              results: finalResults,
              messageIndex: msgIndex
            }

            newMessages.push(assistantMsg)
            console.log('✅ 添加AI响应，变体数量:', finalResults.length, 'messageIndex:', msgIndex)
          }
        }
      }

      // 一次性更新消息列表，避免中间状态导致页面闪烁
      messages.value = newMessages

      // 批量加载所有评分
      await loadRatings(conversationId)

      // 使用最后一轮的变体数量设置输入框（如果最后一轮有变体）
      if (lastRoundVariantCount > 0) {
        // 如果当前输入框数量与最后一轮不一致，更新输入框
        if (variants.value.length !== lastRoundVariantCount) {
          // 获取最后一轮的用户消息变体内容
          const lastUserMsg = newMessages.filter(m => m.type === 'user').pop()
          if (lastUserMsg && lastUserMsg.variants) {
            variants.value = [...lastUserMsg.variants]
            console.log('📝 更新输入框变体数量:', variants.value.length, '个，内容:', variants.value)
          } else {
            // 如果没有用户消息，使用空字符串填充
            variants.value = Array(lastRoundVariantCount).fill('')
            console.log('📝 更新输入框变体数量:', variants.value.length, '个（空内容）')
          }
        }
      }

      console.log('✅ 最终消息列表数量:', messages.value.length)
      console.log('📝 消息列表详情:', messages.value.map(m => ({
        type: m.type,
        messageIndex: m.messageIndex,
        variantsCount: m.variants?.length,
        resultsCount: m.results?.length
      })))

      // 滚动到底部
      scrollToBottom()
    } else {
      console.warn('⚠️ 没有获取到消息数据，响应:', messagesRes)
    }
  } catch (error) {
    console.error('❌ 加载历史会话失败:', error)
  }
}

// 监听conversationId变化
watch(() => route.query.conversationId, (newId, oldId) => {
  // 只有当conversationId真正变化时才重新加载（避免发送新消息时重复加载）
  // 如果正在流式传输，不要重新加载，避免清空正在显示的消息
  if (newId && newId !== oldId && !isStreaming.value) {
    console.log('🔄 conversationId变化，加载历史会话:', oldId, '→', newId)
    loadConversationHistory()
  } else if (!newId) {
    // 新对话，清空状态
    variants.value = ['', '']
    originalVariants.value = []
    messages.value = []
  } else if (newId && newId !== oldId && isStreaming.value) {
    console.log('⏸️ 正在流式传输，延迟加载历史会话')
    // 延迟加载，等流式传输完成后再加载
    // 这个逻辑会在onComplete中处理
  }
}, { immediate: true })

onMounted(() => {
  loadModels()
  // 如果URL中已经有conversationId，立即加载
  if (route.query.conversationId) {
  loadConversationHistory()
  }
})

onUnmounted(() => {
  if (sseController.value) {
    sseController.value.close()
  }
})
</script>

<style scoped>
.prompt-lab-page {
  height: 100%;
  display: flex;
  flex-direction: column;
  background: #fff;
}

.top-header {
  padding: 14px 24px;
  border-bottom: 1px solid #f0f0f0;
  background: #fff;
}

.header-content {
  display: flex;
  align-items: center;
  justify-content: flex-start;
  gap: 16px;
  overflow-x: auto;
  overflow-y: hidden;
  padding: 0 24px;
  flex-wrap: nowrap;
  scrollbar-width: none;
  -ms-overflow-style: none;
}

.header-content::-webkit-scrollbar {
  display: none;
}

.vs-label {
  font-size: 14px;
  color: #999;
  font-weight: 500;
  flex-shrink: 0;
  white-space: nowrap;
}

.content-area {
  flex: 1;
  display: flex;
  flex-direction: column;
  overflow: hidden;
}

.welcome-section {
  flex: 1;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  padding-bottom: 100px;
}

.welcome-title {
  font-size: 36px;
  font-weight: 400;
  color: #6b7280;
  margin: 0 0 12px 0;
}

.welcome-desc {
  font-size: 15px;
  color: #9ca3af;
  margin: 0;
}

.results-section {
  flex: 1;
  overflow-y: auto;
  padding: 24px 24px;
}

/* 水平布局展示AI回答（参考模型对比样式） */
.results-grid {
  display: flex !important;
  flex-direction: row !important;
  flex-wrap: nowrap !important;
  gap: 20px;
  width: 100%;
  overflow-x: auto;
  align-items: stretch;
  scrollbar-width: none;
  -ms-overflow-style: none;
}

.results-grid::-webkit-scrollbar {
  display: none;
}

.result-card {
  flex: 1 1 0% !important;
  min-width: 400px !important;
  background: #ffffff;
  border: 1px solid #e5e7eb;
  border-radius: 12px;
  padding: 18px;
  min-height: 200px;
  box-sizing: border-box;
  display: flex;
  flex-direction: column;
  box-shadow: 0 1px 3px rgba(0, 0, 0, 0.06);
}

.result-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 12px;
  padding-bottom: 10px;
  border-bottom: 1px solid #ddd;
  flex-shrink: 0;
}

.variant-badge {
  font-size: 13px;
  font-weight: 600;
  color: #1f2937;
  background: #fff;
  padding: 4px 10px;
  border-radius: 6px;
  border: 1px solid #e5e7eb;
}

.stats-row {
  display: flex;
  gap: 10px;
  font-size: 11px;
  color: #6b7280;
}

.stats-row span {
  background: #fff;
  padding: 2px 8px;
  border-radius: 4px;
}

.prompt-preview {
  font-size: 12px;
  color: #6b7280;
  padding: 8px 10px;
  background: #f9fafb;
  border-radius: 6px;
  margin-bottom: 12px;
  max-height: 60px;
  overflow: hidden;
  border: 1px solid #f3f4f6;
}

.result-content {
  font-size: 14px;
  line-height: 1.7;
  flex: 1;
  overflow-y: auto;
}

.result-text {
  color: #1f2937;
}

.error-msg {
  color: #dc2626;
  font-size: 13px;
}

.typing-dots {
  display: flex;
  gap: 4px;
  padding: 8px 0;
}

.typing-dots span {
  width: 6px;
  height: 6px;
  background: #9ca3af;
  border-radius: 50%;
  animation: bounce 1.4s infinite;
}

.typing-dots span:nth-child(2) {
  animation-delay: 0.2s;
}

.typing-dots span:nth-child(3) {
  animation-delay: 0.4s;
}

.variants-panel {
  border-top: 1px solid #f0f0f0;
  padding: 20px 24px;
  background: #fafafa;
}

.panel-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 16px;
}

.panel-title {
  font-size: 14px;
  font-weight: 600;
  color: #374151;
}

.header-actions {
  display: flex;
  gap: 8px;
}

/* 水平平铺布局 */
.variants-horizontal {
  display: flex;
  gap: 16px;
  margin-bottom: 16px;
  overflow-x: auto;
  scrollbar-width: none;
  -ms-overflow-style: none;
  padding-bottom: 4px;
}

.variants-horizontal::-webkit-scrollbar {
  display: none;
}

.variant-card {
  flex: 1;
  min-width: 280px;
  max-width: 400px;
  background: #fff;
  border: 1px solid #e5e7eb;
  border-radius: 8px;
  padding: 12px;
  display: flex;
  flex-direction: column;
  cursor: pointer;
  transition: all 0.2s;
}

.variant-card:hover {
  border-color: #1890ff;
  box-shadow: 0 2px 8px rgba(24, 144, 255, 0.1);
}

.variant-card-selected {
  border-color: #1890ff;
  border-width: 2px;
  background: #f0f7ff;
  box-shadow: 0 2px 8px rgba(24, 144, 255, 0.2);
}

.variant-card-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 8px;
}

.variant-actions {
  display: flex;
  align-items: center;
  gap: 4px;
}

.variant-label {
  font-size: 12px;
  font-weight: 600;
  color: #1f2937;
}

.expand-btn {
  width: 24px;
  height: 24px;
  border: none;
  background: transparent;
  color: #9ca3af;
  cursor: pointer;
  border-radius: 4px;
  display: flex;
  align-items: center;
  justify-content: center;
  transition: all 0.2s;
}

.expand-btn:hover {
  background: #f3f4f6;
  color: #374151;
}

.variant-input-inline {
  width: 100%;
  border: 1px solid #e5e7eb;
  border-radius: 6px;
  padding: 8px 12px;
  font-size: 13px;
  line-height: 1.5;
  resize: none;
  font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', sans-serif;
  flex: 1;
}

.variant-input-inline:focus {
  outline: none;
  border-color: #1890ff;
  box-shadow: 0 0 0 2px rgba(24, 144, 255, 0.1);
}

.variant-input-inline:disabled {
  background: #f9fafb;
  cursor: not-allowed;
}

/* 放大编辑的textarea */
.expanded-textarea {
  width: 100%;
  border: 1px solid #e5e7eb;
  border-radius: 6px;
  padding: 12px;
  font-size: 14px;
  line-height: 1.6;
  resize: none;
  font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', sans-serif;
}

.expanded-textarea:focus {
  outline: none;
  border-color: #1890ff;
  box-shadow: 0 0 0 2px rgba(24, 144, 255, 0.1);
}

.submit-btn {
  width: 50%;
  margin: 0 auto;
  padding: 12px;
  background: #1f2937;
  color: #fff;
  border: none;
  border-radius: 8px;
  font-size: 14px;
  font-weight: 500;
  cursor: pointer;
  transition: background 0.15s;
  display: block;
}

.submit-btn:hover:not(.disabled) {
  background: #374151;
}

.submit-btn.disabled {
  background: #d1d5db;
  cursor: not-allowed;
}

@keyframes bounce {
  0%,
  60%,
  100% {
    transform: translateY(0);
  }
  30% {
    transform: translateY(-6px);
  }
}

/* 思考过程样式 */
.thinking-details {
  background: #f7f7f8;
  border: none;
  border-radius: 6px;
  padding: 10px 12px;
  margin-bottom: 12px;
}

.thinking-summary {
  cursor: pointer;
  font-weight: 500;
  color: #6b7280;
  font-size: 12px;
  user-select: none;
  display: flex;
  align-items: center;
  list-style: none;
}

.thinking-summary::-webkit-details-marker {
  display: none;
}

.thinking-summary::before {
  content: '▶';
  display: inline-block;
  margin-right: 8px;
  transition: transform 0.2s;
  font-size: 10px;
}

.thinking-details[open] .thinking-summary::before {
  transform: rotate(90deg);
}

.thinking-summary:hover {
  color: #374151;
}

.thinking-content {
  margin-top: 10px;
  font-size: 12px;
  line-height: 1.6;
  max-height: 200px;
  overflow-y: auto;
}

/* 评分区域样式 */
.rating-section {
  margin: 0 0 24px 0;
  padding: 16px 20px;
  background: #fafafa;
  border-radius: 12px;
  border: 1px solid #e5e7eb;
}

.rating-title {
  font-size: 13px;
  font-weight: 600;
  color: #374151;
  margin-bottom: 12px;
}

.rating-buttons {
  display: flex;
  gap: 10px;
  flex-wrap: wrap;
}

.rating-btn {
  padding: 8px 16px;
  background: #ffffff;
  border: 1.5px solid #d1d5db;
  border-radius: 8px;
  font-size: 13px;
  font-weight: 500;
  color: #374151;
  cursor: pointer;
  transition: all 0.2s;
  white-space: nowrap;
}

.rating-btn:hover {
  background: #f3f4f6;
  border-color: #9ca3af;
  transform: translateY(-1px);
}

.rating-btn:active {
  transform: translateY(0);
}

.rating-btn.rating-selected {
  background: #1890ff;
  border-color: #1890ff;
  color: #ffffff;
  box-shadow: 0 2px 8px rgba(24, 144, 255, 0.3);
}

.rating-btn.rating-selected:hover {
  background: #40a9ff;
  border-color: #40a9ff;
}

.variant-rating-btn {
  min-width: 80px;
}

.history-section {
  margin-bottom: 20px;
  padding: 16px;
  background: #f9fafb;
  border-radius: 8px;
}

.history-title {
  font-size: 14px;
  font-weight: 600;
  color: #374151;
  margin-bottom: 12px;
}

.history-list {
  display: flex;
  gap: 8px;
  flex-wrap: wrap;
}

.history-item {
  padding: 8px 16px;
  background: #fff;
  border: 1px solid #e5e7eb;
  border-radius: 6px;
  cursor: pointer;
  font-size: 13px;
  color: #6b7280;
  transition: all 0.2s;
}

.history-item:hover {
  border-color: #3b82f6;
  color: #3b82f6;
}

.history-item.history-active {
  background: #3b82f6;
  border-color: #3b82f6;
  color: #fff;
}

/* 消息列表样式 */
.messages-wrapper {
  flex: 1;
  overflow-y: auto;
  padding: 20px;
  max-height: calc(100vh - 300px);
}

.msg-block {
  margin-bottom: 24px;
}

.user-msg {
  margin-bottom: 16px;
}

.user-variants {
  display: flex !important;
  flex-direction: row !important;
  flex-wrap: nowrap !important;
  gap: 16px;
  width: 100%;
  overflow-x: auto;
  scrollbar-width: none;
  -ms-overflow-style: none;
}

.user-variants::-webkit-scrollbar {
  display: none;
}

.variant-bubble {
  flex: 1 1 0% !important;
  min-width: 300px !important;
  background: #ffffff;
  color: #1f2937;
  padding: 16px;
  border-radius: 12px;
  font-size: 14px;
  line-height: 1.6;
  box-shadow: 0 1px 3px rgba(0, 0, 0, 0.06);
  border: 1px solid #e5e7eb;
  display: flex;
  flex-direction: column;
  gap: 8px;
  box-sizing: border-box;
}

.variant-label {
  font-weight: 600;
  font-size: 13px;
  color: #6b7280;
}

.variant-content {
  word-break: break-word;
  white-space: pre-wrap;
  color: #1f2937;
}

.ai-responses-wrapper {
  margin-top: 16px;
}


.typing-dots span {
  width: 8px;
  height: 8px;
  background: #9ca3af;
  border-radius: 50%;
  animation: bounce 1.4s infinite ease-in-out;
}

.typing-dots span:nth-child(1) {
  animation-delay: -0.32s;
}

.typing-dots span:nth-child(2) {
  animation-delay: -0.16s;
}

/* 优化结果样式 */
.optimization-result {
  padding: 0;
}

.optimization-section {
  margin-bottom: 24px;
}

.section-title {
  font-size: 16px;
  font-weight: 600;
  color: #1f2937;
  margin: 0 0 12px 0;
  padding-bottom: 8px;
  border-bottom: 2px solid #e5e7eb;
}

.issues-list,
.improvements-list {
  list-style: none;
  padding: 0;
  margin: 0;
}

.issue-item,
.improvement-item {
  padding: 10px 12px;
  margin-bottom: 8px;
  background: #f9fafb;
  border-left: 3px solid #ef4444;
  border-radius: 4px;
  font-size: 14px;
  line-height: 1.6;
  color: #374151;
}

.improvement-item {
  border-left-color: #10b981;
}

.optimized-prompt-box {
  background: #f9fafb;
  border: 1px solid #e5e7eb;
  border-radius: 8px;
  padding: 16px;
}

.optimized-prompt-text {
  margin: 0;
  padding: 0;
  font-size: 14px;
  line-height: 1.7;
  color: #1f2937;
  white-space: pre-wrap;
  word-break: break-word;
  font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', sans-serif;
}

/* 模板库样式 */
.template-library {
  padding: 0;
}

.strategy-filter {
  margin-bottom: 20px;
  padding-bottom: 16px;
  border-bottom: 1px solid #e5e7eb;
}

.template-list {
  max-height: calc(100vh - 300px);
  overflow-y: auto;
  margin-bottom: 20px;
}

.template-item {
  padding: 16px;
  margin-bottom: 12px;
  background: #f9fafb;
  border: 1px solid #e5e7eb;
  border-radius: 8px;
  cursor: pointer;
  transition: all 0.2s;
}

.template-item:hover {
  background: #f3f4f6;
  border-color: #1890ff;
  box-shadow: 0 2px 8px rgba(24, 144, 255, 0.1);
}

.template-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 8px;
}

.template-title-row {
  display: flex;
  align-items: center;
  gap: 8px;
}

.template-name {
  font-size: 15px;
  font-weight: 600;
  color: #1f2937;
}

.template-description {
  font-size: 13px;
  color: #6b7280;
  margin-bottom: 8px;
}

.template-preview {
  font-size: 12px;
  color: #9ca3af;
  background: #fff;
  padding: 8px 12px;
  border-radius: 4px;
  margin-bottom: 8px;
  line-height: 1.5;
}

.template-footer {
  display: flex;
  justify-content: flex-end;
  font-size: 12px;
  color: #9ca3af;
}

.template-actions {
  padding-top: 16px;
  border-top: 1px solid #e5e7eb;
}
</style>
