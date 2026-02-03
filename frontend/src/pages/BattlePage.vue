<template>
  <div id="battle-page" class="page-container">
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
          <a-select-option value="prompt-lab">
            <ExperimentOutlined style="margin-right: 8px" />
            提示词实验
          </a-select-option>
          <a-select-option value="battle">
            <TrophyOutlined style="margin-right: 8px" />
            匿名对比
          </a-select-option>
        </a-select>

        <!-- 匿名模式下显示匿名标识（顶部始终只显示匿名标识，不显示真实模型名） -->
        <div class="anonymous-model-tag">模型A</div>
        <span class="vs-label">vs</span>
        <div class="anonymous-model-tag">模型B</div>

      </div>
    </header>

    <!-- 聊天区域 -->
    <div class="chat-section" style="position: relative; padding-bottom: 200px;">
      <!-- 欢迎界面 -->
      <div v-if="messages.length === 0" class="welcome-view">
        <h1 class="main-title">{{ isImageMode ? '匿名图片生成对比' : '匿名模型对比' }}</h1>
        <p class="subtitle">{{ isImageMode ? '盲测图像生成能力，发现最优模型' : '公平对比模型能力，避免品牌偏见' }}</p>
      </div>

      <!-- 消息列表 -->
      <div v-else class="messages-wrapper" ref="messagesWrapper">
        <div v-for="(msg, idx) in messages" :key="idx" class="msg-block">
          <!-- 用户消息 - 右对齐 -->
          <div v-if="msg.type === 'user'" class="user-msg">
            <div v-if="msg.imageUrls && msg.imageUrls.length" class="user-images">
              <img
                v-for="(url, imgIdx) in msg.imageUrls"
                :key="url + imgIdx"
                :src="url"
                alt="用户图片"
                class="clickable-image"
                @click="expandImage(url)"
              />
            </div>
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
                    <!-- 匿名模式下隐藏图标，揭晓后显示 -->
                    <img
                      v-if="isMessageRevealed(msg) && (getRealModelName(resp.modelName) || resp.realModelName)"
                      :src="getProviderIcon(getRealModelName(resp.modelName) || resp.realModelName || '')"
                      :alt="getModelName(getRealModelName(resp.modelName) || resp.realModelName || '')"
                      class="model-icon"
                      @error="(e) => (e.target as HTMLImageElement).src = getDefaultIconUrl()"
                    />
                    <span class="model-tag">{{ resp.modelName }}</span>
                    <!-- 如果已揭晓或有真实模型名，显示真实模型名称 -->
                    <span v-if="isMessageRevealed(msg) && (getRealModelName(resp.modelName) || (resp as any).realModelName)" class="real-model-name">
                      ({{ getModelName(getRealModelName(resp.modelName) || (resp as any).realModelName || '') }})
                    </span>
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

                      <!-- 成本（揭晓后显示） -->
                      <span v-if="isMessageRevealed(msg) && resp.cost" class="metric-item">
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
                        @click="expandResponse(resp.modelName, resp.fullContent || '', (resp as any).realModelName, msg.messageIndex)"
                      >
                        <ExpandOutlined />
                      </button>
                    </div>
                  </div>
                </div>

                <div class="col-body">
                  <!-- 错误提示 -->
                  <div v-if="resp.hasError" class="error-message">
                    <div class="err-box">
                      ❌ 调用失败: {{ resp.error || '未知错误' }}
                    </div>
                  </div>
                  <!-- 正常内容 -->
                  <template v-else>
                    <!-- 联网搜索信息 -->
                    <div v-if="resp.toolsUsed && parseToolsUsed(resp.toolsUsed)?.webSearch?.enabled" class="web-search-info">
                      <div class="web-search-badge">
                        <GlobalOutlined class="web-search-icon" />
                        <span class="web-search-text">
                          已使用联网搜索
                          <template v-if="parseToolsUsed(resp.toolsUsed)?.webSearch?.query">
                            · 关键词: "{{ parseToolsUsed(resp.toolsUsed)?.webSearch?.query }}"
                          </template>
                        </span>
                      </div>
                      <div v-if="parseToolsUsed(resp.toolsUsed)?.webSearch?.sources?.length" class="web-search-sources">
                        <a
                          v-for="(source, sourceIdx) in parseToolsUsed(resp.toolsUsed)?.webSearch?.sources?.slice(0, 5)"
                          :key="sourceIdx"
                          :href="source.url"
                          target="_blank"
                          rel="noopener noreferrer"
                          class="web-search-source-link"
                          :title="source.url"
                        >
                          <LinkOutlined />
                          {{ source.title || `来源 ${sourceIdx + 1}` }}
                        </a>
                      </div>
                    </div>
                    <!-- 思考过程 -->
                    <details v-if="resp.hasReasoning && resp.reasoning" class="thinking-details" open>
                      <summary class="thinking-summary">
                        <span class="thinking-title">
                          {{ !resp.done && !resp.fullContent ? '正在思考...' : `思考了 ${resp.thinkingTime || Math.max(1, Math.min((resp.reasoning?.length || 0) / 200, 60))} 秒` }}
                        </span>
                      </summary>
                      <div class="thinking-content">
                        <MarkdownRenderer :content="resp.reasoning || ''" />
                      </div>
                    </details>
                    <!-- 思考完成但内容还未返回时的过渡提示 -->
                    <div v-if="resp.hasReasoning && resp.reasoning && !resp.fullContent && !resp.done && !resp.generatedImages?.length" class="generating-content-hint">
                      正在生成回答...
                    </div>
                    <!-- 图片生成结果 -->
                    <template v-if="resp.generatedImages && resp.generatedImages.length > 0">
                      <div class="generated-images">
                        <div
                          v-for="(img, imgIdx) in resp.generatedImages"
                          :key="`${resp.modelName}-img-${imgIdx}`"
                          class="generated-image-item"
                        >
                          <img
                            :src="img.url"
                            :alt="`生成的图片 ${imgIdx + 1}`"
                            class="generated-image"
                            @click="expandImage(img.url)"
                          />
                          <div v-if="img.totalTokens || img.cost" class="image-metrics">
                            <span v-if="img.totalTokens">📊 {{ img.totalTokens }}t</span>
                            <span v-if="img.cost">💰 ${{ img.cost.toFixed(4) }}</span>
                          </div>
                        </div>
                      </div>
                    </template>
                    <!-- 文本回答 -->
                    <template v-else>
                      <MarkdownRenderer :content="resp.fullContent || ''" />
                    </template>
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
              v-if="msg.responses && msg.responses.every((r: any) => r.done) && msg.responses.length >= 2"
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
                  {{ resp.modelName }} 更好
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
            :placeholder="isImageMode ? '输入图片生成提示词...' : (isExistingConversation ? '继续对话...' : '输入你的问题...')"
            :disabled="isLoading"
            @keydown.enter.exact.prevent="sendMessage"
            @paste="handlePaste"
            class="text-input"
          ></textarea>
          
          <!-- 图像生成模式下显示添加图片按钮 -->
          <div v-if="isImageMode && selectedImageUrls.length === 0" class="add-image-btn" @click="triggerSelectImages">
            <FileImageOutlined />
            <span>添加图片</span>
          </div>

          <div v-if="selectedImageUrls.length > 0" class="image-preview-list">
            <div
              v-for="(item, index) in selectedImageUrls"
              :key="item.url + index"
              class="image-preview-item"
            >
              <div class="image-preview-wrapper">
                <!-- 上传中：显示占位图和动画 -->
                <div v-if="item.status === 'uploading'" class="image-placeholder">
                  <div class="image-placeholder-icon">
                    <FileImageOutlined />
                  </div>
                  <div class="image-upload-spinner">
                    <div class="spinner"></div>
                  </div>
                  <div class="image-placeholder-text">上传中...</div>
                </div>
                <!-- 上传失败：显示错误提示 -->
                <div v-else-if="item.status === 'failed'" class="image-placeholder image-placeholder-error">
                  <div class="image-placeholder-icon">
                    <CloseOutlined />
                  </div>
                  <div class="image-placeholder-text">上传失败</div>
                </div>
                <!-- 上传完成：显示实际图片 -->
                <img v-else :src="item.url" alt="预览图片" class="image-preview-thumb" />
              </div>
              <a-button
                type="text"
                danger
                size="small"
                class="image-delete-btn"
                @click="removeImage(index)"
                :disabled="item.status === 'uploading'"
              >
                <template #icon>
                  <CloseOutlined />
                </template>
              </a-button>
            </div>
          </div>

          <div class="bottom-bar">
            <div class="left-tools">
              <button
                class="tool-icon"
                :class="{ 'tool-icon-active': webSearchEnabled }"
                :title="webSearchEnabled ? '关闭联网搜索' : '开启联网搜索'"
                @click="toggleWebSearch"
              >
                <GlobalOutlined />
              </button>
              <button
                class="tool-icon"
                :class="{ 'tool-icon-active': isImageMode }"
                :title="isImageMode ? '关闭图像生成模式' : '开启图像生成模式'"
                @click="toggleImageMode"
              >
                <FileImageOutlined />
              </button>
              <button 
                class="tool-icon" 
                title="切换到代码模式匿名对比"
                @click="switchToCodeModeBattle"
              >
                <CodeOutlined />
              </button>
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
        <input
          ref="imageInputRef"
          type="file"
          accept="image/*"
          multiple
          style="display: none"
          @change="handleImageChange"
        />
      </div>
    </div>

    <!-- 最大化对话框 -->
    <a-modal
      :open="!!expandedResponse"
      :title="expandedResponse ? (expandedResponse.isRevealed && (getRealModelName(expandedResponse.modelName) || (expandedResponse as any).realModelName) ? getModelName(getRealModelName(expandedResponse.modelName) || (expandedResponse as any).realModelName || '') : expandedResponse.modelName) : ''"
      width="90%"
      :footer="null"
      @cancel="closeExpanded"
    >
      <div v-if="expandedResponse" class="expanded-content">
        <div class="expanded-header">
          <img
            v-if="expandedResponse.isRevealed && (getRealModelName(expandedResponse.modelName) || (expandedResponse as any).realModelName)"
            :src="getProviderIcon(getRealModelName(expandedResponse.modelName) || (expandedResponse as any).realModelName || '')"
            :alt="getModelName(getRealModelName(expandedResponse.modelName) || (expandedResponse as any).realModelName || '')"
            class="expanded-icon"
          />
          <h3>{{ expandedResponse.modelName }}</h3>
          <span v-if="expandedResponse.isRevealed && (getRealModelName(expandedResponse.modelName) || (expandedResponse as any).realModelName)" class="real-model-name">
            ({{ getModelName(getRealModelName(expandedResponse.modelName) || (expandedResponse as any).realModelName || '') }})
          </span>
        </div>
        <div class="expanded-body">
          <MarkdownRenderer :content="expandedResponse.content" />
        </div>
      </div>
    </a-modal>

    <!-- 图片放大预览弹窗 -->
    <a-modal
      :open="!!expandedImageUrl"
      :footer="null"
      :width="'90%'"
      :bodyStyle="{ padding: '0', display: 'flex', justifyContent: 'center', alignItems: 'center', background: '#000' }"
      :centered="true"
      @cancel="closeExpandedImage"
    >
      <img
        v-if="expandedImageUrl"
        :src="expandedImageUrl"
        alt="放大图片"
        class="expanded-image-preview"
      />
    </a-modal>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, onMounted, onUnmounted, watch, nextTick } from 'vue'
import { useRouter, useRoute } from 'vue-router'
import { message, notification } from 'ant-design-vue'
import { useLoginUserStore } from '@/stores/loginUser'
import { useLoginModalStore } from '@/stores/loginModal'
import {
  SearchOutlined,
  SendOutlined,
  SwapOutlined,
  ExperimentOutlined,
  TrophyOutlined,
  CopyOutlined,
  ExpandOutlined,
  CodeOutlined,
  GlobalOutlined,
  FileImageOutlined,
  CloseOutlined,
  LinkOutlined,
} from '@ant-design/icons-vue'
import { listModels, getAllModels, type ModelVO } from '@/api/modelController'
import { getConversationMessages, getConversation, type StreamChunkVO, getBattleModelMapping, type ToolsUsedInfo } from '@/api/conversationController'
import { generateImageStream, type GeneratedImageVO } from '@/api/imageController'
import { addRating, getRatingsByConversationId, type RatingVO } from '@/api/ratingController'
import { createPostSSE } from '@/utils/sseClient'
import { API_BASE_URL } from '@/config/env'
import MarkdownRenderer from '@/components/MarkdownRenderer.vue'

interface Msg {
  type: 'user' | 'assistant'
  content?: string
  responses?: (StreamChunkVO & { generatedImages?: GeneratedImageVO[] })[]
  messageIndex?: number
  rating?: RatingVO
  imageUrls?: string[]
}

const router = useRouter()
const route = useRoute()
const loginUserStore = useLoginUserStore()
const loginModalStore = useLoginModalStore()
const loginUser = computed(() => loginUserStore.loginUser)

const currentMode = ref('battle')
const selectedModels = ref<(string | undefined)[]>([undefined, undefined])
const userInput = ref('')
const isLoading = ref(false)
const loadingModels = ref(false)
const modelOptions = ref<{ label: string; value: string }[]>([])
const messages = ref<Msg[]>([])
const sse = ref<any>(null)
const isNewConversation = ref(false)
const expandedResponse = ref<{ modelName: string; content: string; realModelName?: string; isRevealed?: boolean } | null>(null)
const expandedImageUrl = ref<string | null>(null)
const messagesWrapper = ref<HTMLElement | null>(null)
const revealed = ref(false)
const modelMapping = ref<Record<string, string>>({})
// 记录每个消息轮次的揭晓状态（messageIndex -> boolean）
const revealedMessageIndexes = ref<Set<number>>(new Set())

const currentPage = ref(1)
const pageSize = 50
const hasMore = ref(true)
const currentSearchText = ref<string>()

// 联网搜索状态（匿名模式简单按开关控制）
const WEB_SEARCH_STORAGE_KEY = 'ai-test:battle:webSearchEnabled'
const webSearchEnabled = ref(localStorage.getItem(WEB_SEARCH_STORAGE_KEY) === 'true')

// 图片输入（多模态理解 / 参考图）
interface ImageItem {
  url: string
  status: 'uploading' | 'completed' | 'failed'
  file?: File
}
const selectedImageUrls = ref<ImageItem[]>([])
const imageUploading = ref(false)
const imageInputRef = ref<HTMLInputElement | null>(null)

// 输入模式：text=文本/多模态对话，image=图片生成
const inputMode = ref<'text' | 'image'>('text')
const isImageMode = computed(() => inputMode.value === 'image')

const isExistingConversation = computed(() => {
  return !!route.query.conversationId
})

const allResponsesDone = computed(() => {
  if (messages.value.length === 0) return false
  const lastAssistantMsg = messages.value.filter(m => m.type === 'assistant').pop()
  if (!lastAssistantMsg || !lastAssistantMsg.responses) return false
  return lastAssistantMsg.responses.length >= 2 && lastAssistantMsg.responses.every((r: any) => r.done)
})

// 判断某个消息轮次是否已揭晓
const isMessageRevealed = (msg: any): boolean => {
  if (!msg || msg.type !== 'assistant' || msg.messageIndex === undefined) {
    return false
  }
  // 如果该消息轮次在revealedMessageIndexes中，说明已揭晓
  return revealedMessageIndexes.value.has(msg.messageIndex)
}

const getAnonymousName = (index: number): string => {
  const names = ['模型A', '模型B', '模型C', '模型D', '模型E', '模型F', '模型G', '模型H']
  return names[index] || `模型${String.fromCharCode(65 + index)}`
}

const handleModeChange = (mode: string) => {
  router.push(`/${mode}`)
}

const canSend = computed(() => {
  return userInput.value.trim() && !isLoading.value && !hasUploadingImages()
})

const getRealModelName = (anonymousName: string): string | undefined => {
  return modelMapping.value[anonymousName]
}

// 解析 toolsUsed 信息
const parseToolsUsed = (toolsUsedStr: string | undefined): ToolsUsedInfo | null => {
  if (!toolsUsedStr) return null
  try {
    return JSON.parse(toolsUsedStr) as ToolsUsedInfo
  } catch (e) {
    console.warn('解析 toolsUsed 失败:', e)
    return null
  }
}

// 切换联网搜索
const toggleWebSearch = () => {
  // 如果要开启联网搜索，先关闭图像生成模式
  if (!webSearchEnabled.value && isImageMode.value) {
    inputMode.value = 'text'
  }
  webSearchEnabled.value = !webSearchEnabled.value
  localStorage.setItem(WEB_SEARCH_STORAGE_KEY, webSearchEnabled.value.toString())
  message.info(webSearchEnabled.value ? '已开启联网搜索' : '已关闭联网搜索')
}

// 切换图像生成模式
const toggleImageMode = () => {
  const wasImageMode = inputMode.value === 'image'
  inputMode.value = wasImageMode ? 'text' : 'image'
  console.log('切换图片模式:', inputMode.value)
  
  // 如果要开启图像生成模式，先关闭联网搜索
  if (inputMode.value === 'image' && webSearchEnabled.value) {
    webSearchEnabled.value = false
    localStorage.setItem(WEB_SEARCH_STORAGE_KEY, 'false')
  }
  
  message.info(inputMode.value === 'image' ? '已切换到图像生成模式' : '已切换到对话模式')
}

// 图片上传相关
const getCompletedImageUrls = (): string[] => {
  return selectedImageUrls.value
    .filter(item => item.status === 'completed')
    .map(item => item.url)
}

const hasUploadingImages = (): boolean => {
  return selectedImageUrls.value.some(item => item.status === 'uploading')
}

const triggerSelectImages = () => {
  if (isLoading.value) {
    message.warning('正在请求中，请稍后再选择图片')
    return
  }
  imageInputRef.value?.click()
}

const uploadImages = async (files: File[]) => {
  if (!files.length) {
    return
  }
  const remainingSlots = 5 - selectedImageUrls.value.length
  if (remainingSlots <= 0) {
    message.warning('最多只能选择 5 张图片')
    return
  }
  const filesToUpload = files.slice(0, remainingSlots)
  if (filesToUpload.length < files.length) {
    message.warning(`已达到图片数量上限，最多支持 5 张，本次仅上传前 ${filesToUpload.length} 张`)
  }

  try {
    imageUploading.value = true
    const { uploadImage } = await import('@/api/fileController')

    const placeholderItems: ImageItem[] = filesToUpload.map((file, index) => {
      const placeholderUrl = `placeholder://${file.name}-${Date.now()}-${index}`
      return {
        url: placeholderUrl,
        status: 'uploading',
        file
      }
    })
    selectedImageUrls.value.push(...placeholderItems)

    for (let i = 0; i < filesToUpload.length; i++) {
      const file = filesToUpload[i]
      const placeholderIndex = selectedImageUrls.value.length - filesToUpload.length + i
      try {
        const formData = new FormData()
        formData.append('file', file)
        const res = await uploadImage(formData)
        if (res?.data?.code === 0 && res.data.data?.url) {
          selectedImageUrls.value[placeholderIndex] = {
            url: res.data.data.url,
            status: 'completed'
          }
        } else {
          selectedImageUrls.value[placeholderIndex].status = 'failed'
          message.error(res?.data?.message || '图片上传失败')
        }
      } catch (error) {
        console.error('单张图片上传失败:', error)
        selectedImageUrls.value[placeholderIndex].status = 'failed'
        message.error(`图片 "${file.name}" 上传失败`)
      }
    }

    const successCount = selectedImageUrls.value.filter(item => item.status === 'completed').length
    if (successCount > 0) {
      message.success(`成功上传 ${successCount} 张图片`, 1.5)
    }
  } catch (error) {
    console.error('图片上传异常', error)
    message.error('图片上传失败，请重试')
  } finally {
    imageUploading.value = false
  }
}

const removeImage = (index: number) => {
  if (index >= 0 && index < selectedImageUrls.value.length) {
    selectedImageUrls.value.splice(index, 1)
  }
}

const handleImageChange = async (event: Event) => {
  const target = event.target as HTMLInputElement | null
  if (!target || !target.files || target.files.length === 0) {
    return
  }
  const files = Array.from(target.files)
  await uploadImages(files)
  target.value = ''
}

const handlePaste = async (event: ClipboardEvent) => {
  const clipboardData = event.clipboardData
  if (!clipboardData) {
    return
  }
  const items = clipboardData.items
  const imageFiles: File[] = []
  for (let i = 0; i < items.length; i += 1) {
    const item = items[i]
    if (item.kind === 'file' && item.type.startsWith('image/')) {
      const file = item.getAsFile()
      if (file) {
        imageFiles.push(file)
      }
    }
  }
  if (imageFiles.length === 0) {
    return
  }
  await uploadImages(imageFiles)
}

const ensureBattleModelMapping = async (conversationId: string) => {
  if (!conversationId) {
    return
  }
  if (modelMapping.value && Object.keys(modelMapping.value).length > 0) {
    return
  }
  try {
    const res: any = await getBattleModelMapping({ conversationId })
    if (res.data && res.data.code === 0 && res.data.data) {
      modelMapping.value = res.data.data.mapping || {}
    }
  } catch (error) {
    console.error('加载 Battle 模型映射失败:', error)
  }
}

const switchToCodeModeBattle = () => {
  const conversationId = route.query.conversationId as string
  if (conversationId) {
    router.push(`/code-mode?conversationId=${conversationId}&mode=battle`)
  } else {
    router.push('/code-mode?mode=battle')
  }
}

const updateModelAtIndex = (index: number, value: string | undefined) => {
  selectedModels.value[index] = value
}

const getAvailableOptionsForIndex = (currentIndex: number) => {
  const selectedByOthers = selectedModels.value
    .map((m, idx) => (idx !== currentIndex ? m : undefined))
    .filter(Boolean) as string[]

  const available = modelOptions.value.filter(option =>
    !selectedByOthers.includes(option.value)
  )

  return available
}

const addModel = () => {
  if (selectedModels.value.length < 8) {
    selectedModels.value = [...selectedModels.value, undefined]
  }
}

const removeModel = () => {
  if (selectedModels.value.length > 2) {
    selectedModels.value = selectedModels.value.slice(0, -1)
  }
}

const loadModels = async (searchText?: string, append: boolean = false) => {
  try {
    loadingModels.value = true

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
        const existingIds = new Set(modelOptions.value.map((o: any) => o.value))
        const uniqueNewOptions = newOptions.filter((o: any) => !existingIds.has(o.value))
        modelOptions.value = [...modelOptions.value, ...uniqueNewOptions]
      } else {
        modelOptions.value = newOptions
      }

      hasMore.value = models.length >= pageSize

      if (!selectedModels.value[0] && !append && modelOptions.value.length >= 2) {
        selectedModels.value = [
          modelOptions.value[0].value,
          modelOptions.value[1].value
        ]
      }
    }
  } catch (err) {
    console.error('Load models error:', err)
  } finally {
    loadingModels.value = false
  }
}

const handleSearchModel = (value: string) => {
  if (!value || value.trim() === '') {
    currentSearchText.value = undefined
    currentPage.value = 1
    loadModels(undefined, false)
  } else {
    currentSearchText.value = value
    currentPage.value = 1
    loadModels(value, false)
  }
}

const handleDropdownChange = (visible: boolean) => {
  if (!visible && currentSearchText.value) {
    currentSearchText.value = undefined
    currentPage.value = 1
    loadModels(undefined, false)
  }
}

const handlePopupScroll = (e: Event) => {
  const target = e.target as HTMLElement
  const scrollTop = target.scrollTop
  const scrollHeight = target.scrollHeight
  const clientHeight = target.clientHeight

  if (scrollHeight - scrollTop - clientHeight < 50 && hasMore.value && !loadingModels.value) {
    currentPage.value++
    loadModels(undefined, true)
  }
}

const scrollToBottom = () => {
  nextTick(() => {
    if (messagesWrapper.value) {
      messagesWrapper.value.scrollTop = messagesWrapper.value.scrollHeight
    }
  })
}

const sendMessage = async () => {
  if (!canSend.value) return

  if (!loginUser.value.id) {
    message.warning('请先登录')
    loginModalStore.openModal('login')
    return
  }

  const text = userInput.value.trim()
  userInput.value = ''
  isLoading.value = true

  const currentConversationId = route.query.conversationId as string
  if (!currentConversationId) {
    isNewConversation.value = true
  }

  const completedImageUrls = getCompletedImageUrls()
  
  // 图像生成模式
  if (isImageMode.value) {
    if (!text) {
      message.warning('请输入图片生成提示词')
      isLoading.value = false
      return
    }
    
    // 添加用户消息
    const userMsgIndex = messages.value.length
    messages.value.push({
      type: 'user',
      content: text,
      messageIndex: Math.floor(userMsgIndex / 2),
      imageUrls: completedImageUrls
    })
    // 清空已选图片
    selectedImageUrls.value = []
    
    // 添加assistant消息占位（两个匿名模型）
    const assistantMsgIndex = messages.value.length
    const initialResponses = [
      { modelName: '模型A', fullContent: '', done: false, hasError: false, generatedImages: [] as GeneratedImageVO[] },
      { modelName: '模型B', fullContent: '', done: false, hasError: false, generatedImages: [] as GeneratedImageVO[] }
    ]
    
    messages.value.push({
      type: 'assistant',
      responses: initialResponses,
      messageIndex: Math.floor(assistantMsgIndex / 2)
    })
    
    scrollToBottom()
    
    // 匿名图像生成模式：调用两次generateImageStream，使用匿名标识
    try {
      let savedConversationId: string | undefined = currentConversationId || undefined
      
      const generatePromises = ['模型A', '模型B'].map((anonymousName, modelIndex) => {
        return new Promise<void>((resolve) => {
          const isFirst = modelIndex === 0 && !currentConversationId
          
          generateImageStream(
            {
              prompt: text,
              referenceImageUrls: completedImageUrls.length > 0 ? completedImageUrls : undefined,
              count: 1,
              conversationId: savedConversationId,
              conversationType: isFirst ? 'battle' : undefined,
              variantIndex: modelIndex,
              isAnonymous: true
            },
            (chunk) => {
              const msg = messages.value[assistantMsgIndex]
              if (!msg || !msg.responses) return
              
              const idx = msg.responses.findIndex((r: any) => r.modelName === anonymousName)
              if (idx < 0) return
              
              if (chunk.type === 'thinking') {
                msg.responses[idx] = {
                  ...msg.responses[idx],
                  reasoning: chunk.fullThinking || '',
                  hasReasoning: true
                }
                messages.value = [...messages.value]
                scrollToBottom()
              } else if (chunk.type === 'image' && chunk.image) {
                const currentImages = msg.responses[idx].generatedImages || []
                msg.responses[idx] = {
                  ...msg.responses[idx],
                  generatedImages: [...currentImages, chunk.image]
                }
                messages.value = [...messages.value]
                scrollToBottom()
                
                // 保存 conversationId
                if (chunk.conversationId && !savedConversationId) {
                  savedConversationId = chunk.conversationId
                  if (!currentConversationId) {
                    router.replace({
                      path: '/battle',
                      query: { conversationId: chunk.conversationId }
                    })
                  }
                }
              } else if (chunk.type === 'done') {
                msg.responses[idx] = {
                  ...msg.responses[idx],
                  done: true
                }
                messages.value = [...messages.value]
                
                // 检查是否所有响应都完成
                const allDone = msg.responses.every((r: any) => r.done)
                if (allDone) {
                  isLoading.value = false
                  isNewConversation.value = false
                  if (savedConversationId) {
                    ensureBattleModelMapping(savedConversationId)
                    loadRatings(savedConversationId)
                  }
                }
                resolve()
              } else if (chunk.type === 'error') {
                const errorMsg = chunk.error || '图片生成失败'
                msg.responses[idx] = {
                  ...msg.responses[idx],
                  done: true,
                  hasError: true,
                  error: errorMsg
                }
                messages.value = [...messages.value]
                message.error(`${anonymousName} 图片生成失败: ${errorMsg}`, 1)
                resolve()
              }
            },
            (error) => {
              console.error(`图片生成失败 (${anonymousName}):`, error)
              const msg = messages.value[assistantMsgIndex]
              if (msg && msg.responses) {
                const idx = msg.responses.findIndex((r: any) => r.modelName === anonymousName)
                if (idx >= 0) {
                  msg.responses[idx] = {
                    ...msg.responses[idx],
                    done: true,
                    hasError: true,
                    error: error.message || '图片生成失败'
                  }
                  messages.value = [...messages.value]
                }
              }
              message.error(`${anonymousName} 图片生成失败: ${error.message || '未知错误'}`, 1)
              resolve()
            }
          )
        })
      })
      
      await Promise.all(generatePromises)
    } catch (error: any) {
      console.error('图片生成失败:', error)
      isLoading.value = false
      isNewConversation.value = false
      message.error('图片生成失败: ' + (error.message || '未知错误'))
    }
    
    return
  }
  
  // 以下是文本对话模式（原有逻辑）
  const userMsgIndex = messages.value.length
  messages.value.push({
    type: 'user',
    content: text,
    messageIndex: Math.floor(userMsgIndex / 2),
    imageUrls: completedImageUrls
  })
  // 发送后清空已选图片
  selectedImageUrls.value = []

  const assistantMsgIndex = messages.value.length
  console.log('📍 Assistant消息索引:', assistantMsgIndex, '当前消息数:', messages.value.length)

  const initialResponses = [
    { modelName: '', fullContent: '', done: false, hasError: false },
    { modelName: '', fullContent: '', done: false, hasError: false }
  ]

  const assistantMsg = {
    type: 'assistant',
    responses: initialResponses,
    messageIndex: Math.floor(assistantMsgIndex / 2)
  }
  
  messages.value.push(assistantMsg)

  console.log('✅ 添加后消息数:', messages.value.length, 'Assistant在索引:', assistantMsgIndex)
  console.log('✅ 初始化响应列表:', initialResponses.length, '个空响应槽位')
  console.log('✅ 验证消息:', messages.value.map((m: any, i: number) => ({ index: i, type: m.type, messageIndex: m.messageIndex })))
  console.log('✅ Assistant消息对象:', assistantMsg)

  scrollToBottom()

  await nextTick()
  
  console.log('✅ nextTick后消息数:', messages.value.length, 'Assistant在索引:', assistantMsgIndex)
  console.log('✅ nextTick后验证消息:', messages.value.map((m: any, i: number) => ({ index: i, type: m.type, messageIndex: m.messageIndex })))

  try {
    const url = `${API_BASE_URL}/conversation/battle/stream`

    const requestBody: any = {
      prompt: text,
      imageUrls: completedImageUrls.length > 0 ? completedImageUrls : undefined,
      stream: true,
      webSearchEnabled: webSearchEnabled.value
    }

    if (currentConversationId) {
      requestBody.conversationId = currentConversationId
    }

    sse.value = await createPostSSE(
      url,
      requestBody,
      {
        onMessage: (chunk: StreamChunkVO) => {
          console.log('📨 收到SSE chunk:', {
            conversationId: chunk.conversationId,
            modelName: chunk.modelName,
            messageIndex: chunk.messageIndex,
            contentLength: chunk.fullContent?.length || 0,
            done: chunk.done,
            currentMessagesCount: messages.value.length,
            assistantMsgIndex
          })
          
          if (chunk.conversationId && !route.query.conversationId) {
            console.log('🔄 延迟更新路由，添加conversationId:', chunk.conversationId, '当前消息数:', messages.value.length)
            setTimeout(() => {
              router.replace({
                path: '/battle',
                query: { conversationId: chunk.conversationId }
              }).then(() => {
                console.log('✅ 路由更新完成，conversationId:', chunk.conversationId, '当前消息数:', messages.value.length)
              }).catch((err) => {
                console.error('❌ 路由更新失败:', err)
              })
            }, 100)
          }

          let targetMsg: any = null
          let targetMsgIndex = -1
          
          console.log('🔍 开始查找assistant消息，当前消息数:', messages.value.length, 'assistantMsgIndex:', assistantMsgIndex)
          
          if (chunk.messageIndex !== undefined) {
            const msgByIndex = messages.value.find((m: any, idx: number) => {
              if (m.type === 'assistant' && m.messageIndex === chunk.messageIndex) {
                targetMsgIndex = idx
                return true
              }
              return false
            })
            if (msgByIndex) {
              targetMsg = msgByIndex
            }
          }
          
          if (!targetMsg) {
            for (let i = messages.value.length - 1; i >= 0; i--) {
              const m = messages.value[i]
              if (m.type === 'assistant') {
                targetMsg = m
                targetMsgIndex = i
                if (chunk.messageIndex !== undefined && targetMsg.messageIndex !== chunk.messageIndex) {
                  targetMsg.messageIndex = chunk.messageIndex
                  messages.value = [...messages.value]
                }
                break
              }
            }
          }
          
          if (!targetMsg) {
            if (assistantMsgIndex >= 0 && assistantMsgIndex < messages.value.length) {
              const msg = messages.value[assistantMsgIndex]
              console.log('🔍 检查assistantMsgIndex:', assistantMsgIndex, '消息:', msg)
              if (msg && msg.type === 'assistant') {
                targetMsg = msg
                targetMsgIndex = assistantMsgIndex
                if (chunk.messageIndex !== undefined) {
                  targetMsg.messageIndex = chunk.messageIndex
                  messages.value = [...messages.value]
                }
                console.log('✅ 通过assistantMsgIndex找到消息')
              }
            } else {
              console.warn('⚠️ assistantMsgIndex无效:', assistantMsgIndex, '消息数组长度:', messages.value.length)
            }
          }

          console.log('🔍 当前assistant消息:', targetMsg ? `存在，索引=${targetMsgIndex}，有${targetMsg.responses?.length}个响应` : `不存在，总消息数=${messages.value.length}`)
          
          if (!targetMsg || !targetMsg.responses || targetMsg.type !== 'assistant') {
            console.error('❌ assistant消息不存在或没有responses数组！', {
              targetMsg: targetMsg,
              targetMsgIndex,
              assistantMsgIndex,
              totalMessages: messages.value.length,
              isLoading: isLoading.value,
              messages: messages.value.map((m: any, i: number) => ({ index: i, type: m.type, messageIndex: m.messageIndex }))
            })
            
            if (assistantMsgIndex >= 0 && assistantMsgIndex < messages.value.length) {
              const msgAtIdx = messages.value[assistantMsgIndex]
              console.log('🔍 尝试使用assistantMsgIndex直接访问:', msgAtIdx)
              if (msgAtIdx && msgAtIdx.type === 'assistant') {
                targetMsg = msgAtIdx
                targetMsgIndex = assistantMsgIndex
                console.log('✅ 使用assistantMsgIndex找到消息')
              }
            }
            
            if (!targetMsg || !targetMsg.responses || targetMsg.type !== 'assistant') {
              return
            }
          }
          
          const msg = targetMsg

          if (!chunk.modelName) {
            console.warn('⚠️ 收到的chunk没有modelName，跳过')
            return
          }

          console.log('📨 收到SSE消息:', {
            modelName: chunk.modelName,
            contentLength: chunk.fullContent?.length || 0,
            done: chunk.done,
            hasError: chunk.hasError
          })

          // Battle模式：将真实模型名转换为匿名标识
          let displayModelName = chunk.modelName
          let realModelName = chunk.modelName
          
          if (chunk.modelName && modelMapping.value && Object.keys(modelMapping.value).length > 0) {
            // 创建反向映射：真实模型名 -> 匿名标识
            const realToAnonymousMap: Record<string, string> = {}
            Object.entries(modelMapping.value).forEach(([anonymousName, realModel]) => {
              realToAnonymousMap[realModel] = anonymousName
            })
            
            // 如果找到了对应的匿名标识，使用匿名标识作为显示名称
            if (realToAnonymousMap[chunk.modelName]) {
              displayModelName = realToAnonymousMap[chunk.modelName]
              console.log('🔐 Battle模式流式响应，转换模型名称:', chunk.modelName, '->', displayModelName)
            } else {
              // 如果找不到映射，根据响应顺序分配（第一个响应是模型A，第二个是模型B）
              const emptyIdx = msg.responses.findIndex((r: any) => !r.modelName || r.modelName === '')
              if (emptyIdx === 0) {
                displayModelName = '模型A'
              } else if (emptyIdx === 1) {
                displayModelName = '模型B'
              }
              console.log('⚠️ Battle模式流式响应，未找到映射，使用索引分配:', emptyIdx, '->', displayModelName)
            }
          }
          
          let idx = msg.responses.findIndex((r: any) => r.modelName === displayModelName || r.modelName === chunk.modelName)
          console.log('🔍 查找模型:', chunk.modelName, 'displayModelName:', displayModelName, '→ 索引:', idx)
          console.log('🔍 响应列表中的模型:', msg.responses.map((r: any) => r.modelName))
          
          if (idx >= 0) {
            const existingResp = msg.responses[idx]
            let updatedResponse = { ...existingResp, ...chunk, modelName: displayModelName }
            
            // 保护 reasoning 相关字段，防止被后续 chunk 的 undefined 覆盖
            if (existingResp.reasoning && !chunk.reasoning) {
              updatedResponse.reasoning = existingResp.reasoning
            }
            if (existingResp.hasReasoning && chunk.hasReasoning === undefined) {
              updatedResponse.hasReasoning = existingResp.hasReasoning
            }
            if (existingResp.thinkingTime && chunk.thinkingTime === undefined) {
              updatedResponse.thinkingTime = existingResp.thinkingTime
            }
            
            // 如果是Battle模式且该消息轮次已揭晓，保存真实模型名（未揭晓时不保存）
            if (msg.messageIndex !== undefined) {
              const isMsgRevealed = revealedMessageIndexes.value.has(msg.messageIndex)
              if (isMsgRevealed && realModelName) {
                updatedResponse.realModelName = realModelName
              }
            }
            
            const newResponses = [...msg.responses]
            newResponses[idx] = updatedResponse
            
            // Battle模式：确保响应顺序为模型A在前、模型B在后
            if (newResponses.length >= 2) {
              newResponses.sort((a: any, b: any) => {
                const orderA = a.modelName === '模型A' ? 0 : a.modelName === '模型B' ? 1 : 2
                const orderB = b.modelName === '模型A' ? 0 : b.modelName === '模型B' ? 1 : 2
                return orderA - orderB
              })
            }
            
            if (targetMsgIndex >= 0 && targetMsgIndex < messages.value.length) {
              const updatedMsg = { ...msg, responses: newResponses }
              messages.value[targetMsgIndex] = updatedMsg
              messages.value = [...messages.value]
              console.log('✔ 更新成功:', chunk.modelName, 'at index', idx, '→ done:', updatedResponse.done, '内容长度:', updatedResponse.fullContent?.length, 'reasoning长度:', updatedResponse.reasoning?.length)
            } else {
              console.error('❌ targetMsgIndex无效:', targetMsgIndex, '总消息数:', messages.value.length)
            }

            scrollToBottom()
          } else {
            const emptyIdx = msg.responses.findIndex((r: any) => !r.modelName || r.modelName === '')
            if (emptyIdx >= 0) {
              const newResponse = { ...chunk, modelName: displayModelName }
              
              // 如果是Battle模式且该消息轮次已揭晓，保存真实模型名（未揭晓时不保存）
              if (msg.messageIndex !== undefined) {
                const isMsgRevealed = revealedMessageIndexes.value.has(msg.messageIndex)
                if (isMsgRevealed && realModelName) {
                  newResponse.realModelName = realModelName
                }
              }
              
              const newResponses = [...msg.responses]
              newResponses[emptyIdx] = newResponse
              
              // Battle模式：确保响应顺序为模型A在前、模型B在后
              if (newResponses.length >= 2) {
                newResponses.sort((a: any, b: any) => {
                  const orderA = a.modelName === '模型A' ? 0 : a.modelName === '模型B' ? 1 : 2
                  const orderB = b.modelName === '模型A' ? 0 : b.modelName === '模型B' ? 1 : 2
                  return orderA - orderB
                })
              }
              
              if (targetMsgIndex >= 0 && targetMsgIndex < messages.value.length) {
                const updatedMsg = { ...msg, responses: newResponses }
                messages.value[targetMsgIndex] = updatedMsg
                messages.value = [...messages.value]
                console.log('✅ 填充空响应槽位:', emptyIdx, '匿名标识:', chunk.modelName, '响应数:', newResponses.length)
              } else {
                console.error('❌ targetMsgIndex无效:', targetMsgIndex, '总消息数:', messages.value.length)
              }
              
              if (chunk.fullContent && chunk.fullContent.length < 100) {
                scrollToBottom()
              }
            } else {
              console.warn('⚠️ 未找到空响应槽位，添加新响应:', chunk.modelName, '当前响应数:', msg.responses.length)
              const newResponse = { ...chunk, modelName: displayModelName }
              
              // 如果是Battle模式且该消息轮次已揭晓，保存真实模型名（未揭晓时不保存）
              if (msg.messageIndex !== undefined) {
                const isMsgRevealed = revealedMessageIndexes.value.has(msg.messageIndex)
                if (isMsgRevealed && realModelName) {
                  newResponse.realModelName = realModelName
                }
              }
              
              const newResponses = [...msg.responses, newResponse]
              
              // Battle模式：确保响应顺序为模型A在前、模型B在后
              if (newResponses.length >= 2) {
                newResponses.sort((a: any, b: any) => {
                  const orderA = a.modelName === '模型A' ? 0 : a.modelName === '模型B' ? 1 : 2
                  const orderB = b.modelName === '模型A' ? 0 : b.modelName === '模型B' ? 1 : 2
                  return orderA - orderB
                })
              }
              if (targetMsgIndex >= 0 && targetMsgIndex < messages.value.length) {
                const updatedMsg = { ...msg, responses: newResponses }
                messages.value[targetMsgIndex] = updatedMsg
                messages.value = [...messages.value]
              } else {
                console.error('❌ targetMsgIndex无效:', targetMsgIndex, '总消息数:', messages.value.length)
              }
            }
          }

          if (chunk.done) {
            if (chunk.budgetStatus && chunk.budgetStatus !== 'normal') {
              const budgetWarningKey = 'budget-warning'
              if (chunk.budgetStatus === 'exceeded') {
                notification.error({
                  key: budgetWarningKey,
                  message: '预算超出',
                  description: chunk.budgetMessage || '今日预算已用完，无法继续调用',
                  duration: 5,
                })
              } else if (chunk.budgetStatus === 'warning') {
                notification.warning({
                  key: budgetWarningKey,
                  message: '预算预警',
                  description: chunk.budgetMessage || '今日预算即将用完，请注意控制',
                  duration: 4,
                })
              }
            }

            if (targetMsgIndex >= 0 && targetMsgIndex < messages.value.length) {
              const currentMsg = messages.value[targetMsgIndex]
              if (currentMsg && currentMsg.responses) {
                const allDone = currentMsg.responses.every((r: any) => r.done)
                if (allDone) {
                  isLoading.value = false
                  if (isNewConversation.value) {
                    isNewConversation.value = false
                  }
                  const conversationId = chunk.conversationId || route.query.conversationId as string
                  if (conversationId) {
                    ensureBattleModelMapping(conversationId)
                    loadRatings(conversationId)
                  }
                  scrollToBottom()
                }
              }
            }
          }
        },
        onError: (err) => {
          console.error('SSE错误:', err)
          isLoading.value = false
          isNewConversation.value = false
          message.error('请求失败: ' + err.message)
        },
        onBusinessError: (data) => {
          isLoading.value = false
          isNewConversation.value = false
          message.error(data.message || '请求过于频繁，请稍后再试')
        },
        onComplete: () => {
          isLoading.value = false
          isNewConversation.value = false
        },
      }
    )
  } catch (error: any) {
    console.error('发送消息失败:', error)
    isLoading.value = false
    isNewConversation.value = false
    message.error('发送失败: ' + (error.message || '未知错误'))
  }
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

const copyResponse = async (content: string, modelName: string) => {
  try {
    await navigator.clipboard.writeText(content)
    message.success(`已复制 ${modelName} 的响应`)
  } catch (err) {
    message.error('复制失败')
  }
}

const expandResponse = (modelName: string, content: string, realModelName?: string, messageIndex?: number) => {
  // 判断该消息轮次是否已揭晓
  const isRevealed = messageIndex !== undefined ? revealedMessageIndexes.value.has(messageIndex) : false
  expandedResponse.value = { modelName, content, realModelName, isRevealed } as any
}

const closeExpanded = () => {
  expandedResponse.value = null
}

const expandImage = (imageUrl: string) => {
  expandedImageUrl.value = imageUrl
}

const closeExpandedImage = () => {
  expandedImageUrl.value = null
}

const isModelSelected = (msg: Msg, modelName: string, modelIndex: number) => {
  if (!msg.rating) return false

  const rating = msg.rating
  if (rating.ratingType === 'model_better' && rating.winnerModel === modelName) {
    return true
  }

  if (rating.ratingType === 'left_better' && modelIndex === 0) {
    return true
  }

  if (rating.ratingType === 'right_better' && modelIndex === (msg.responses?.length || 0) - 1) {
    return true
  }

  return false
}

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
    let winnerModel: string | undefined
    let loserModel: string | undefined

    if (ratingType === 'model_better' && winnerModelName) {
      winnerModel = winnerModelName
      const otherModels = msg.responses.filter((r: any) => r.modelName !== winnerModelName)
      if (otherModels.length > 0) {
        loserModel = otherModels[0].modelName
      }
    } else if (ratingType === 'tie' || ratingType === 'both_bad') {
      winnerModel = undefined
      loserModel = undefined
    }

    const res: any = await addRating({
      conversationId,
      messageIndex: msg.messageIndex!,
      ratingType,
      winnerModel,
      loserModel
    })

    if (res.data && res.data.code === 0) {
      const msgIndex = messages.value.findIndex(m => m === msg)
      if (msgIndex !== -1) {
        messages.value[msgIndex] = {
          ...messages.value[msgIndex],
          rating: {
            id: '',
            conversationId,
            messageIndex: msg.messageIndex!,
            userId: loginUser.value.id,
            ratingType,
            winnerModel,
            loserModel,
            createTime: new Date().toISOString()
          }
        }
      }
      // 评分成功后自动揭晓该轮模型
      if (msg.messageIndex !== undefined) {
        revealedMessageIndexes.value.add(msg.messageIndex)
      }
      // 确保有模型映射（用于显示真实模型名）
      if (conversationId) {
        await ensureBattleModelMapping(conversationId)
      }
      messages.value = [...messages.value]
      await nextTick()
      message.success('评分成功')
    }
  } catch (error) {
    console.error('评分失败:', error)
    message.error('评分失败')
  }
}

let loadingRatings = false
let loadingRatingsConversationId: string | null = null
const loadRatings = async (conversationId: string) => {
  if (loadingRatings && loadingRatingsConversationId === conversationId) {
    return
  }

  loadingRatings = true
  loadingRatingsConversationId = conversationId

  try {
    const res: any = await getRatingsByConversationId(conversationId)
    if (res.data && res.data.code === 0 && res.data.data) {
      const ratings = res.data.data as RatingVO[]
      const ratingMap = new Map<number, RatingVO>()
      ratings.forEach(rating => {
        ratingMap.set(rating.messageIndex, rating)
      })

      // 有评分的轮次视为已揭晓
      revealedMessageIndexes.value.clear()
      ratings.forEach(rating => {
        revealedMessageIndexes.value.add(rating.messageIndex)
      })

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
      messages.value = [...messages.value]
    }
  } catch (error) {
    console.error('加载评分失败:', error)
  } finally {
    loadingRatings = false
    loadingRatingsConversationId = null
  }
}

const loadConversation = async () => {
  const conversationId = route.query.conversationId as string
  if (!conversationId) return

  if (isLoading.value || isNewConversation.value) {
    console.log('⏭️ 跳过loadConversation，正在发送消息或新会话')
    return
  }

  console.log('📥 开始加载对话:', conversationId, '当前消息数:', messages.value.length)

  try {
    const convRes: any = await getConversation({ conversationId })
    if (convRes.data && convRes.data.code === 0 && convRes.data.data) {
      const conv = convRes.data.data
      // 检查是否为Battle模式（isAnonymous=true 或 conversationType=battle）
      const isBattle = conv.isAnonymous || conv.conversationType === 'battle'
      
      if (isBattle) {
        // 如果有modelMapping，解析它
        if (conv.modelMapping) {
          try {
            const parsedMapping = typeof conv.modelMapping === 'string' 
              ? JSON.parse(conv.modelMapping) 
              : conv.modelMapping
            modelMapping.value = parsedMapping
            console.log('🤖 Battle模式，恢复模型映射:', modelMapping.value)
          } catch (e) {
            console.error('解析模型映射失败:', e)
            modelMapping.value = {}
          }
        } else {
          // 如果没有modelMapping，尝试从models字段恢复
          console.warn('⚠️ Battle模式但没有modelMapping，尝试从models字段恢复')
          if (conv.models) {
            try {
              const models = typeof conv.models === 'string' 
                ? JSON.parse(conv.models) 
                : conv.models
              if (Array.isArray(models) && models.length >= 2) {
                // 创建默认映射
                modelMapping.value = {
                  '模型A': models[0],
                  '模型B': models[1]
                }
                console.log('✅ 从models字段恢复模型映射:', modelMapping.value)
              }
            } catch (e) {
              console.error('从models字段恢复映射失败:', e)
            }
          }
        }
        
        // 检查 localStorage 中是否记录过已揭晓
        const revealedConversations = JSON.parse(localStorage.getItem('battle_revealed') || '[]')
        const isRevealed = revealedConversations.includes(conversationId)
        revealed.value = isRevealed
        console.log('🔍 加载对话详情时检查localStorage - conversationId:', conversationId, 'revealed列表:', revealedConversations, 'isRevealed:', isRevealed)
      }

      if (conv.models) {
        try {
          const models = JSON.parse(conv.models)
          selectedModels.value = models.map(() => undefined)
        } catch (e) {
          console.error('解析模型列表失败:', e)
        }
      }
    }

    const msgRes: any = await getConversationMessages({ conversationId })
    if (msgRes.data && msgRes.data.code === 0 && msgRes.data.data) {
      const msgList = msgRes.data.data
      const grouped: Record<number, any> = {}

      msgList.forEach((msg: any) => {
        if (!grouped[msg.messageIndex]) {
          grouped[msg.messageIndex] = { user: null, assistants: [] }
        }
        if (msg.role === 'user') {
          grouped[msg.messageIndex].user = msg
        } else {
          grouped[msg.messageIndex].assistants.push(msg)
        }
      })

      const sorted = Object.keys(grouped).sort((a, b) => Number(a) - Number(b))
      
      if (isLoading.value || isNewConversation.value) {
        console.log('⏭️ 跳过加载历史消息，正在发送新消息')
        return
      }
      
      messages.value = []

      sorted.forEach((idx) => {
        const group = grouped[Number(idx)]
        if (group.user) {
          let imageUrls: string[] | undefined
          if (group.user.images) {
            try {
              imageUrls = JSON.parse(group.user.images)
            } catch (e) {
              console.warn('解析用户消息图片失败:', e, group.user.images)
            }
          }
          messages.value.push({
            type: 'user',
            content: group.user.content,
            messageIndex: group.user.messageIndex,
            imageUrls
          })
        }

        if (group.assistants.length > 0) {
          // 创建反向映射（真实模型名 -> 匿名标识）
          const realToAnonymousMap: Record<string, string> = {}
          if (modelMapping.value && Object.keys(modelMapping.value).length > 0) {
            Object.entries(modelMapping.value).forEach(([anonymousName, realModelName]) => {
              realToAnonymousMap[realModelName as string] = anonymousName
            })
            console.log('🔐 Battle模式反向映射:', realToAnonymousMap)
          }
          
          const responses = group.assistants.map((a: any) => {
            // Battle模式：始终使用匿名标识作为显示名称，并保存真实模型名
            let displayModelName = a.modelName
            const realModelName = a.modelName
            
            // 如果modelMapping存在，尝试转换为匿名标识
            if (modelMapping.value && Object.keys(modelMapping.value).length > 0) {
              if (realToAnonymousMap[a.modelName]) {
                // 如果找到了对应的匿名标识，使用匿名标识作为显示名称
                displayModelName = realToAnonymousMap[a.modelName]
                console.log('🔐 Battle模式，转换模型名称: {} -> {}', a.modelName, displayModelName)
              } else {
                // 如果没有找到映射，可能是旧数据或映射不完整
                console.warn('⚠️ 未找到模型映射，真实模型名:', a.modelName, '可用映射:', Object.keys(realToAnonymousMap))
                // 如果只有2个模型，按顺序分配为模型A和模型B
                if (group.assistants.length === 2) {
                  const index = group.assistants.findIndex((m: any) => m.modelName === a.modelName)
                  displayModelName = index === 0 ? '模型A' : '模型B'
                  console.log('🔄 按顺序分配匿名标识: {} -> {}', a.modelName, displayModelName)
                }
              }
            } else {
              // 如果没有modelMapping，按顺序分配为模型A和模型B
              if (group.assistants.length === 2) {
                const index = group.assistants.findIndex((m: any) => m.modelName === a.modelName)
                displayModelName = index === 0 ? '模型A' : '模型B'
                console.log('🔄 无映射，按顺序分配匿名标识: {} -> {}', a.modelName, displayModelName)
              }
            }
            
            let generatedImages: GeneratedImageVO[] | undefined
            if (a.images) {
              try {
                const imageUrls = JSON.parse(a.images) as string[]
                if (Array.isArray(imageUrls) && imageUrls.length > 0) {
                  generatedImages = imageUrls.map((url: string, idx: number) => ({
                    url,
                    modelName: a.modelName,
                    index: idx,
                    inputTokens: a.inputTokens || 0,
                    outputTokens: a.outputTokens || 0,
                    totalTokens: (a.inputTokens || 0) + (a.outputTokens || 0),
                    cost: a.cost
                  }))
                }
              } catch (e) {
                console.warn('解析助手消息图片失败:', e, a.images)
              }
            }

            const response: any = {
              modelName: displayModelName,
              fullContent: a.content,
              done: true,
              hasError: false,
              responseTimeMs: a.responseTimeMs,
              inputTokens: a.inputTokens,
              outputTokens: a.outputTokens,
              cost: a.cost,
              generatedImages,
              toolsUsed: a.toolsUsed,
              reasoning: a.reasoning,
              hasReasoning: !!(a.reasoning && a.reasoning.length > 0)
            }
            
            // 如果是Battle模式且该消息轮次已揭晓，保存真实模型名用于显示（未揭晓时不保存）
            const messageIndex = group.assistants[0].messageIndex
            const isMessageRevealed = revealedMessageIndexes.value.has(messageIndex)
            if (isMessageRevealed && realModelName) {
              response.realModelName = realModelName
            }
            
            console.log('📦 构建的response对象:', {
              displayModelName: response.modelName,
              realModelName: response.realModelName,
              messageIndex: messageIndex,
              isMessageRevealed: isMessageRevealed
            })
            
            return response
          })
          
          // Battle模式：确保响应顺序为模型A在前、模型B在后
          if (responses.length >= 2) {
            responses.sort((a: any, b: any) => {
              const orderA = a.modelName === '模型A' ? 0 : a.modelName === '模型B' ? 1 : 2
              const orderB = b.modelName === '模型A' ? 0 : b.modelName === '模型B' ? 1 : 2
              return orderA - orderB
            })
          }

          const messageIndex = group.assistants[0].messageIndex
          messages.value.push({
            type: 'assistant',
            responses,
            messageIndex: messageIndex
          })
          
          // 如果已揭晓过，标记该消息轮次为已揭晓
          if (revealed.value && modelMapping.value && Object.keys(modelMapping.value).length > 0) {
            revealedMessageIndexes.value.add(messageIndex)
          }
        }
      })

      const conversationId = route.query.conversationId as string
      if (conversationId) {
        await loadRatings(conversationId)
      }

      scrollToBottom()
    }
  } catch (error) {
    console.error('加载对话失败:', error)
  }
}

onMounted(() => {
  if (route.query.conversationId) {
    loadConversation()
  }
})

onUnmounted(() => {
  if (sse.value) {
    sse.value.close()
  }
})

watch(() => route.query.conversationId, (newId, oldId) => {
  if (newId && newId !== oldId && !isLoading.value && !isNewConversation.value) {
    console.log('🔄 Watch触发，加载对话:', newId, 'oldId:', oldId, 'isLoading:', isLoading.value, 'isNewConversation:', isNewConversation.value)
    loadConversation()
  } else if (!newId) {
    messages.value = []
    revealed.value = false
    modelMapping.value = {}
    revealedMessageIndexes.value.clear()
  }
})
</script>

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
}

.vs-label {
  color: #999;
  font-size: 14px;
  margin: 0 8px;
}

.anonymous-model-tag {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  min-width: 200px;
  height: 32px;
  padding: 0 12px;
  background: #f5f5f5;
  border: 1px solid #d9d9d9;
  border-radius: 6px;
  font-size: 14px;
  font-weight: 500;
  color: #595959;
  flex-shrink: 0;
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
  flex-direction: column;
  align-items: center;
  justify-content: center;
  padding: 40px;
}

.main-title {
  font-size: 32px;
  font-weight: 600;
  color: #1f2937;
  margin-bottom: 12px;
}

.subtitle {
  font-size: 16px;
  color: #6b7280;
}

.messages-wrapper {
  flex: 1;
  overflow-y: auto;
  padding: 24px;
  padding-bottom: 200px;
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

.user-images {
  display: flex;
  gap: 6px;
  margin-bottom: 8px;
}

.user-images img {
  width: 56px;
  height: 56px;
  border-radius: 8px;
  object-fit: cover;
  border: 1px solid #e5e7eb;
}

.clickable-image {
  cursor: pointer;
  transition: transform 0.2s, box-shadow 0.2s;
}

.clickable-image:hover {
  transform: scale(1.05);
  box-shadow: 0 4px 12px rgba(0, 0, 0, 0.15);
}

.expanded-image-preview {
  max-width: 100%;
  max-height: 80vh;
  object-fit: contain;
}

.ai-responses-wrapper {
  margin-top: 20px;
}

.response-col {
  display: flex;
  flex-direction: column;
}

.col-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 12px;
  padding-bottom: 12px;
  border-bottom: 1px solid #f0f0f0;
}

.header-left {
  display: flex;
  align-items: center;
  gap: 8px;
}

.model-icon {
  width: 24px;
  height: 24px;
  border-radius: 4px;
}

.model-tag {
  font-weight: 500;
  color: #1f2937;
}

.real-model-name {
  font-size: 12px;
  color: #6b7280;
  margin-left: 4px;
}

.real-model-name-in-tag {
  color: #8c8c8c;
  font-size: 12px;
  margin-left: 6px;
  font-weight: 400;
}

.header-right {
  display: flex;
  align-items: center;
  gap: 12px;
}

.metrics {
  display: flex;
  gap: 12px;
  font-size: 12px;
  color: #6b7280;
}

.metric-item {
  white-space: nowrap;
}

.action-buttons {
  display: flex;
  gap: 4px;
}

.action-btn {
  background: transparent;
  border: none;
  cursor: pointer;
  padding: 4px 8px;
  border-radius: 4px;
  color: #6b7280;
  transition: all 0.2s;
}

.action-btn:hover {
  background: #f3f4f6;
  color: #1f2937;
}

.col-body {
  font-size: 14px;
  line-height: 1.7;
  color: #374151;
  flex: 1;
  overflow-y: auto;
}

.error-message {
  margin: 8px 0;
}

.err-box {
  color: #dc2626;
  font-size: 13px;
  background: #fef2f2;
  padding: 8px 12px;
  border-radius: 6px;
  border: 1px solid #fecaca;
}

.dots {
  display: flex;
  gap: 4px;
  padding: 8px 0;
}

.dots span {
  width: 5px;
  height: 5px;
  background: #aaa;
  border-radius: 50%;
  animation: bounce 1.4s infinite;
}

.dots span:nth-child(2) {
  animation-delay: 0.2s;
}

.dots span:nth-child(3) {
  animation-delay: 0.4s;
}

@keyframes bounce {
  0%, 60%, 100% {
    transform: translateY(0);
  }
  30% {
    transform: translateY(-6px);
  }
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

.thinking-details {
  margin-bottom: 12px;
  border: 1px solid #e5e7eb;
  border-radius: 8px;
  padding: 12px;
  background: #f9fafb;
}

.thinking-summary {
  cursor: pointer;
  font-size: 13px;
  color: #6b7280;
  user-select: none;
  list-style: none;
}

.thinking-summary::marker {
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

.thinking-title {
  font-size: 13px;
  font-weight: 500;
}

.thinking-content {
  margin-top: 12px;
  padding-top: 0;
  border-top: none;
  font-size: 13px;
  max-height: 300px;
  overflow-y: auto;
}

.generating-content-hint {
  color: #6b7280;
  font-size: 13px;
  padding: 8px 0;
  display: flex;
  align-items: center;
  gap: 8px;
}

.generating-content-hint::before {
  content: '';
  width: 12px;
  height: 12px;
  border: 2px solid #e5e7eb;
  border-top-color: #6b7280;
  border-radius: 50%;
  animation: spin 0.8s linear infinite;
}

@keyframes spin {
  to {
    transform: rotate(360deg);
  }
}

/* 联网搜索信息样式（参考模型对比页面） */
.web-search-info {
  background: linear-gradient(135deg, #f0f9ff 0%, #e6f7ff 100%);
  border: 1px solid #91d5ff;
  border-radius: 8px;
  padding: 10px 14px;
  margin-bottom: 12px;
}

.web-search-badge {
  display: flex;
  align-items: center;
  gap: 6px;
  color: #1890ff;
  font-size: 13px;
  font-weight: 500;
}

.web-search-icon {
  font-size: 14px;
}

.web-search-text {
  color: #1890ff;
}

.web-search-sources {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  margin-top: 8px;
  padding-top: 8px;
  border-top: 1px dashed #91d5ff;
}

.web-search-source-link {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  padding: 4px 10px;
  background: #ffffff;
  border: 1px solid #d9d9d9;
  border-radius: 4px;
  font-size: 12px;
  color: #1890ff;
  text-decoration: none;
  max-width: 200px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  transition: all 0.2s;
}

.web-search-source-link:hover {
  background: #e6f7ff;
  border-color: #1890ff;
  transform: translateY(-1px);
  box-shadow: 0 2px 4px rgba(24, 144, 255, 0.15);
}

.web-search-source-link:active {
  transform: translateY(0);
}

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

/* 图像生成模式添加图片按钮 */
.add-image-btn {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 8px;
  margin: 8px 15px;
  padding: 12px 16px;
  background: #f5f5f5;
  border: 1px dashed #d9d9d9;
  border-radius: 8px;
  cursor: pointer;
  color: #666;
  font-size: 14px;
  transition: all 0.2s;
}

.add-image-btn:hover {
  border-color: #1890ff;
  color: #1890ff;
  background: #f0f7ff;
}

/* 图片上传样式（参考模型对比页面） */
.image-preview-list {
  display: flex;
  gap: 8px;
  padding: 6px 12px 10px;
  border-top: 1px solid #f5f5f5;
  overflow-x: auto;
  max-height: 110px;
  box-sizing: border-box;
}

.image-preview-item {
  flex: 0 0 auto;
  position: relative;
}

.image-preview-wrapper {
  position: relative;
  width: 72px;
  height: 72px;
}

.image-preview-thumb {
  width: 72px;
  height: 72px;
  border-radius: 8px;
  object-fit: cover;
  border: 1px solid #e5e7eb;
  box-shadow: 0 1px 3px rgba(0, 0, 0, 0.06);
}

.image-placeholder {
  width: 72px;
  height: 72px;
  border-radius: 8px;
  border: 1px solid #e5e7eb;
  background: #f9fafb;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  position: relative;
  overflow: hidden;
}

.image-placeholder-error {
  background: #fef2f2;
  border-color: #fecaca;
}

.image-placeholder-icon {
  font-size: 24px;
  color: #9ca3af;
  margin-bottom: 4px;
}

.image-placeholder-error .image-placeholder-icon {
  color: #ef4444;
}

.image-placeholder-text {
  font-size: 11px;
  color: #6b7280;
  margin-top: 2px;
}

.image-placeholder-error .image-placeholder-text {
  color: #dc2626;
}

.image-upload-spinner {
  position: absolute;
  top: 0;
  left: 0;
  right: 0;
  bottom: 0;
  display: flex;
  align-items: center;
  justify-content: center;
  background: rgba(255, 255, 255, 0.8);
}

.spinner {
  width: 20px;
  height: 20px;
  border: 2px solid #e5e7eb;
  border-top-color: #1890ff;
  border-radius: 50%;
  animation: spin 0.8s linear infinite;
}

.image-delete-btn {
  position: absolute;
  top: -8px;
  right: -8px;
  width: 20px;
  height: 20px;
  min-width: 20px;
  padding: 0;
  display: flex;
  align-items: center;
  justify-content: center;
  background: #fff;
  border: 1px solid #e5e7eb;
  border-radius: 50%;
  color: #ff4d4f;
  font-size: 12px;
  z-index: 10;
  box-shadow: 0 2px 4px rgba(0, 0, 0, 0.1);
  transition: all 0.2s;
}

.image-delete-btn:hover {
  background: #fff2f0;
  border-color: #ff4d4f;
  transform: scale(1.1);
}

.image-delete-btn:active {
  transform: scale(0.95);
}

.bottom-bar {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 8px 12px;
  border-top: 1px solid #f0f0f0;
}

.left-tools {
  display: flex;
  gap: 4px;
}

.tool-icon {
  width: 32px;
  height: 32px;
  border: none;
  background: transparent;
  border-radius: 6px;
  cursor: pointer;
  display: flex;
  align-items: center;
  justify-content: center;
  color: #6b7280;
  transition: all 0.2s;
}

.tool-icon:hover {
  background: #f3f4f6;
  color: #1f2937;
}

.tool-icon-active {
  background: #e6f4ff;
  color: #1890ff;
}

.send-icon {
  width: 32px;
  height: 32px;
  border-radius: 6px;
  border: none;
  background: #1890ff;
  color: #fff;
  cursor: pointer;
  display: flex;
  align-items: center;
  justify-content: center;
  transition: all 0.2s;
}

.send-icon:hover:not(:disabled) {
  background: #40a9ff;
}

.send-icon:disabled {
  background: #d1d5db;
  cursor: not-allowed;
}

.expanded-content {
  padding: 20px;
}

.expanded-header {
  display: flex;
  align-items: center;
  gap: 12px;
  margin-bottom: 20px;
  padding-bottom: 16px;
  border-bottom: 1px solid #f0f0f0;
}

.expanded-icon {
  width: 32px;
  height: 32px;
  border-radius: 4px;
}

.expanded-body {
  max-height: 70vh;
  overflow-y: auto;
}

/* 生成图片展示样式（参考模型对比页面） */
.generated-images {
  display: flex;
  flex-direction: column;
  gap: 16px;
  margin-top: 8px;
}

.generated-image-item {
  display: flex;
  flex-direction: column;
  gap: 8px;
  align-items: center;
}

.generated-image {
  max-width: 512px;
  max-height: 512px;
  width: auto;
  height: auto;
  border-radius: 8px;
  border: 1px solid #e5e7eb;
  cursor: pointer;
  transition: transform 0.2s, box-shadow 0.2s;
  display: block;
  margin: 0 auto;
  object-fit: contain;
}

.generated-image:hover {
  transform: scale(1.02);
  box-shadow: 0 4px 12px rgba(0, 0, 0, 0.15);
}

.image-metrics {
  display: flex;
  gap: 12px;
  font-size: 12px;
  color: #666;
  padding: 4px 0;
}

@keyframes spin {
  to {
    transform: rotate(360deg);
  }
}
</style>
