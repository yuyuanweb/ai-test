<template>
  <div id="side-by-side-page" class="page-container">
    <!-- 顶部栏 -->
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
        </a-select>

        <!-- 动态模型选择器 (1-8个) -->
        <template v-for="(model, index) in selectedModels" :key="`model-select-${index}`">
          <span v-if="index > 0" class="vs-label">vs</span>
          <a-select
            :value="selectedModels[index]"
            @update:value="(val) => updateModelAtIndex(index, val)"
            @dropdown-visible-change="(visible) => handleDropdownChange(visible)"
            :placeholder="`选择模型 ${index + 1}`"
            style="width: 200px; flex-shrink: 0;"
            show-search
            :options="getAvailableOptionsForIndex(index)"
            :loading="loadingModels"
            :filter-option="false"
            @search="handleSearchModel"
            @popup-scroll="handlePopupScroll"
            allow-clear
          />
        </template>

        <!-- 添加/删除模型按钮（已有会话中禁用） -->
        <a-button
          v-if="selectedModels.length < 8 && !isExistingConversation"
          type="dashed"
          size="small"
          @click="addModel"
          style="margin-left: 8px; flex-shrink: 0;"
        >
          + 添加模型
        </a-button>
        <a-button
          v-if="selectedModels.length > 1 && !isExistingConversation"
          type="dashed"
          size="small"
          danger
          @click="removeModel"
          style="margin-left: 8px; flex-shrink: 0;"
        >
          - 移除
        </a-button>
      </div>
    </header>

    <!-- 聊天区域 -->
    <div class="chat-section">
      <!-- 欢迎界面 -->
      <div v-if="messages.length === 0" class="welcome-view">
        <h1 class="main-title">你想做什么？</h1>
      </div>

      <!-- 消息列表 -->
      <div v-else class="messages-wrapper" ref="messagesWrapper">
        <div v-for="(msg, idx) in messages" :key="idx" class="msg-block">
          <!-- 用户消息 - 右对齐 -->
          <div v-if="msg.type === 'user'" class="user-msg">
            <div class="user-bubble">{{ msg.content }}</div>
          </div>

          <!-- AI回答 - 水平并排 -->
          <div v-if="msg.type === 'assistant' && msg.responses" class="ai-responses-wrapper">
            <div class="ai-responses" :style="{ width: '100%', marginTop: '20px' }">
              <div
                class="response-grid"
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
                  v-for="(resp, respIndex) in msg.responses"
                  :key="`${resp.modelName}-${respIndex}`"
                  class="response-col"
                  :style="{
                    flex: '1 1 0%',
                    minWidth: '400px',
                    background: '#ffffff',
                    border: '1px solid #e5e7eb',
                    borderRadius: '12px',
                    padding: '18px',
                    minHeight: '180px',
                    boxSizing: 'border-box',
                    display: 'flex',
                    flexDirection: 'column',
                    boxShadow: '0 1px 3px rgba(0, 0, 0, 0.06)'
                  }"
                >
                  <div class="col-header">
                    <div class="header-left">
                      <img
                        :src="getProviderIcon(resp.modelName)"
                        :alt="getModelName(resp.modelName)"
                        class="model-icon"
                        @error="(e) => (e.target as HTMLImageElement).src = '/src/assets/provider-icons/default.png'"
                      />
                      <span class="model-tag">{{ getModelName(resp.modelName) }}</span>
                    </div>
                    <div class="header-right">
                      <div class="metrics">
                        <!-- 实时响应时间 -->
                        <span v-if="!resp.done && resp.elapsedMs" class="metric-item">
                        ⏱ {{ (resp.elapsedMs / 1000).toFixed(1) }}s
                      </span>
                        <span v-else-if="resp.responseTimeMs" class="metric-item">
                        ⏱ {{ (resp.responseTimeMs / 1000).toFixed(2) }}s
                      </span>

                        <!-- Token消耗 -->
                        <span v-if="resp.inputTokens || resp.outputTokens" class="metric-item">
                        📊 {{ (resp.inputTokens || 0) + (resp.outputTokens || 0) }}t
                      </span>

                        <!-- 成本 -->
                        <span v-if="resp.cost" class="metric-item">
                        💰 ${{ resp.cost.toFixed(4) }}
                      </span>
                      </div>
                      <div class="action-buttons">
                        <button
                          class="action-btn"
                          title="复制响应"
                          @click="copyResponse(resp.fullContent || '', resp.modelName)"
                        >
                          <CopyOutlined />
                        </button>
                        <button
                          class="action-btn"
                          title="最大化"
                          @click="expandResponse(resp.modelName, resp.fullContent || '')"
                        >
                          <ExpandOutlined />
                        </button>
                      </div>
                    </div>
                  </div>
                  <div class="col-body">
                    <!-- 错误提示 -->
                    <div v-if="resp.hasError" class="error-message">
                      <a-alert
                        type="error"
                        :message="`调用失败: ${resp.error || '未知错误'}`"
                        show-icon
                        closable
                      />
                    </div>
                    <!-- 正常内容 -->
                    <template v-else>
                      <!-- 思考过程 -->
                      <details v-if="resp.hasReasoning && resp.reasoning" class="thinking-details" open>
                        <summary class="thinking-summary">
                        <span class="thinking-title">
                          思考了 {{ resp.thinkingTime || calculateThinkingTime(resp.reasoning) }} 秒
                        </span>
                        </summary>
                        <div class="thinking-content">
                          <MarkdownRenderer :content="resp.reasoning || ''" />
                        </div>
                      </details>
                      <!-- 最终回答 - 使用Markdown渲染 -->
                      <MarkdownRenderer :content="resp.fullContent || ''" />
                    </template>
                    <div v-if="!resp.done && !resp.hasError" class="dots">
                      <span></span><span></span><span></span>
                    </div>
                  </div>
                </div>
              </div>
            </div>

            <!-- 评分按钮 - 只在所有响应完成后显示 -->
            <div
              v-if="msg.responses.every((r: any) => r.done) && msg.responses.length >= 2"
              class="rating-section"
            >
              <div class="rating-buttons">
                <!-- 为每个模型生成"X更好"按钮 -->
                <button
                  v-for="(resp, respIdx) in msg.responses"
                  :key="`model-better-${respIdx}`"
                  class="rating-btn"
                  :class="{ 'rating-selected': isModelSelected(msg, resp.modelName, respIdx) }"
                  @click="handleRating(idx, 'model_better', resp.modelName)"
                >
                  {{ getModelName(resp.modelName) }} 更好
                </button>
                <button
                  class="rating-btn"
                  :class="{ 'rating-selected': msg.rating?.ratingType === 'tie' }"
                  @click="handleRating(idx, 'tie')"
                >
                  平局 😐
                </button>
                <button
                  class="rating-btn"
                  :class="{ 'rating-selected': msg.rating?.ratingType === 'both_bad' }"
                  @click="handleRating(idx, 'both_bad')"
                >
                  都不好 👎
                </button>
              </div>
            </div>
          </div>
        </div>
      </div>

      <!-- 输入框 -->
      <div class="input-zone">
        <div class="input-card">
          <textarea
            v-model="userInput"
            placeholder="输入你的问题..."
            :disabled="isLoading"
            @keydown.enter.exact.prevent="sendMessage"
            class="text-input"
          ></textarea>

          <div class="bottom-bar">
            <div class="left-tools">
              <button class="tool-icon"><SearchOutlined /></button>
            </div>

            <button
              class="send-icon"
              :disabled="!canSend"
              @click="sendMessage"
            >
              <SendOutlined />
            </button>
          </div>
        </div>
      </div>
    </div>

    <!-- 最大化对话框 -->
    <a-modal
      :open="!!expandedResponse"
      :title="expandedResponse ? getModelName(expandedResponse.modelName) : ''"
      width="90%"
      :footer="null"
      @cancel="closeExpanded"
    >
      <div v-if="expandedResponse" class="expanded-content">
        <div class="expanded-header">
          <img
            :src="getProviderIcon(expandedResponse.modelName)"
            :alt="getModelName(expandedResponse.modelName)"
            class="expanded-icon"
          />
          <h3>{{ getModelName(expandedResponse.modelName) }}</h3>
        </div>
        <div class="expanded-body">
          <MarkdownRenderer :content="expandedResponse.content" />
        </div>
      </div>
    </a-modal>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, onMounted, onUnmounted, watch, nextTick } from 'vue'
import { useRouter, useRoute } from 'vue-router'
import { message } from 'ant-design-vue'
import { useLoginUserStore } from '@/stores/loginUser'
import {
  SearchOutlined,
  SendOutlined,
  SwapOutlined,
  ExperimentOutlined,
  CopyOutlined,
  ExpandOutlined,
  ReloadOutlined,
} from '@ant-design/icons-vue'
import { listModels, type ModelVO } from '@/api/modelController'
import { getConversationMessages, getConversation, type StreamChunkVO } from '@/api/conversationController'
import { createPostSSE } from '@/utils/sseClient'
import { API_BASE_URL } from '@/config/env'
import MarkdownRenderer from '@/components/MarkdownRenderer.vue'
import { addRating, getRating, type RatingVO } from '@/api/ratingController'

interface Msg {
  type: 'user' | 'assistant'
  content?: string
  responses?: StreamChunkVO[]
  messageIndex?: number  // 添加消息索引用于评分
  rating?: RatingVO  // 添加评分信息
}

const router = useRouter()
const route = useRoute()
const loginUserStore = useLoginUserStore()
const loginUser = computed(() => loginUserStore.loginUser)

const currentMode = ref('side-by-side')
const selectedModels = ref<(string | undefined)[]>([undefined, undefined]) // 默认2个模型
const userInput = ref('')
const isLoading = ref(false)
const loadingModels = ref(false)
const modelOptions = ref<{ label: string; value: string }[]>([])
const messages = ref<Msg[]>([])
const sse = ref<any>(null)
const isNewConversation = ref(false) // 标记是否是新会话（刚生成conversationId）
const expandedResponse = ref<{ modelName: string; content: string } | null>(null) // 最大化的响应
const messagesWrapper = ref<HTMLElement | null>(null) // 消息列表容器引用

// 分页相关
const currentPage = ref(1)
const pageSize = 50
const hasMore = ref(true)
const currentSearchText = ref<string>()

const handleModeChange = (mode: string) => {
  router.push(`/${mode}`)
}

const canSend = computed(() => {
  const validModels = selectedModels.value.filter(m => m)
  return userInput.value.trim() && validModels.length >= 1 && !isLoading.value
})

// 是否是已有会话（禁止修改模型）
const isExistingConversation = computed(() => {
  return !!route.query.conversationId
})

// 更新指定位置的模型
const updateModelAtIndex = (index: number, value: string | undefined) => {
  console.log('🔧 更新模型:', {
    位置: index,
    新值: value,
    旧值: selectedModels.value[index]
  })

  // 检查是否有重复（排除自己）
  if (value) {
    const isDuplicate = selectedModels.value.some((m, idx) =>
      idx !== index && m === value
    )

    if (isDuplicate) {
      message.warning('该模型已被选择，请选择其他模型！')
      console.warn('⚠️ 重复选择模型:', value)
      return // 不更新
    }
  }

  const newModels = [...selectedModels.value]
  newModels[index] = value
  selectedModels.value = newModels

  const validModels = selectedModels.value.filter(m => m)
  console.log('✅ 更新后的模型列表:', {
    完整列表: selectedModels.value,
    有效模型: validModels,
    数量: validModels.length
  })
}

// 获取某个位置可选的模型选项（排除已被其他位置选择的模型）
const getAvailableOptionsForIndex = (currentIndex: number) => {
  // 获取其他位置已选择的模型
  const selectedByOthers = selectedModels.value
    .map((model, idx) => idx !== currentIndex ? model : null)
    .filter(m => m) as string[]

  console.log(`📋 位置${currentIndex}可选模型:`, {
    总数: modelOptions.value.length,
    已被占用: selectedByOthers.length,
    占用的模型: selectedByOthers
  })

  // 过滤掉已被选择的模型
  const available = modelOptions.value.filter(option =>
    !selectedByOthers.includes(option.value)
  )

  console.log(`📋 位置${currentIndex}过滤后可选:`, available.length)
  return available
}

// 添加模型
const addModel = () => {
  if (selectedModels.value.length < 8) {
    selectedModels.value = [...selectedModels.value, undefined]
    console.log('➕ 添加模型槽位，当前数量:', selectedModels.value.length)
  }
}

// 移除最后一个模型
const removeModel = () => {
  if (selectedModels.value.length > 1) {
    selectedModels.value = selectedModels.value.slice(0, -1)
    console.log('➖ 移除模型槽位，当前数量:', selectedModels.value.length)
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

    if (res.data && res.data.code === 0 && res.data.data && res.data.data.records) {
      const models = res.data.data.records
      const newOptions = models.map((m: ModelVO) => ({
        label: m.name,
        value: m.id,
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

      // 如果当前没有选择模型，自动选择前两个
      if (!selectedModels.value[0] && !append && modelOptions.value.length >= 2) {
        selectedModels.value = [
          modelOptions.value[0].value,
          modelOptions.value[1].value
        ]
        console.log('✅ 自动选择默认模型:', selectedModels.value)
      }
    }
  } catch (err) {
    console.error('Load models error:', err)
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

// 处理清除搜索
const handleClearSearch = () => {
  console.log('🧹 清除搜索')
  currentSearchText.value = undefined
  currentPage.value = 1
  loadModels(undefined, false)
}

// 处理下拉框显示/隐藏
const handleDropdownChange = (visible: boolean) => {
  if (!visible && currentSearchText.value) {
    // 下拉框关闭时，如果有搜索词，清空搜索并重新加载完整列表
    console.log('🔄 下拉框关闭，清除搜索状态')
    currentSearchText.value = undefined
    currentPage.value = 1
    loadModels(undefined, false)
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



const sendMessage = async () => {
  if (!canSend.value) return

  // 检查是否登录
  if (!loginUser.value.id) {
    message.warning('请先登录')
    router.push('/user/login')
    return
  }

  const text = userInput.value.trim()
  userInput.value = ''
  isLoading.value = true

  // 如果当前没有conversationId，说明是新会话
  const currentConversationId = route.query.conversationId as string
  if (!currentConversationId) {
    isNewConversation.value = true
    console.log('🆕 标记为新会话')
  }

  // 获取已选择的有效模型
  const validModels = selectedModels.value.filter(m => m) as string[]
  console.log('🎯 准备发送消息，选中的模型:', validModels)
  console.log('🎯 模型数量:', validModels.length)

  // 添加用户消息
  const userMsgIndex = messages.value.length
  messages.value.push({
    type: 'user',
    content: text,
    messageIndex: Math.floor(userMsgIndex / 2)  // 每两条消息（user+assistant）为一轮
  })

  // 添加assistant消息占位（索引是当前长度，因为接下来要push）
  const assistantMsgIndex = messages.value.length
  console.log('📍 Assistant消息索引:', assistantMsgIndex, '当前消息数:', messages.value.length)

  const initialResponses = validModels.map(model => ({
    modelName: model,
    fullContent: '',
    done: false,
    hasError: false
  }))

  messages.value.push({
    type: 'assistant',
    responses: initialResponses,
    messageIndex: Math.floor(assistantMsgIndex / 2),  // 每两条消息（user+assistant）为一轮
    rating: undefined
  })

  console.log('✅ 添加后消息数:', messages.value.length, 'Assistant在索引:', assistantMsgIndex)
  console.log('✅ 初始化响应列表:', initialResponses.map(r => r.modelName))

  // 滚动到底部显示新消息
  scrollToBottom()

  try {
    const url = `${API_BASE_URL}/conversation/side-by-side/stream`
    console.log('发送请求:', { conversationId: currentConversationId, models: validModels, prompt: text })

    sse.value = await createPostSSE(
      url,
      {
        conversationId: currentConversationId,
        models: validModels,
        prompt: text,
        stream: true
      },
      {
        onMessage: (chunk: StreamChunkVO) => {
          console.log('📨 收到SSE消息:', {
            modelName: chunk.modelName,
            contentLength: chunk.fullContent?.length || 0,
            done: chunk.done,
            hasError: chunk.hasError,
            error: chunk.error,
            hasReasoning: chunk.hasReasoning,
            reasoningLength: chunk.reasoning?.length || 0
          })

          // 检查是否有错误
          if (chunk.hasError) {
            console.error('❌ 模型调用失败:', chunk.modelName, chunk.error)
            message.error(`${chunk.modelName} 调用失败: ${chunk.error || '未知错误'}`)
          }

          // 如果有思考内容，输出详细信息
          if (chunk.reasoning) {
            console.log('💭 思考内容:', chunk.reasoning.substring(0, 100) + '...')
          }

          // 如果是新会话，保存conversationId到URL
          if (chunk.conversationId && !route.query.conversationId) {
            console.log('💾 保存新会话ID到URL:', chunk.conversationId)
            router.replace({
              path: '/side-by-side',
              query: { conversationId: chunk.conversationId }
            })
            // 保持isNewConversation标志，在所有消息完成后再重置
          }

          // 获取当前的assistant消息
          const msg = messages.value[assistantMsgIndex]
          console.log('🔍 当前assistant消息:', msg ? `存在，有${msg.responses?.length}个响应` : '不存在')

          if (!msg || !msg.responses) {
            console.error('❌ assistant消息不存在或没有responses数组！')
            return
          }

          const idx = msg.responses.findIndex((r: any) => r.modelName === chunk.modelName)
          console.log('🔍 查找模型:', chunk.modelName, '→ 索引:', idx)
          console.log('🔍 响应列表中的模型:', msg.responses.map((r: any) => r.modelName))

          if (idx >= 0) {
            // 使用响应式替换确保Vue检测到变化
            msg.responses[idx] = { ...msg.responses[idx], ...chunk }
            console.log('✔ 更新成功:', chunk.modelName, 'at index', idx, '→ done:', msg.responses[idx].done, '内容长度:', msg.responses[idx].fullContent?.length)

            // 强制触发响应式更新
            messages.value = [...messages.value]

            // 如果是第一次收到内容，滚动到底部
            if (chunk.fullContent && chunk.fullContent.length < 100) {
              scrollToBottom()
            }
          } else {
            console.error('❌ 未找到匹配的响应槽位！收到的modelName:', chunk.modelName)
            console.error('❌ 期望的模型列表:', validModels)
          }

          // 检查完成
          if (chunk.done) {
            const allDone = msg.responses.every((r: any) => r.done)
            const doneCount = msg.responses.filter((r: any) => r.done).length
            console.log(`📊 完成进度: ${doneCount}/${msg.responses.length}`)

            if (allDone) {
              console.log('✅ 所有模型响应完成！')
              isLoading.value = false
              // 重置新会话标志
              if (isNewConversation.value) {
                console.log('🔄 重置新会话标志')
                isNewConversation.value = false
              }
              // 所有响应完成后，再次滚动到底部
              scrollToBottom()
            }
          }
        },
        onError: (err) => {
          console.error('SSE错误:', err)
          isLoading.value = false
          isNewConversation.value = false
          message.error('请求失败: ' + err.message)
        },
        onComplete: () => {
          console.log('🏁 SSE连接完成')
          isLoading.value = false
          isNewConversation.value = false
        },
      }
    )
  } catch (err: any) {
    console.error('发送失败:', err)
    isLoading.value = false
    isNewConversation.value = false
    message.error('发送失败: ' + err.message)
  }
}

const getModelName = (id: string | undefined) => {
  if (!id) return ''
  return id.split('/').pop() || id
}

// 根据模型ID获取提供商图标
const getProviderIcon = (modelId: string | undefined) => {
  if (!modelId) return '/src/assets/provider-icons/default.png'

  const id = modelId.toLowerCase()

  // 根据模型ID匹配对应的提供商图标
  if (id.includes('openai') || id.includes('gpt')) return '/src/assets/provider-icons/openai.png'
  if (id.includes('anthropic') || id.includes('claude')) return '/src/assets/provider-icons/anthropic.png'
  if (id.includes('google') || id.includes('gemini')) return '/src/assets/provider-icons/google.png'
  if (id.includes('qwen') || id.includes('alibaba')) return '/src/assets/provider-icons/qwen.png'
  if (id.includes('deepseek')) return '/src/assets/provider-icons/deepseek.png'
  if (id.includes('moonshot') || id.includes('kimi')) return '/src/assets/provider-icons/moonshot.png'
  if (id.includes('zhipu') || id.includes('glm')) return '/src/assets/provider-icons/zhipu.png'
  if (id.includes('baidu') || id.includes('ernie')) return '/src/assets/provider-icons/baidu.png'
  if (id.includes('tencent') || id.includes('hunyuan')) return '/src/assets/provider-icons/tencent.png'
  if (id.includes('bytedance') || id.includes('doubao')) return '/src/assets/provider-icons/bytedance.png'
  if (id.includes('meta') || id.includes('llama')) return '/src/assets/provider-icons/meta-llama.png'

  return '/src/assets/provider-icons/default.png'
}

// 复制响应内容
const copyResponse = async (content: string, modelName: string) => {
  try {
    await navigator.clipboard.writeText(content)
    message.success(`已复制 ${getModelName(modelName)} 的响应`)
  } catch (err) {
    message.error('复制失败')
  }
}

// 最大化响应
const expandResponse = (modelName: string, content: string) => {
  expandedResponse.value = { modelName, content }
}

// 关闭最大化
const closeExpanded = () => {
  expandedResponse.value = null
}

// 滚动到消息列表底部
const scrollToBottom = async () => {
  await nextTick()
  if (messagesWrapper.value) {
    messagesWrapper.value.scrollTo({
      top: messagesWrapper.value.scrollHeight,
      behavior: 'smooth'
    })
    console.log('📜 自动滚动到底部')
  }
}

// 估算思考时间（如果后端没有提供）
const calculateThinkingTime = (reasoning: string | undefined) => {
  if (!reasoning) return 0
  // 简单估算：每200个字符约1秒
  const estimatedSeconds = Math.ceil(reasoning.length / 200)
  return Math.max(1, Math.min(estimatedSeconds, 60)) // 最少1秒，最多60秒
}

// 判断模型按钮是否应该被选中（兼容旧的left_better/right_better类型）
const isModelSelected = (msg: Msg, modelName: string, modelIndex: number) => {
  if (!msg.rating) return false
  
  const rating = msg.rating
  // 新的model_better类型：直接比较winnerModel
  if (rating.ratingType === 'model_better' && rating.winnerModel === modelName) {
    return true
  }
  
  // 兼容旧的left_better类型：第一个模型
  if (rating.ratingType === 'left_better' && modelIndex === 0) {
    return true
  }
  
  // 兼容旧的right_better类型：最后一个模型
  if (rating.ratingType === 'right_better' && modelIndex === (msg.responses?.length || 0) - 1) {
    return true
  }
  
  return false
}

// 处理用户评分
const handleRating = async (msgIndex: number, ratingType: string, winnerModelName?: string) => {
  const msg = messages.value[msgIndex]
  if (!msg || msg.type !== 'assistant' || !msg.responses) {
    return
  }

  const conversationId = route.query.conversationId as string
  if (!conversationId) {
    message.warning('请先开始对话')
    return
  }

  try {
    // 确定获胜和失败的模型
    let winnerModel: string | undefined
    let loserModel: string | undefined

    if (ratingType === 'model_better' && winnerModelName) {
      // 选择特定模型为获胜者
      winnerModel = winnerModelName
      // 其他所有模型都是失败者（可以选择第一个作为loserModel，或者留空）
      const otherModels = msg.responses.filter((r: any) => r.modelName !== winnerModelName)
      if (otherModels.length > 0) {
        loserModel = otherModels[0].modelName
      }
    } else if (ratingType === 'tie' || ratingType === 'both_bad') {
      // 平局或都不好，不需要winnerModel和loserModel
      winnerModel = undefined
      loserModel = undefined
    }

    // 提交评分
    const res: any = await addRating({
      conversationId,
      messageIndex: msg.messageIndex!,
      ratingType,
      winnerModel,
      loserModel
    })

    if (res.data && res.data.code === 0) {
      // 更新本地评分状态
      msg.rating = {
        id: '',
        conversationId,
        messageIndex: msg.messageIndex!,
        userId: loginUser.value.id,
        ratingType,
        winnerModel,
        loserModel,
        createTime: new Date().toISOString()
      }
      // 触发响应式更新
      messages.value = [...messages.value]
      message.success('评分成功')
    }
  } catch (error) {
    console.error('评分失败:', error)
    message.error('评分失败')
  }
}

// 加载评分信息
const loadRatings = async (conversationId: string) => {
  // 为每条assistant消息加载评分
  for (let i = 0; i < messages.value.length; i++) {
    const msg = messages.value[i]
    if (msg.type === 'assistant' && msg.messageIndex !== undefined) {
      try {
        const res: any = await getRating({ conversationId, messageIndex: msg.messageIndex })
        if (res.data && res.data.code === 0 && res.data.data) {
          msg.rating = res.data.data
        }
      } catch (error) {
        console.error('加载评分失败:', error)
      }
    }
  }
  // 触发响应式更新
  messages.value = [...messages.value]
}

// 加载历史会话消息
const loadConversationHistory = async () => {
  const conversationId = route.query.conversationId as string
  console.log('🔍 URL参数 conversationId:', conversationId)

  if (!conversationId) {
    console.log('⚠️ 没有conversationId参数，跳过加载历史消息')
    return
  }

  try {
    console.log('📡 开始加载历史会话:', conversationId)

    // 1. 先获取会话详情，获取模型列表
    const conversationRes: any = await getConversation({ conversationId })
    console.log('📡 会话详情响应:', conversationRes)

    if (conversationRes.data && conversationRes.data.code === 0 && conversationRes.data.data) {
      const conversation = conversationRes.data.data
      console.log('📋 会话信息:', conversation)
      console.log('📋 会话类型:', conversation.conversationType)


      console.log('📋 会话models字段:', conversation.models)
      console.log('📋 models类型:', typeof conversation.models)
      console.log('📋 是否数组:', Array.isArray(conversation.models))

      // 更新模型选择器，从会话的models字段中获取
      let modelsList = conversation.models

      // 如果models是JSON字符串，需要解析
      if (typeof modelsList === 'string') {
        try {
          modelsList = JSON.parse(modelsList)
          console.log('📋 解析后的models:', modelsList)
        } catch (e) {
          console.error('❌ 解析models失败:', e)
        }
      }

      if (modelsList && Array.isArray(modelsList) && modelsList.length > 0) {
        selectedModels.value = [...modelsList]
        console.log('✅ 设置模型列表:', selectedModels.value)
        console.log('✅ 模型数量:', selectedModels.value.length)
      } else {
        console.warn('⚠️ models字段无效:', modelsList)
      }
    }

    // 2. 加载消息历史
    const res: any = await getConversationMessages({ conversationId })
    console.log('📡 消息API完整响应:', res)
    console.log('📡 消息API响应数据:', res.data)

    if (res.data && res.data.code === 0 && res.data.data) {
      const historyMessages = res.data.data as any[]
      console.log('📨 原始历史消息数量:', historyMessages.length, historyMessages)

      // 按messageIndex分组
      const messagesByIndex = new Map<number, any[]>()
      historyMessages.forEach((msg: any) => {
        const index = msg.messageIndex
        console.log('📝 消息分组:', {
          messageIndex: index,
          role: msg.role,
          modelName: msg.modelName,
          content: msg.content?.substring(0, 30)
        })
        if (!messagesByIndex.has(index)) {
          messagesByIndex.set(index, [])
        }
        messagesByIndex.get(index)!.push(msg)
      })

      console.log('📊 分组结果:', Array.from(messagesByIndex.entries()).map(([index, msgs]) => ({
        index,
        count: msgs.length,
        roles: msgs.map(m => m.role),
        models: msgs.map(m => m.modelName)
      })))

      // 转换为前端格式
      const groupedMessages: Msg[] = []
      const sortedIndexes = Array.from(messagesByIndex.keys()).sort((a, b) => a - b)

      sortedIndexes.forEach(index => {
        const msgs = messagesByIndex.get(index)!

        if (msgs[0].role === 'user') {
          // 用户消息
          groupedMessages.push({
            type: 'user',
            content: msgs[0].content,
            messageIndex: index
          })
        } else if (msgs[0].role === 'assistant') {
          // AI回复 - 多个模型的响应
          console.log('🔍 原始assistant消息:', msgs)
          const responses = msgs.map((msg: any) => {
            console.log('🔍 单条消息字段:', {
              modelName: msg.modelName,
              inputTokens: msg.inputTokens,
              outputTokens: msg.outputTokens,
              cost: msg.cost,
              responseTimeMs: msg.responseTimeMs
            })
            // 计算思考时间
            const thinkingTime = msg.reasoning
              ? Math.max(1, Math.min(Math.ceil(msg.reasoning.length / 200), 60))
              : undefined

            return {
              modelName: msg.modelName || '',
              fullContent: msg.content || '',
              done: true,
              hasError: false,
              responseTimeMs: msg.responseTimeMs,
              inputTokens: msg.inputTokens || 0,
              outputTokens: msg.outputTokens || 0,
              cost: msg.cost,
              reasoning: msg.reasoning,
              hasReasoning: !!msg.reasoning,
              thinkingTime: thinkingTime
            }
          })

          // 按照selectedModels的顺序排序responses
          const sortedResponses = [...responses].sort((a, b) => {
            const indexA = selectedModels.value.indexOf(a.modelName)
            const indexB = selectedModels.value.indexOf(b.modelName)
            // 如果在selectedModels中找不到，放到最后
            return (indexA === -1 ? 999 : indexA) - (indexB === -1 ? 999 : indexB)
          })

          console.log('✅ 构建的assistant消息:', { type: 'assistant', responses: sortedResponses })
          groupedMessages.push({
            type: 'assistant',
            responses: sortedResponses,
            messageIndex: index,
            rating: undefined
          })
        }
      })

      messages.value = groupedMessages
      console.log('✅ 加载了', groupedMessages.length, '组消息', groupedMessages)

      // 加载评分信息
      await loadRatings(conversationId)

      // 加载完成后滚动到底部
      scrollToBottom()
    } else {
      console.log('⚠️ API返回数据格式不正确:', res)
    }
  } catch (error) {
    console.error('❌ 加载历史消息失败:', error)
    message.error('加载历史消息失败')
  }
}

// 监听selectedModels变化（用于调试）
watch(selectedModels, (newVal, oldVal) => {
  console.log('🔄 selectedModels变化:', {
    旧值: oldVal,
    新值: newVal,
    长度: newVal.length,
    唯一值数量: new Set(newVal.filter(m => m)).size
  })
}, { deep: true })

// 监听消息数量变化，自动滚动到底部
watch(() => messages.value.length, () => {
  nextTick(() => {
    scrollToBottom()
  })
})

// 监听路由参数变化
watch(() => route.query.conversationId, (newId, oldId) => {
  console.log('🔄 conversationId变化:', oldId, '->', newId, '是否新会话:', isNewConversation.value)

  // 如果是新会话刚生成的ID，不要重新加载历史（因为消息还在接收中）
  if (newId && !isNewConversation.value) {
    console.log('📥 加载历史会话')
    loadConversationHistory()
  } else if (newId && isNewConversation.value) {
    console.log('⏸️ 跳过加载历史（新会话消息接收中）')
  } else if (oldId && !newId) {
    // 从历史会话切换到新对话，清空状态
    console.log('🆕 开始新对话，清空历史消息')
    messages.value = []
    userInput.value = ''
    isNewConversation.value = false
    // 重置为默认2个模型（如果已加载模型列表，使用前两个）
    if (modelOptions.value.length >= 2) {
      selectedModels.value = [
        modelOptions.value[0].value,
        modelOptions.value[1].value
      ]
    } else {
      selectedModels.value = [undefined, undefined]
    }
  }
}, { immediate: false })

// 监听整个query变化（包括t参数）
watch(() => route.query.t, (newT) => {
  if (newT && !route.query.conversationId) {
    console.log('🆕 新对话触发，清空状态')
    messages.value = []
    userInput.value = ''
    // 重置为默认2个模型（如果已加载模型列表，使用前两个）
    if (modelOptions.value.length >= 2) {
      selectedModels.value = [
        modelOptions.value[0].value,
        modelOptions.value[1].value
      ]
    } else {
      selectedModels.value = [undefined, undefined]
    }
  }
})

onMounted(() => {
  console.log('📱 页面挂载, route.query:', route.query)
  loadModels()
  loadConversationHistory()
})
onUnmounted(() => sse.value?.close())
</script>

<style scoped>
/* ========== 页面样式 ========== */
.ai-responses,
.msg-block .ai-responses,
.page-container .msg-block .ai-responses,
.page-container .ai-responses,
.ai-responses {
  width: 100% !important;
  margin-top: 20px !important;
  display: block !important;
}

#side-by-side-page .response-grid,
#side-by-side-page .msg-block .ai-responses .response-grid,
.msg-block .ai-responses .response-grid,
.page-container .msg-block .ai-responses .response-grid,
.page-container .response-grid,
.response-grid {
  display: flex !important;
  flex-direction: row !important;
  flex-wrap: nowrap !important;
  gap: 20px !important;
  width: 100% !important;
  overflow-x: auto !important;
  align-items: stretch !important;
  padding-bottom: 4px !important;
}

/* 防止被任何其他样式覆盖 */
#side-by-side-page div.response-grid,
div.response-grid {
  display: flex !important;
  flex-direction: row !important;
}

/* 隐藏响应区域的滚动条但保持滚动功能 */
#side-by-side-page .response-grid,
.response-grid {
  scrollbar-width: none !important; /* Firefox */
  -ms-overflow-style: none !important; /* IE and Edge */
}

#side-by-side-page .response-grid::-webkit-scrollbar,
.response-grid::-webkit-scrollbar {
  display: none !important; /* Chrome, Safari and Opera */
}

#side-by-side-page .response-grid .response-col,
#side-by-side-page .msg-block .response-grid .response-col,
.msg-block .response-grid .response-col,
.page-container .response-grid .response-col,
.response-grid .response-col,
div.response-col {
  flex: 1 1 0% !important;
  min-width: 400px !important;
  max-width: none !important;
  background: #ffffff !important;
  border: 1px solid #e5e7eb !important;
  border-radius: 12px !important;
  padding: 18px !important;
  min-height: 180px !important;
  box-sizing: border-box !important;
  display: flex !important;
  flex-direction: column !important;
  box-shadow: 0 1px 3px rgba(0, 0, 0, 0.06) !important;
}

#side-by-side-page .col-header,
.col-header {
  display: flex !important;
  justify-content: space-between !important;
  align-items: center !important;
  margin-bottom: 12px !important;
  padding-bottom: 10px !important;
  border-bottom: 1px solid #ddd !important;
  flex-shrink: 0 !important;
}

.header-left {
  display: flex !important;
  align-items: center !important;
  gap: 8px !important;
}

.header-right {
  display: flex !important;
  align-items: center !important;
  gap: 12px !important;
}

.model-icon {
  width: 20px !important;
  height: 20px !important;
  border-radius: 4px !important;
  object-fit: contain !important;
}

#side-by-side-page .model-tag,
.model-tag {
  font-size: 13px !important;
  font-weight: 600 !important;
  color: #1f2937 !important;
}

.action-buttons {
  display: flex !important;
  gap: 4px !important;
}

.action-btn {
  width: 28px !important;
  height: 28px !important;
  border: none !important;
  background: transparent !important;
  color: #6b7280 !important;
  cursor: pointer !important;
  border-radius: 6px !important;
  display: flex !important;
  align-items: center !important;
  justify-content: center !important;
  transition: all 0.2s !important;
  font-size: 14px !important;
}

.action-btn:hover {
  background: #f3f4f6 !important;
  color: #1f2937 !important;
}

#side-by-side-page .metrics,
.metrics {
  display: flex !important;
  gap: 10px !important;
  font-size: 11px !important;
  color: #6b7280 !important;
  flex-wrap: wrap !important;
}

#side-by-side-page .metric-item,
.metric-item {
  white-space: nowrap !important;
  background: #fff !important;
  padding: 2px 8px !important;
  border-radius: 4px !important;
}

#side-by-side-page .col-body,
.col-body {
  font-size: 14px !important;
  line-height: 1.7 !important;
  color: #374151 !important;
  flex: 1 !important;
  overflow-y: auto !important;
}

#side-by-side-page .text-box,
.text-box {
  white-space: pre-wrap !important;
  word-break: break-word !important;
  padding: 4px 0 !important;
}

#side-by-side-page .err-box,
.error-message {
  margin: 8px 0;
}

.err-box {
  color: #dc2626 !important;
  font-size: 13px !important;
  background: #fef2f2 !important;
  padding: 8px 12px !important;
  border-radius: 6px !important;
  border: 1px solid #fecaca !important;
}

#side-by-side-page .thinking-details,
.thinking-details {
  background: #f7f7f8 !important;
  border: none !important;
  border-radius: 6px !important;
  padding: 10px 12px !important;
  margin-bottom: 12px !important;
}

#side-by-side-page .thinking-summary,
.thinking-summary {
  cursor: pointer !important;
  font-weight: 500 !important;
  color: #6b7280 !important;
  font-size: 13px !important;
  user-select: none !important;
  display: flex !important;
  align-items: center !important;
  list-style: none !important;
  position: relative !important;
}

/* 移除默认的三角图标 */
#side-by-side-page .thinking-summary::-webkit-details-marker,
.thinking-summary::-webkit-details-marker {
  display: none !important;
}

/* 自定义箭头图标 */
#side-by-side-page .thinking-summary::before,
.thinking-summary::before {
  content: '▶' !important;
  display: inline-block !important;
  margin-right: 8px !important;
  transition: transform 0.2s !important;
  font-size: 10px !important;
}

#side-by-side-page .thinking-details[open] .thinking-summary::before,
.thinking-details[open] .thinking-summary::before {
  transform: rotate(90deg) !important;
}

#side-by-side-page .thinking-summary:hover,
.thinking-summary:hover {
  color: #374151 !important;
}

.thinking-title {
  font-size: 13px !important;
  font-weight: 500 !important;
}

#side-by-side-page .thinking-content,
.thinking-content {
  margin-top: 12px !important;
  padding-top: 0 !important;
  border-top: none !important;
  font-size: 13px !important;
  max-height: 300px !important;
  overflow-y: auto !important;
}

#side-by-side-page .dots,
.dots {
  display: flex !important;
  gap: 4px !important;
  padding: 8px 0 !important;
}

#side-by-side-page .dots span,
.dots span {
  width: 5px !important;
  height: 5px !important;
  background: #aaa !important;
  border-radius: 50% !important;
  animation: bounce 1.4s infinite !important;
}

#side-by-side-page .dots span:nth-child(2),
.dots span:nth-child(2) {
  animation-delay: 0.2s !important;
}

#side-by-side-page .dots span:nth-child(3),
.dots span:nth-child(3) {
  animation-delay: 0.4s !important;
}

@keyframes bounce {
  0%, 60%, 100% {
    transform: translateY(0);
  }
  30% {
    transform: translateY(-6px);
  }
}
</style>

<style scoped>

.page-container {
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
  scrollbar-width: none; /* Firefox */
  -ms-overflow-style: none; /* IE and Edge */
}

/* 隐藏滚动条但保持滚动功能 */
.header-content::-webkit-scrollbar {
  display: none; /* Chrome, Safari and Opera */
}

.mode-select {
  opacity: 0.6;
}

.vs-label {
  font-size: 14px;
  color: #999;
  font-weight: 500;
  flex-shrink: 0;
  white-space: nowrap;
}

.login-button {
  padding: 6px 20px;
  background: #000;
  color: #fff;
  border: none;
  border-radius: 8px;
  cursor: pointer;
  font-size: 13px;
  font-weight: 500;
  transition: background 0.15s;
}

.login-button:hover {
  background: #333;
}

.chat-section {
  flex: 1;
  display: flex;
  flex-direction: column;
  position: relative;
  overflow: hidden;
}

.welcome-view {
  flex: 1;
  display: flex;
  align-items: center;
  justify-content: center;
  padding-bottom: 160px;
}

.main-title {
  font-size: 40px;
  font-weight: 400;
  color: #6b7280;
  margin: 0;
}

.messages-wrapper {
  flex: 1;
  overflow-y: auto;
  padding: 40px 40px 200px;
  max-width: 100%;
}

.msg-block {
  margin-bottom: 40px;
  width: 100%;
}

.user-msg {
  margin-bottom: 24px;
  display: flex;
  justify-content: flex-end;
  width: 100%;
}

.user-bubble {
  display: inline-block;
  background: #f3f4f6;
  padding: 12px 20px;
  border-radius: 16px;
  max-width: 70%;
  font-size: 15px;
  line-height: 1.6;
  color: #1f2937;
  box-shadow: 0 1px 2px rgba(0, 0, 0, 0.05);
}

/* .ai-responses、.response-grid、.col-header、.model-tag、.metrics、.col-body、.text-box、.err-box、.dots 等样式已在全局样式中定义 */

.input-zone {
  position: absolute;
  bottom: 0;
  left: 0;
  right: 0;
  background: linear-gradient(180deg, transparent, #fff 20%);
  padding: 50px 50px 30px;
}

.input-card {
  max-width: 800px;
  margin: 0 auto;
  background: #fff;
  border: 1px solid #d4d4d4;
  border-radius: 14px;
  box-shadow: 0 2px 6px rgba(0, 0, 0, 0.04);
  transition: all 0.2s;
}

.input-card:focus-within {
  border-color: #1890ff;
  box-shadow: 0 3px 10px rgba(24, 144, 255, 0.1);
}

.text-input {
  width: 100%;
  border: none;
  outline: none;
  padding: 15px 18px;
  font-size: 14px;
  line-height: 1.5;
  resize: none;
  min-height: 22px;
  max-height: 180px;
  font-family: inherit;
}

.bottom-bar {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 7px 10px 7px 14px;
  border-top: 1px solid #f0f0f0;
}

.left-tools {
  display: flex;
  gap: 3px;
}

.tool-icon {
  width: 30px;
  height: 30px;
  border: none;
  background: transparent;
  border-radius: 5px;
  cursor: pointer;
  display: flex;
  align-items: center;
  justify-content: center;
  color: #777;
  transition: background 0.12s;
}

.tool-icon:hover {
  background: #f5f5f5;
}

.send-icon {
  width: 34px;
  height: 34px;
  border: none;
  background: #777;
  color: #fff;
  border-radius: 7px;
  cursor: pointer;
  display: flex;
  align-items: center;
  justify-content: center;
  transition: background 0.12s;
}

.send-icon:hover:not(:disabled) {
  background: #1890ff;
}

.send-icon:disabled {
  background: #ddd;
  cursor: not-allowed;
}

.user-info {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 6px 12px;
  border-radius: 8px;
  background: #f5f5f5;
}

.username {
  font-size: 13px;
  color: #374151;
}

/* 最大化对话框样式 */
.expanded-content {
  max-height: 70vh;
  overflow-y: auto;
}

.expanded-header {
  display: flex;
  align-items: center;
  gap: 12px;
  margin-bottom: 20px;
  padding-bottom: 16px;
  border-bottom: 2px solid #e5e7eb;
}

.expanded-icon {
  width: 32px;
  height: 32px;
  border-radius: 6px;
  object-fit: contain;
}

.expanded-header h3 {
  margin: 0;
  font-size: 20px;
  font-weight: 600;
  color: #1f2937;
}

.expanded-body {
  font-size: 16px;
}

/* 思考过程样式已在全局样式中定义 */

/* 评分区域样式 */
.ai-responses-wrapper {
  width: 100%;
}

.rating-section {
  margin-top: 20px;
  display: flex;
  justify-content: center;
  width: 100%;
}

.rating-buttons {
  display: flex;
  gap: 12px;
  padding: 16px;
  background: #fafafa;
  border-radius: 12px;
  border: 1px solid #e5e7eb;
}

.rating-btn {
  padding: 10px 20px;
  background: #ffffff;
  border: 1.5px solid #d1d5db;
  border-radius: 8px;
  font-size: 14px;
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
</style>
