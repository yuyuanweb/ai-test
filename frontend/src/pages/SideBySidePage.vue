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
          <a-select-option value="prompt-lab">
            <ExperimentOutlined style="margin-right: 8px" />
            提示词实验
          </a-select-option>
          <a-select-option value="battle">
            <TrophyOutlined style="margin-right: 8px" />
            匿名对比
          </a-select-option>
        </a-select>

        <!-- 动态模型选择器 (1-8个) -->
        <template v-for="(model, index) in selectedModels" :key="`model-select-${index}-${isImageMode}`">
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
                    <img
                      :src="getProviderIcon(resp.modelName)"
                      :alt="getModelName(resp.modelName)"
                      class="model-icon"
                        @error="(e) => (e.target as HTMLImageElement).src = getDefaultIconUrl()"
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
                    <!-- 图片生成结果 - 优先显示 -->
                    <template v-if="resp.generatedImages && resp.generatedImages.length > 0">
                      <!-- 图片生成的思考过程 -->
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
                      <!-- 生成的图片 -->
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
                            @error="(e) => {
                              console.error('❌ 图片加载失败:', img.url, e);
                              (e.target as HTMLImageElement).style.display = 'none';
                            }"
                            @load="() => console.log('✅ 图片加载成功:', img.url)"
                          />
                          <div v-if="img.cost || img.totalTokens" class="image-metrics">
                            <span v-if="img.totalTokens">📊 {{ img.totalTokens }}t</span>
                            <span v-if="img.cost">💰 ${{ img.cost.toFixed(4) }}</span>
                          </div>
                        </div>
                      </div>
                    </template>
                    <!-- 文本回答 - 只在没有图片时显示 -->
                    <template v-else-if="!resp.generatedImages || resp.generatedImages.length === 0">
                      <!-- 思考过程 -->
                      <details v-if="resp.hasReasoning && resp.reasoning" class="thinking-details" open>
                        <summary class="thinking-summary">
                          <span class="thinking-title">
                            {{ !resp.done && !resp.fullContent ? '正在思考...' : `思考了 ${resp.thinkingTime || calculateThinkingTime(resp.reasoning)} 秒` }}
                          </span>
                        </summary>
                        <div class="thinking-content">
                          <MarkdownRenderer :content="resp.reasoning || ''" />
                        </div>
                      </details>
                      <!-- 最终回答 - 使用Markdown渲染（流式显示） -->
                      <MarkdownRenderer v-if="resp.fullContent" :content="resp.fullContent" />
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
            @paste="handlePaste"
            class="text-input"
          ></textarea>

          <!-- 图像生成模式下显示添加图片按钮 -->
          <div v-if="isImageMode && selectedImageUrls.length === 0" class="add-image-btn" @click="triggerSelectImages">
            <FileImageOutlined />
            <span>添加图片</span>
          </div>

          <div class="bottom-bar">
            <div class="left-tools">
              <button
                class="tool-icon"
                :class="{ 'tool-icon-active': webSearchEnabled && hasToolCallingModel, 'tool-icon-disabled': !hasToolCallingModel }"
                :title="hasToolCallingModel ? (webSearchEnabled ? '关闭联网搜索' : '开启联网搜索') : '当前模型不支持联网搜索'"
                @click="toggleWebSearch"
              >
                <GlobalOutlined />
              </button>
              <button
                class="tool-icon"
                :class="{ 'tool-icon-active': isImageMode }"
                title="图片模式"
                @click="toggleImageMode"
              >
                <FileImageOutlined />
              </button>
              <button class="tool-icon" title="代码模式" @click="switchToCodeMode">
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
          <input
            ref="imageInputRef"
            type="file"
            accept="image/*"
            multiple
            style="display: none"
            @change="handleImageChange"
          />
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
  FileImageOutlined,
  CodeOutlined,
  SendOutlined,
  SwapOutlined,
  ExperimentOutlined,
  TrophyOutlined,
  CopyOutlined,
  ExpandOutlined,
  ReloadOutlined,
  CloseOutlined,
  GlobalOutlined,
  LinkOutlined,
} from '@ant-design/icons-vue'
import { listModels, type ModelVO } from '@/api/modelController'
import { getConversationMessages, getConversation, type StreamChunkVO, type ToolsUsedInfo } from '@/api/conversationController'
import { createPostSSE } from '@/utils/sseClient'
import { API_BASE_URL } from '@/config/env'
import MarkdownRenderer from '@/components/MarkdownRenderer.vue'
import { addRating, getRating, getRatingsByConversationId, type RatingVO } from '@/api/ratingController'
import { generateImage, generateImageStream, type GeneratedImageVO } from '@/api/imageController'

interface Msg {
  type: 'user' | 'assistant'
  content?: string
  responses?: StreamChunkVO[]
  messageIndex?: number  // 添加消息索引用于评分
  rating?: RatingVO  // 添加评分信息
  imageUrls?: string[] // 用户消息关联的图片
}

const router = useRouter()
const route = useRoute()
const loginUserStore = useLoginUserStore()
const loginModalStore = useLoginModalStore()
const loginUser = computed(() => loginUserStore.loginUser)

const currentMode = ref('side-by-side')
const selectedModels = ref<(string | undefined)[]>([undefined, undefined]) // 默认2个模型
// 输入模式：文本 / 图片
const inputMode = ref<'text' | 'image'>('text')
const userInput = ref('')
const isLoading = ref(false)
const loadingModels = ref(false)
const modelOptions = ref<{ label: string; value: string; supportsMultimodal?: boolean; supportsImageGen?: boolean; supportsToolCalling?: boolean }[]>([])

// 联网搜索相关状态
const WEB_SEARCH_STORAGE_KEY = 'ai-test:webSearchEnabled'
const webSearchEnabled = ref(localStorage.getItem(WEB_SEARCH_STORAGE_KEY) === 'true')
const messages = ref<Msg[]>([])
const sse = ref<any>(null)
const isNewConversation = ref(false) // 标记是否是新会话（刚生成conversationId）
const expandedResponse = ref<{ modelName: string; content: string } | null>(null) // 最大化的响应
const expandedImageUrl = ref<string | null>(null) // 放大查看的图片URL
const messagesWrapper = ref<HTMLElement | null>(null) // 消息列表容器引用

// 分页相关
const currentPage = ref(1)
const pageSize = 50
const hasMore = ref(true)
const currentSearchText = ref<string>()

// 图片相关状态（多模态输入）
interface ImageItem {
  url: string
  status: 'uploading' | 'completed' | 'failed'
  file?: File
}
const selectedImageUrls = ref<ImageItem[]>([])
const imageUploading = ref(false)
const imageInputRef = ref<HTMLInputElement | null>(null)

const isImageMode = computed(() => inputMode.value === 'image')

// 检查选中的模型中是否有支持工具调用的
const hasToolCallingModel = computed(() => {
  const validModels = selectedModels.value.filter(m => m) as string[]
  if (validModels.length === 0) return false
  
  return validModels.some(modelId => {
    const option = modelOptions.value.find(opt => opt.value === modelId)
    return option?.supportsToolCalling === true
  })
})

// 切换联网搜索
const toggleWebSearch = async () => {
  if (!hasToolCallingModel.value) {
    message.warning('当前选中的模型不支持联网搜索')
    return
  }
  
  // 如果要开启联网搜索，先关闭图像生成模式
  if (!webSearchEnabled.value && isImageMode.value) {
    inputMode.value = 'text'
    await loadModels(undefined, false, false)
  }
  
  webSearchEnabled.value = !webSearchEnabled.value
  localStorage.setItem(WEB_SEARCH_STORAGE_KEY, webSearchEnabled.value.toString())
  message.info(webSearchEnabled.value ? '已开启联网搜索' : '已关闭联网搜索')
}

// 解析工具使用信息
const parseToolsUsed = (toolsUsedStr: string | undefined): ToolsUsedInfo | null => {
  if (!toolsUsedStr) return null
  try {
    return JSON.parse(toolsUsedStr) as ToolsUsedInfo
  } catch (e) {
    console.warn('解析 toolsUsed 失败:', e)
    return null
  }
}

const toggleImageMode = async () => {
  const wasImageMode = inputMode.value === 'image'
  inputMode.value = wasImageMode ? 'text' : 'image'
  console.log('切换图片模式:', inputMode.value, '之前是:', wasImageMode, '已有会话:', isExistingConversation.value)
  
  // 保存当前模型数量（在所有情况下都保持不变）
  const originalModelCount = selectedModels.value.length
  
  // 如果要开启图像生成模式，先关闭联网搜索
  if (inputMode.value === 'image' && webSearchEnabled.value) {
    webSearchEnabled.value = false
    localStorage.setItem(WEB_SEARCH_STORAGE_KEY, 'false')
  }
  
  if (inputMode.value === 'image') {
    // 切换到图片模式
    const validModels = selectedModels.value.filter(m => m) as string[]
    const invalidModels: string[] = []
    
    // 检查每个已选择的模型是否支持图片生成
    for (const modelId of validModels) {
      const option = modelOptions.value.find(opt => opt.value === modelId)
      if (!option || !option.supportsImageGen) {
        invalidModels.push(modelId)
      }
    }
    
    // 清除不支持图片生成的模型选择（但保持模型数量不变）
    if (invalidModels.length > 0) {
      const newSelectedModels = selectedModels.value.map(model => 
        invalidModels.includes(model as string) ? undefined : model
      )
      // 确保模型数量不变
      while (newSelectedModels.length < originalModelCount) {
        newSelectedModels.push(undefined)
      }
      while (newSelectedModels.length > originalModelCount) {
        newSelectedModels.pop()
      }
      selectedModels.value = newSelectedModels
      message.info('已切换图像生成模式', 1.5)
    }
    
    // 刷新模型列表（只显示支持图片生成的模型）
    console.log('🔄 切换到图片模式，刷新模型列表（筛选图片生成模型）')
    // 等待一个 tick 确保 inputMode 和 isImageMode 已更新
    await nextTick()
    await loadModels(undefined, false, true)
    
    // 确保模型数量不变（防止 loadModels 中的自动选择逻辑修改）
    if (selectedModels.value.length !== originalModelCount) {
      console.log('🔒 恢复模型数量:', originalModelCount, '当前:', selectedModels.value.length)
      while (selectedModels.value.length < originalModelCount) {
        selectedModels.value.push(undefined)
      }
      while (selectedModels.value.length > originalModelCount) {
        selectedModels.value.pop()
      }
      console.log('✅ 恢复后模型数量:', selectedModels.value.length)
    }
    
    // 如果第一个位置没有选择模型，自动选择第一个
    if (!selectedModels.value[0] && modelOptions.value.length >= 1) {
      const newSelectedModels = [...selectedModels.value]
      newSelectedModels[0] = modelOptions.value[0].value
      selectedModels.value = newSelectedModels
      console.log('✅ 切换到图片模式后，自动选择第一个模型:', selectedModels.value)
    }
    
    // 等待下一个 tick 确保下拉框更新
    await nextTick()
  } else {
    // 取消图片模式，恢复为文本模式
    console.log('🔄 取消图片模式，刷新模型列表（显示所有模型）')
    console.log('🔍 当前 isImageMode:', isImageMode.value, 'inputMode:', inputMode.value)
    
    // 等待一个 tick 确保 inputMode 和 isImageMode 已更新
    await nextTick()
    console.log('🔍 等待后 isImageMode:', isImageMode.value, 'inputMode:', inputMode.value)
    
    // 刷新模型列表（显示所有模型，不筛选）
    // 显式传递 forceImageGenFilter: false 确保不筛选
    await loadModels(undefined, false, false)
    
    // 确保模型数量不变
    if (selectedModels.value.length !== originalModelCount) {
      console.log('🔒 恢复模型数量:', originalModelCount, '当前:', selectedModels.value.length)
      while (selectedModels.value.length < originalModelCount) {
        selectedModels.value.push(undefined)
      }
      while (selectedModels.value.length > originalModelCount) {
        selectedModels.value.pop()
      }
      console.log('✅ 恢复后模型数量:', selectedModels.value.length)
    }
    
    // 检查当前选中的模型是否是图片生成模型，如果是则清除并选择第一个
    const currentModel = selectedModels.value[0]
    if (currentModel) {
      const currentOption = modelOptions.value.find(opt => opt.value === currentModel)
      // 如果当前模型是图片生成模型，或者不在新的模型列表中，清除并选择第一个
      if (currentOption && currentOption.supportsImageGen) {
        console.log('⚠️ 当前选中的是图片生成模型，清除并选择第一个非图片生成模型')
        const newSelectedModels = [...selectedModels.value]
        // 找到第一个非图片生成模型，如果没有则选择第一个
        const firstNonImageGenModel = modelOptions.value.find(opt => !opt.supportsImageGen)
        newSelectedModels[0] = firstNonImageGenModel ? firstNonImageGenModel.value : modelOptions.value[0]?.value
        selectedModels.value = newSelectedModels
        console.log('✅ 取消图片模式后，已选择第一个模型:', selectedModels.value[0])
      } else if (!currentOption) {
        // 当前模型不在列表中，选择第一个
        console.log('⚠️ 当前选中的模型不在列表中，选择第一个')
        const newSelectedModels = [...selectedModels.value]
        newSelectedModels[0] = modelOptions.value[0]?.value
        selectedModels.value = newSelectedModels
        console.log('✅ 取消图片模式后，已选择第一个模型:', selectedModels.value[0])
      }
    } else {
      // 如果第一个位置没有选择模型，自动选择第一个
      if (modelOptions.value.length >= 1) {
        const newSelectedModels = [...selectedModels.value]
        newSelectedModels[0] = modelOptions.value[0].value
        selectedModels.value = newSelectedModels
        console.log('✅ 取消图片模式后，自动选择第一个模型:', selectedModels.value)
      }
    }
    
    // 等待下一个 tick 确保下拉框更新
    await nextTick()
  }
}

const isImageCapableOption = (option: { supportsMultimodal?: boolean; supportsImageGen?: boolean }) => {
  // 图片生成模式：检查 supportsImageGen
  // 图片输入模式：检查 supportsMultimodal
  if (isImageMode.value) {
    return !!option.supportsImageGen
  }
  return !!option.supportsMultimodal
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
    
    // 先添加占位图（上传中状态）
    const placeholderItems: ImageItem[] = filesToUpload.map((file, index) => {
      // 使用唯一的占位符 URL（基于文件名和时间戳）
      const placeholderUrl = `placeholder://${file.name}-${Date.now()}-${index}`
      return {
        url: placeholderUrl,
        status: 'uploading',
        file
      }
    })
    selectedImageUrls.value.push(...placeholderItems)
    
    // 逐个上传图片
    for (let i = 0; i < filesToUpload.length; i++) {
      const file = filesToUpload[i]
      const placeholderIndex = selectedImageUrls.value.length - filesToUpload.length + i
      
      try {
        const formData = new FormData()
        formData.append('file', file)
        const res = await uploadImage(formData)
        
        if (res?.data?.code === 0 && res.data.data?.url) {
          // 更新为完成状态
          selectedImageUrls.value[placeholderIndex] = {
            url: res.data.data.url,
            status: 'completed'
          }
        } else {
          // 更新为失败状态
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
      
      // 如果上传成功且有图片，且不是图片生成模式，自动过滤为多模态模型
      const hasCompletedImages = getCompletedImageUrls().length > 0
      if (hasCompletedImages && !isImageMode.value) {
        console.log('🖼️ 检测到图片上传，自动过滤为多模态模型')
        // 清除不支持多模态的已选模型
        const completedImageUrls = getCompletedImageUrls()
        if (completedImageUrls.length > 0) {
          const invalidModels: (string | undefined)[] = []
          selectedModels.value.forEach((model, index) => {
            if (model) {
              const modelOption = modelOptions.value.find(opt => opt.value === model)
              if (modelOption && !modelOption.supportsMultimodal) {
                invalidModels.push(model)
                selectedModels.value[index] = undefined
              }
            }
          })
          
          if (invalidModels.length > 0) {
            message.info('已切换为多模态模式，已清除不支持多模态的模型')
          }
          
          // 刷新模型列表（只显示多模态模型）
          await loadModels(undefined, false, false, true)
          
          // 如果第一个模型被清空，自动选择第一个多模态模型
          if (!selectedModels.value[0] && modelOptions.value.length > 0) {
            selectedModels.value[0] = modelOptions.value[0].value
            console.log('✅ 自动选择第一个多模态模型:', selectedModels.value[0])
          }
        }
      }
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
    console.log('🗑️ 删除图片，剩余:', selectedImageUrls.value.length)
    
    // 如果删除后没有图片了，且不是图片生成模式，刷新模型列表（显示所有模型）
    const hasCompletedImages = getCompletedImageUrls().length > 0
    if (!hasCompletedImages && !isImageMode.value) {
      console.log('🔄 已删除所有图片，刷新模型列表（显示所有模型）')
      loadModels(undefined, false, false, false)
    }
  }
}

// 获取已完成的图片 URL 列表
const getCompletedImageUrls = (): string[] => {
  return selectedImageUrls.value
    .filter(item => item.status === 'completed')
    .map(item => item.url)
}

// 检查是否有正在上传的图片
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
  // 不阻止默认行为，允许文本正常粘贴
  await uploadImages(imageFiles)
}

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
  // 后端已经根据 onlySupportsImageGen 过滤了，但为了兼容性，前端也做一次过滤
  const available = modelOptions.value.filter(option => {
    if (selectedByOthers.includes(option.value)) {
      return false
    }
    // 如果是图片生成模式，确保选项支持图片生成
    if (isImageMode.value) {
      if (!option.supportsImageGen) {
        return false
      }
    }
    // 文本模式下，不进行额外过滤（后端已经处理）
    return true
  })
  
  console.log(`📋 位置${currentIndex}过滤后可选:`, {
    总数: available.length,
    图片模式: isImageMode.value,
    选项: available.map(o => ({ label: o.label, supportsImageGen: o.supportsImageGen }))
  })

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

const loadModels = async (searchText?: string, append: boolean = false, forceImageGenFilter?: boolean, forceMultimodalFilter?: boolean) => {
  try {
    loadingModels.value = true

    // 如果是新搜索，重置分页
    if (!append) {
      currentPage.value = 1
      currentSearchText.value = searchText
    }

    // 如果显式指定了 forceImageGenFilter，使用该值；否则根据 isImageMode 判断
    const shouldFilterImageGen = forceImageGenFilter !== undefined 
        ? forceImageGenFilter 
        : (isImageMode.value ? true : undefined)
    
    // 如果显式指定了 forceMultimodalFilter，使用该值；否则根据是否有图片判断（且不是图片生成模式）
    const hasCompletedImages = getCompletedImageUrls().length > 0
    const shouldFilterMultimodal = forceMultimodalFilter !== undefined
        ? forceMultimodalFilter
        : (!isImageMode.value && hasCompletedImages ? true : undefined)
    
    console.log('📡 加载模型列表:', {
      isImageMode: isImageMode.value,
      inputMode: inputMode.value,
      forceImageGenFilter,
      forceMultimodalFilter,
      onlySupportsImageGen: shouldFilterImageGen,
      onlySupportsMultimodal: shouldFilterMultimodal,
      hasCompletedImages
    })

    const res: any = await listModels({
      pageNum: currentPage.value,
      pageSize,
      searchText: currentSearchText.value || undefined,
      onlySupportsImageGen: shouldFilterImageGen,
      onlySupportsMultimodal: shouldFilterMultimodal
    })

    if (res.data && res.data.code === 0 && res.data.data && res.data.data.records) {
      const models = res.data.data.records as ModelVO[]
      const newOptions = models.map((m) => ({
        label: m.name,
        value: m.id,
        supportsMultimodal: m.supportsMultimodal,
        supportsImageGen: m.supportsImageGen,
        supportsToolCalling: m.supportsToolCalling,
      }))

      if (append) {
        // 追加模式：去重后添加
        const existingIds = new Set(modelOptions.value.map((o: any) => o.value))
        const uniqueNewOptions = newOptions.filter((o: any) => !existingIds.has(o.value))
        modelOptions.value = [...modelOptions.value, ...uniqueNewOptions]
      } else {
        // 覆盖模式：替换列表
        console.log('🔄 更新模型选项列表:', {
          模式: isImageMode.value ? '图片生成' : (shouldFilterMultimodal ? '多模态' : '文本'),
          数量: newOptions.length,
          选项: newOptions.map(o => ({ 
            label: o.label, 
            supportsImageGen: o.supportsImageGen,
            supportsMultimodal: o.supportsMultimodal
          }))
        })
        modelOptions.value = newOptions
      }

      // 判断是否还有更多数据
      hasMore.value = models.length >= pageSize
      console.log('模型加载:', append ? '追加' : '初始', newOptions.length, '个，总计', modelOptions.value.length)

      // 如果当前没有选择模型，且不是已有会话，自动选择默认模型
      // 注意：这里只在初始加载时执行，切换模式时的自动选择在 toggleImageMode 中处理
      if (!append && !isExistingConversation.value && modelOptions.value.length >= 1) {
        const newSelectedModels = [...selectedModels.value]
        // 第一个模型槽位为空时，选择列表第一个
        if (!newSelectedModels[0]) {
          newSelectedModels[0] = modelOptions.value[0].value
        }
        // 第二个模型槽位为空时，选择列表第二个（如果有），否则选择第一个
        if (newSelectedModels.length >= 2 && !newSelectedModels[1] && modelOptions.value.length >= 1) {
          newSelectedModels[1] = modelOptions.value.length >= 2 
            ? modelOptions.value[1].value 
            : modelOptions.value[0].value
        }
        selectedModels.value = newSelectedModels
        console.log('✅ 初始加载，自动选择模型:', selectedModels.value)
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


const switchToCodeMode = () => {
  router.push('/code-mode')
}

const sendMessage = async () => {
  if (!canSend.value) return

  // 检查是否登录
  if (!loginUser.value.id) {
    message.warning('请先登录')
    loginModalStore.openModal('login')
    return
  }

  const text = userInput.value.trim()
  userInput.value = ''
  isLoading.value = true

  // 获取已选择的有效模型
  const validModels = selectedModels.value.filter(m => m) as string[]
  console.log('🎯 准备发送消息，选中的模型:', validModels)
  console.log('🎯 模型数量:', validModels.length)

  // 图片生成模式
  if (isImageMode.value) {
    if (validModels.length === 0) {
      message.warning('请至少选择一个支持图片生成的模型')
      isLoading.value = false
      return
    }
    if (!text) {
      message.warning('请输入图片生成提示词')
      isLoading.value = false
      return
    }

  // 检查是否有正在上传的图片
  if (hasUploadingImages()) {
    message.warning('请等待图片上传完成后再发送')
    isLoading.value = false
    return
  }

  // 先保存图片URL（在清空之前）
  const completedImageUrls = getCompletedImageUrls()

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

    // 添加assistant消息占位
    const assistantMsgIndex = messages.value.length
    const initialResponses = validModels.map(model => ({
      modelName: model,
      fullContent: '',
      done: false,
      hasError: false,
      generatedImages: [] as any[]
    }))

    messages.value.push({
      type: 'assistant',
      responses: initialResponses,
      messageIndex: Math.floor(assistantMsgIndex / 2),
      rating: undefined
    })

    scrollToBottom()

    // 获取当前会话ID（如果有）
    const currentConversationId = route.query.conversationId as string

    // 为每个模型调用图片生成流式接口
    try {
      // 用于存储第一个返回的 conversationId
      let savedConversationId: string | undefined = undefined
      
      const generatePromises = validModels.map((model, modelIndex) => {
        return new Promise<void>((resolve) => {
          const isFirst = modelIndex === 0 && !currentConversationId
          
          generateImageStream(
            {
              model,
              prompt: text,
              referenceImageUrls: completedImageUrls.length > 0 ? completedImageUrls : undefined,
              count: 1,
              conversationId: savedConversationId || currentConversationId || undefined,
              models: isFirst ? validModels : undefined,
              conversationType: isFirst ? 'side_by_side' : undefined,
              variantIndex: modelIndex
            },
            (chunk: API.ImageStreamChunkVO) => {
              const msg = messages.value[assistantMsgIndex]
              if (!msg || !msg.responses) return
              
              const idx = msg.responses.findIndex((r: any) => r.modelName === model)
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
                      path: '/side-by-side',
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
                message.error(`${model} 图片生成失败: ${errorMsg}`, 1)
                resolve()
              }
            },
            (error) => {
              console.error(`图片生成失败 (${model}):`, error)
              const msg = messages.value[assistantMsgIndex]
              if (msg && msg.responses) {
                const idx = msg.responses.findIndex((r: any) => r.modelName === model)
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
              message.error(`${model} 图片生成失败: ${error.message || '未知错误'}`, 1)
              resolve()
            },
            () => {
              console.log(`🖼️ ${model} 流式响应完成`)
            }
          )
        })
      })

      await Promise.all(generatePromises)
      isLoading.value = false
    } catch (err: any) {
      console.error('图片生成异常:', err)
      isLoading.value = false
      message.error('图片生成失败: ' + err.message)
    }
    return
  }

  // 如果当前没有conversationId，说明是新会话
  const currentConversationId = route.query.conversationId as string
  if (!currentConversationId) {
    isNewConversation.value = true
    console.log('🆕 标记为新会话')
  }

  // 检查是否有正在上传的图片
  if (hasUploadingImages()) {
    message.warning('请等待图片上传完成后再发送')
    isLoading.value = false
    return
  }

  // 先保存图片URL（在清空之前）
  const completedImageUrls = getCompletedImageUrls()

  // 添加用户消息
  const userMsgIndex = messages.value.length
  messages.value.push({
    type: 'user',
    content: text,
    messageIndex: Math.floor(userMsgIndex / 2),  // 每两条消息（user+assistant）为一轮
    imageUrls: completedImageUrls
  })

  // 清空已选图片
  selectedImageUrls.value = []

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
    // 联网搜索：只有在有支持工具调用的模型时才启用
    const effectiveWebSearch = webSearchEnabled.value && hasToolCallingModel.value
    console.log('📤 发送Side-by-Side请求:', { 
      conversationId: currentConversationId, 
      models: validModels, 
      prompt: text, 
      imageUrls: completedImageUrls,
      imageCount: completedImageUrls.length,
      webSearchEnabled: effectiveWebSearch
    })

    sse.value = await createPostSSE(
      url,
      {
        conversationId: currentConversationId,
        models: validModels,
        prompt: text,
        imageUrls: completedImageUrls.length > 0 ? completedImageUrls : undefined,
        stream: true,
        webSearchEnabled: effectiveWebSearch
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

          // 如果chunk中包含messageIndex，更新消息的messageIndex
          if (chunk.messageIndex !== undefined) {
            const msg = messages.value[assistantMsgIndex]
            if (msg && msg.messageIndex !== chunk.messageIndex) {
              console.log('📝 更新messageIndex:', msg.messageIndex, '→', chunk.messageIndex)
              messages.value[assistantMsgIndex] = {
                ...messages.value[assistantMsgIndex],
                messageIndex: chunk.messageIndex
              }
            }
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
            // 直接更新响应数据
            msg.responses[idx] = { ...msg.responses[idx], ...chunk }
            // 强制触发响应式更新
            messages.value = [...messages.value]
            
            console.log('✔ 更新成功:', chunk.modelName, 'at index', idx, '→ done:', msg.responses[idx].done, '内容长度:', msg.responses[idx].fullContent?.length, 'reasoning长度:', msg.responses[idx].reasoning?.length)

            // 滚动到底部
            scrollToBottom()
          } else {
            console.error('❌ 未找到匹配的响应槽位！收到的modelName:', chunk.modelName)
            console.error('❌ 期望的模型列表:', validModels)
          }

          // 检查完成
          if (chunk.done) {
            const allDone = msg.responses.every((r: any) => r.done)
            const doneCount = msg.responses.filter((r: any) => r.done).length
            console.log(`完成进度: ${doneCount}/${msg.responses.length}`)

            // 处理预算预警
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

            if (allDone) {
              console.log('所有模型响应完成')
              isLoading.value = false
              if (isNewConversation.value) {
                console.log('重置新会话标志')
                isNewConversation.value = false
              }
              const conversationId = route.query.conversationId as string
              if (conversationId && msg.messageIndex !== undefined) {
                loadRatings(conversationId)
              }
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
        onBusinessError: (data) => {
          isLoading.value = false
          isNewConversation.value = false
          message.error(data.message || '请求过于频繁，请稍后再试')
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

// 根据模型ID获取图标文件名
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

// 获取默认图标URL
const getDefaultIconUrl = () => {
  return new URL('../assets/provider-icons/default.png', import.meta.url).href
}

// 根据模型ID获取提供商图标（使用和左侧列表相同的方式）
const getProviderIcon = (modelId: string | undefined) => {
  if (!modelId) {
    return getDefaultIconUrl()
  }

  const iconFile = getIconFile(modelId)
  return new URL(`../assets/provider-icons/${iconFile}`, import.meta.url).href
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

const expandImage = (imageUrl: string) => {
  expandedImageUrl.value = imageUrl
}

const closeExpandedImage = () => {
  expandedImageUrl.value = null
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
      // 直接使用本地数据更新，避免额外请求
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
      // 强制触发响应式更新
      messages.value = [...messages.value]
      await nextTick()
      message.success('评分成功')
    }
  } catch (error) {
    console.error('评分失败:', error)
    message.error('评分失败')
  } finally {
    // 发送后清空输入区域的图片（图片仍保留在本轮用户消息中）
    selectedImageUrls.value = []
  }
}

// 加载评分信息（带防抖，避免重复调用）
let loadingRatings = false
let loadingRatingsConversationId: string | null = null
const loadRatings = async (conversationId: string) => {
  // 如果正在加载同一个会话的评分，直接返回
  if (loadingRatings && loadingRatingsConversationId === conversationId) {
    console.log('⏸️ 评分正在加载中，跳过重复调用')
    return
  }

  loadingRatings = true
  loadingRatingsConversationId = conversationId

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
  } finally {
    loadingRatings = false
    // 延迟清除，避免快速连续调用
    setTimeout(() => {
      if (loadingRatingsConversationId === conversationId) {
        loadingRatingsConversationId = null
      }
    }, 1000)
    }
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

      // 检查会话类型，如果是prompt_lab类型，跳转到对应页面
      if (conversation.conversationType === 'prompt_lab') {
        console.log('🔄 检测到提示词实验会话，跳转到prompt-lab页面')
        router.replace(`/prompt-lab?conversationId=${conversationId}`)
        return
      }

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
        
        // 检查选中的模型是否是图片生成模型，如果是则自动选中图片模式
        // 先加载模型列表以获取模型信息（不筛选，获取所有模型信息）
        await loadModels(undefined, false, false)
        
        // 检查第一个选中的模型是否是图片生成模型
        const firstModel = selectedModels.value[0]
        console.log('🔍 检查第一个模型:', firstModel, '模型选项数量:', modelOptions.value.length)
        if (firstModel) {
          const modelOption = modelOptions.value.find(opt => opt.value === firstModel)
          console.log('🔍 找到模型选项:', modelOption ? {
            value: modelOption.value,
            label: modelOption.label,
            supportsImageGen: modelOption.supportsImageGen
          } : '未找到')
          if (modelOption && modelOption.supportsImageGen) {
            console.log('🖼️ 检测到图片生成模型，自动选中图片模式')
            inputMode.value = 'image'
            // 刷新模型列表（只显示图片生成模型）
            await loadModels(undefined, false, true)
            console.log('✅ 图片模式已选中，inputMode:', inputMode.value, 'isImageMode:', isImageMode.value)
          } else {
            console.log('ℹ️ 第一个模型不是图片生成模型，保持文本模式')
          }
        } else {
          console.log('ℹ️ 没有选中的模型')
        }
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
        
        // 分离用户消息和 AI 消息
        const userMsgs = msgs.filter(m => m.role === 'user')
        const assistantMsgs = msgs.filter(m => m.role === 'assistant')

        // 先添加用户消息
        if (userMsgs.length > 0) {
          const userMsg = userMsgs[0]
          let imageUrls: string[] | undefined
          if (userMsg.images) {
            try {
              imageUrls = JSON.parse(userMsg.images)
            } catch (e) {
              console.warn('解析用户消息图片失败:', e)
            }
          }
          groupedMessages.push({
            type: 'user',
            content: userMsg.content,
            messageIndex: index,
            imageUrls
          })
        }
        
        // 再添加 AI 响应
        if (assistantMsgs.length > 0) {
          // AI回复 - 多个模型的响应
          const msgs = assistantMsgs
          console.log('🔍 原始assistant消息:', msgs)
          const responses = msgs.map((msg: any) => {
            console.log('🔍 单条消息字段:', {
              modelName: msg.modelName,
              inputTokens: msg.inputTokens,
              outputTokens: msg.outputTokens,
              cost: msg.cost,
              responseTimeMs: msg.responseTimeMs,
              images: msg.images
            })
            // 计算思考时间
            const thinkingTime = msg.reasoning
              ? Math.max(1, Math.min(Math.ceil(msg.reasoning.length / 200), 60))
              : undefined

            // 解析生成的图片（如果存在）
            let generatedImages: GeneratedImageVO[] | undefined
            if (msg.images) {
              try {
                const imageUrls = JSON.parse(msg.images) as string[]
                console.log('🖼️ 从会话历史解析图片:', {
                  modelName: msg.modelName,
                  imagesJson: msg.images,
                  imageUrls,
                  imageUrlsCount: imageUrls?.length
                })
                if (imageUrls && Array.isArray(imageUrls) && imageUrls.length > 0) {
                  generatedImages = imageUrls.map((url, idx) => ({
                    url,
                    modelName: msg.modelName,
                    index: idx,
                    inputTokens: msg.inputTokens || 0,
                    outputTokens: msg.outputTokens || 0,
                    totalTokens: (msg.inputTokens || 0) + (msg.outputTokens || 0),
                    cost: msg.cost
                  }))
                  console.log('✅ 成功解析图片:', generatedImages)
                }
              } catch (e) {
                console.warn('❌ 解析助手消息图片失败:', e, msg.images)
              }
            } else {
              console.log('ℹ️ 助手消息没有图片字段:', {
                modelName: msg.modelName,
                hasImages: !!msg.images,
                content: msg.content?.substring(0, 50)
              })
            }

            const response = {
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
              thinkingTime: thinkingTime,
              generatedImages: generatedImages,
              toolsUsed: msg.toolsUsed
            }
            console.log('✅ 构建响应对象:', {
              modelName: response.modelName,
              hasGeneratedImages: !!response.generatedImages,
              generatedImagesCount: response.generatedImages?.length,
              fullContent: response.fullContent?.substring(0, 50)
            })
            return response
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
      
      // 通过消息列表的最后一个消息判断是否为图片生成
      // 检查最后一个 assistant 消息是否有 images 字段
      let shouldEnableImageMode = false
      if (groupedMessages.length > 0) {
        // 从后往前查找最后一个 assistant 消息
        for (let i = groupedMessages.length - 1; i >= 0; i--) {
          const msg = groupedMessages[i]
          if (msg.type === 'assistant' && msg.responses && msg.responses.length > 0) {
            // 检查是否有任何响应包含生成的图片
            const hasGeneratedImages = msg.responses.some((resp: any) => 
              resp.generatedImages && resp.generatedImages.length > 0
            )
            if (hasGeneratedImages) {
              shouldEnableImageMode = true
              console.log('🖼️ 检测到最后一个消息包含生成的图片，应启用图片模式')
              break
            }
          }
        }
      }
      
      // 如果应该启用图片模式，或者第一个模型是图片生成模型，则设置图片模式
      if (shouldEnableImageMode) {
        console.log('🖼️ 根据消息历史，自动选中图片模式')
        inputMode.value = 'image'
        // 刷新模型列表（只显示图片生成模型）
        await loadModels(undefined, false, true)
        console.log('✅ 图片模式已选中，inputMode:', inputMode.value, 'isImageMode:', isImageMode.value)
      } else {
        // 检查第一个选中的模型是否是图片生成模型
        const firstModel = selectedModels.value[0]
        if (firstModel && modelOptions.value.length > 0) {
          const modelOption = modelOptions.value.find(opt => opt.value === firstModel)
          console.log('🔍 消息加载后检查模型:', {
            firstModel,
            modelOption: modelOption ? {
              value: modelOption.value,
              label: modelOption.label,
              supportsImageGen: modelOption.supportsImageGen
            } : null,
            currentInputMode: inputMode.value,
            currentIsImageMode: isImageMode.value
          })
          if (modelOption && modelOption.supportsImageGen && inputMode.value !== 'image') {
            console.log('🖼️ 检测到图片生成模型，自动选中图片模式')
            inputMode.value = 'image'
            // 刷新模型列表（只显示图片生成模型）
            await loadModels(undefined, false, true)
            console.log('✅ 图片模式已选中，inputMode:', inputMode.value, 'isImageMode:', isImageMode.value)
          }
        }
      }

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
    selectedImageUrls.value = []
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
    selectedImageUrls.value = []
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
  font-size: 13px;
  transition: all 0.2s;
}

.add-image-btn:hover {
  border-color: #1890ff;
  color: #1890ff;
  background: #f0f7ff;
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
  pointer-events: auto;
}

.tool-icon:hover:not(:disabled) {
  background: #f5f5f5;
}

.tool-icon:disabled {
  cursor: not-allowed;
  opacity: 0.5;
}

.tool-icon-active {
  background: #e6f4ff;
  color: #1890ff;
}

.tool-icon-disabled {
  opacity: 0.4;
  cursor: not-allowed;
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

@keyframes spin {
  to {
    transform: rotate(360deg);
  }
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

@media (max-width: 768px) {
  .generated-image {
    max-width: 100%;
    max-height: 400px;
  }
}

.image-metrics {
  display: flex;
  gap: 12px;
  font-size: 12px;
  color: #666;
  padding: 4px 0;
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

/* 联网搜索信息样式 */
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
</style>
