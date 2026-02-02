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
          <a-select-option value="battle">
            <TrophyOutlined style="margin-right: 8px" />
            匿名对比
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
                <!-- 显示变体关联的图片 -->
                <div v-if="msg.variantImageUrls && msg.variantImageUrls[vIdx] && msg.variantImageUrls[vIdx].length > 0" class="variant-msg-images">
                  <img 
                    v-for="(imgUrl, imgIdx) in msg.variantImageUrls[vIdx]" 
                    :key="imgIdx" 
                    :src="imgUrl" 
                    alt="用户上传图片" 
                    class="variant-msg-image"
                  />
                </div>
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

                <!-- 联网搜索信息 -->
                <div v-if="result.toolsUsed && parseToolsUsed(result.toolsUsed)?.webSearch?.enabled" class="web-search-info">
                  <div class="web-search-badge">
                    <GlobalOutlined class="web-search-icon" />
                    <span class="web-search-text">
                      已使用联网搜索
                      <template v-if="parseToolsUsed(result.toolsUsed)?.webSearch?.query">
                        · 关键词: "{{ parseToolsUsed(result.toolsUsed)?.webSearch?.query }}"
                      </template>
                    </span>
                  </div>
                  <div v-if="parseToolsUsed(result.toolsUsed)?.webSearch?.sources?.length" class="web-search-sources">
                    <a
                      v-for="(source, sourceIdx) in parseToolsUsed(result.toolsUsed)?.webSearch?.sources?.slice(0, 5)"
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

            <div class="result-content">
                  <div v-if="result.hasError" class="error-msg">{{ result.error }}</div>
              <template v-else>
                <!-- 图片生成结果 -->
                <div v-if="result.generatedImages && result.generatedImages.length > 0" class="generated-images">
                  <div
                    v-for="(img, imgIdx) in result.generatedImages"
                    :key="`variant-${rIdx}-img-${imgIdx}`"
                    class="generated-image-item"
                  >
                    <img 
                      :src="img.url" 
                      :alt="img.revisedPrompt || '生成的图片'" 
                      class="generated-image clickable-image" 
                      @click="expandImage(img.url)"
                    />
                    <div v-if="img.revisedPrompt" class="revised-prompt">
                      <span class="revised-prompt-label">优化后的提示词：</span>
                      {{ img.revisedPrompt }}
                    </div>
                  </div>
                </div>
                <!-- 思考过程 -->
                <details v-if="result.hasReasoning && result.reasoning" class="thinking-details" open>
                  <summary class="thinking-summary">
                    {{ !result.done && !result.fullContent ? '正在思考...' : `思考了 ${result.thinkingTime || 1} 秒` }}
                  </summary>
                  <div class="thinking-content">
                    <MarkdownRenderer :content="result.reasoning || ''" />
                  </div>
                </details>
                <!-- 思考完成但内容还未返回时的过渡提示 -->
                <div v-if="result.hasReasoning && result.reasoning && !result.fullContent && !result.done && !result.generatedImages?.length" class="generating-content-hint">
                  正在生成回答...
                </div>
                <!-- 最终回答 -->
                <MarkdownRenderer v-if="result.fullContent" :content="result.fullContent" />
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
              title="图片模式（支持多模态输入）"
              @click="toggleImageMode"
            >
              <FileImageOutlined />
            </button>
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
              @paste="(e: ClipboardEvent) => handlePaste(e, idx)"
            ></textarea>
            <!-- 图片预览列表 -->
            <div v-if="variantImageUrls[idx] && variantImageUrls[idx].length > 0" class="variant-image-list">
              <div
                v-for="(item, imgIdx) in variantImageUrls[idx]"
                :key="item.url + imgIdx"
                class="variant-image-item"
              >
                <div class="variant-image-wrapper">
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
                  <img v-else :src="item.url" alt="预览图片" class="variant-image-thumb" />
                </div>
                <a-button
                  type="text"
                  danger
                  size="small"
                  class="variant-image-delete-btn"
                  @click.stop="removeImage(idx, imgIdx)"
                  :disabled="item.status === 'uploading'"
                >
                  <template #icon>
                    <CloseOutlined />
                  </template>
                </a-button>
              </div>
              <!-- 添加更多图片按钮 -->
              <div 
                v-if="variantImageUrls[idx].length < 5" 
                class="variant-image-add"
                @click.stop="triggerSelectImages(idx)"
              >
                <PlusOutlined />
              </div>
            </div>
            <!-- 无图片时的添加图片按钮 -->
            <div v-else-if="isImageMode" class="variant-add-image-btn" @click.stop="triggerSelectImages(idx)">
              <FileImageOutlined />
              <span>添加图片</span>
            </div>
          </div>
        </div>
        <!-- 隐藏的文件选择器 -->
        <input
          ref="imageInputRef"
          type="file"
          accept="image/*"
          multiple
          style="display: none"
          @change="handleImageChange"
        />

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
  </div>
</template>

<script setup lang="ts">
import { ref, computed, onMounted, onUnmounted, watch, nextTick } from 'vue'
import { useRouter, useRoute } from 'vue-router'
import { message } from 'ant-design-vue'
import { SwapOutlined, ExperimentOutlined, TrophyOutlined, ExpandOutlined, ThunderboltOutlined, AppstoreOutlined, PlusOutlined, GlobalOutlined, FileImageOutlined, CloseOutlined, LinkOutlined } from '@ant-design/icons-vue'
import { uploadImage } from '@/api/fileController'
import { listModels } from '@/api/modelController'
import { getConversation, getConversationMessages, type StreamChunkVO, type ToolsUsedInfo } from '@/api/conversationController'
import { createPostSSE } from '@/utils/sseClient'
import { API_BASE_URL } from '@/config/env'
import MarkdownRenderer from '@/components/MarkdownRenderer.vue'
import { addRating, getRating, getRatingsByConversationId, type RatingVO } from '@/api/ratingController'
import { optimizePrompt, type PromptOptimizationVO } from '@/api/promptOptimizationController'
import { listTemplates, createTemplate, incrementUsage, type PromptTemplateVO } from '@/api/promptTemplateController'
import { generateImage, generateImageStream, type GeneratedImageVO } from '@/api/imageController'

const router = useRouter()
const route = useRoute()
const currentMode = ref('prompt-lab')
const selectedModel = ref<string>()
const variants = ref<string[]>(['', ''])
const isStreaming = ref(false)
const loadingModels = ref(false)
const modelOptions = ref<{ label: string; value: string; supportsMultimodal?: boolean; supportsImageGen?: boolean; supportsToolCalling?: boolean }[]>([])
interface PromptLabMsg {
  type: 'user' | 'assistant'
  variants?: string[]  // 用户消息的变体列表
  variantImageUrls?: string[][]  // 用户消息的变体图片列表
  results?: StreamChunkVO[]  // AI响应的结果列表
  messageIndex?: number  // 消息索引
  rating?: RatingVO  // 评分信息
}

const messages = ref<PromptLabMsg[]>([])  // 消息列表
const sseController = ref<any>(null)
const expandedVariantIndex = ref<number | null>(null)
const originalVariants = ref<string[]>([])  // 会话创建时的原始变体
const messagesWrapper = ref<HTMLElement | null>(null)  // 消息列表容器引用
const expandedImageUrl = ref<string | null>(null)  // 放大查看的图片URL
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

// 联网搜索相关状态
const WEB_SEARCH_STORAGE_KEY = 'ai-test:promptLab:webSearchEnabled'
const webSearchEnabled = ref(localStorage.getItem(WEB_SEARCH_STORAGE_KEY) === 'true')

// 图片相关状态（多模态输入）
interface ImageItem {
  url: string
  status: 'uploading' | 'completed' | 'failed'
  file?: File
}
// 每个变体独立的图片列表
const variantImageUrls = ref<ImageItem[][]>([[], []])
const imageUploading = ref(false)
const imageInputRef = ref<HTMLInputElement | null>(null)
const currentUploadVariantIndex = ref<number>(0)  // 当前上传图片的目标变体索引

// 图片生成模式
const isImageMode = ref(false)

const handleModeChange = (mode: string) => {
  router.push(`/${mode}`)
}

// 检查选中的模型是否支持工具调用
const hasToolCallingModel = computed(() => {
  if (!selectedModel.value) return false
  const option = modelOptions.value.find(opt => opt.value === selectedModel.value)
  return (option as any)?.supportsToolCalling === true
})

// 切换联网搜索
const toggleWebSearch = async () => {
  if (!hasToolCallingModel.value) {
    message.warning('当前选中的模型不支持联网搜索')
    return
  }
  
  // 如果要开启联网搜索，先关闭图像生成模式
  if (!webSearchEnabled.value && isImageMode.value) {
    isImageMode.value = false
    await loadModels()
  }
  
  webSearchEnabled.value = !webSearchEnabled.value
  localStorage.setItem(WEB_SEARCH_STORAGE_KEY, webSearchEnabled.value.toString())
  message.info(webSearchEnabled.value ? '已开启联网搜索' : '已关闭联网搜索')
}

// 切换图片模式
const toggleImageMode = async () => {
  // 如果要开启图像生成模式，先关闭联网搜索
  if (!isImageMode.value && webSearchEnabled.value) {
    webSearchEnabled.value = false
    localStorage.setItem(WEB_SEARCH_STORAGE_KEY, 'false')
  }
  
  isImageMode.value = !isImageMode.value
  if (isImageMode.value) {
    message.info('已开启图像生成模式，正在加载支持图像生成的模型...', 1.5)
    await loadModels({ onlySupportsImageGen: true })
    if (modelOptions.value.length === 0) {
      message.warning('没有找到支持图像生成的模型')
    }
  } else {
    message.info('已关闭图像生成模式', 1.5)
    await loadModels()
  }
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

// 触发图片选择
const triggerSelectImages = (variantIndex: number) => {
  if (isStreaming.value) {
    message.warning('正在请求中，请稍后再选择图片')
    return
  }
  currentUploadVariantIndex.value = variantIndex
  imageInputRef.value?.click()
}

// 处理图片选择变化
const handleImageChange = async (event: Event) => {
  const target = event.target as HTMLInputElement | null
  if (!target || !target.files || target.files.length === 0) {
    return
  }
  const files = Array.from(target.files)
  await uploadImagesForVariant(files, currentUploadVariantIndex.value)
  target.value = ''
}

// 处理粘贴事件
const handlePaste = async (event: ClipboardEvent, variantIndex: number) => {
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
  await uploadImagesForVariant(imageFiles, variantIndex)
}

// 为指定变体上传图片
const uploadImagesForVariant = async (files: File[], variantIndex: number) => {
  if (!files.length) {
    return
  }
  
  // 确保数组长度与变体数量一致
  while (variantImageUrls.value.length < variants.value.length) {
    variantImageUrls.value.push([])
  }
  
  const currentImages = variantImageUrls.value[variantIndex] || []
  const remainingSlots = 5 - currentImages.length
  if (remainingSlots <= 0) {
    message.warning('每个变体最多只能选择 5 张图片')
    return
  }
  const filesToUpload = files.slice(0, remainingSlots)
  if (filesToUpload.length < files.length) {
    message.warning(`已达到图片数量上限，最多支持 5 张，本次仅上传前 ${filesToUpload.length} 张`)
  }

  try {
    imageUploading.value = true
    
    // 先添加占位图（上传中状态）
    const placeholderItems: ImageItem[] = filesToUpload.map((file, index) => {
      const placeholderUrl = `placeholder://${file.name}-${Date.now()}-${index}`
      return {
        url: placeholderUrl,
        status: 'uploading' as const,
        file
      }
    })
    
    if (!variantImageUrls.value[variantIndex]) {
      variantImageUrls.value[variantIndex] = []
    }
    variantImageUrls.value[variantIndex].push(...placeholderItems)
    
    // 逐个上传图片
    for (let i = 0; i < filesToUpload.length; i++) {
      const file = filesToUpload[i]
      const placeholderIndex = variantImageUrls.value[variantIndex].length - filesToUpload.length + i
      
      try {
        const formData = new FormData()
        formData.append('file', file)
        const res = await uploadImage(formData)
        
        if (res?.data?.code === 0 && res.data.data?.url) {
          // 更新为完成状态
          variantImageUrls.value[variantIndex][placeholderIndex] = {
            url: res.data.data.url,
            status: 'completed'
          }
        } else {
          // 更新为失败状态
          variantImageUrls.value[variantIndex][placeholderIndex].status = 'failed'
          message.error(res?.data?.message || '图片上传失败')
        }
      } catch (error) {
        console.error('单张图片上传失败:', error)
        variantImageUrls.value[variantIndex][placeholderIndex].status = 'failed'
        message.error(`图片 "${file.name}" 上传失败`)
      }
    }
    
    const successCount = variantImageUrls.value[variantIndex].filter(item => item.status === 'completed').length
    if (successCount > 0) {
      message.success(`变体 ${variantIndex + 1} 成功上传 ${successCount} 张图片`, 1.5)
    }
  } catch (error) {
    console.error('图片上传异常', error)
    message.error('图片上传失败，请重试')
  } finally {
    imageUploading.value = false
  }
}

// 删除指定变体的图片
const removeImage = (variantIndex: number, imageIndex: number) => {
  if (variantImageUrls.value[variantIndex] && 
      imageIndex >= 0 && 
      imageIndex < variantImageUrls.value[variantIndex].length) {
    variantImageUrls.value[variantIndex].splice(imageIndex, 1)
    console.log('删除图片，变体', variantIndex + 1, '剩余:', variantImageUrls.value[variantIndex].length)
  }
}

// 获取指定变体已完成的图片 URL 列表
const getCompletedImageUrlsForVariant = (variantIndex: number): string[] => {
  if (!variantImageUrls.value[variantIndex]) return []
  return variantImageUrls.value[variantIndex]
    .filter(item => item.status === 'completed')
    .map(item => item.url)
}

// 检查是否有正在上传的图片
const hasUploadingImages = (): boolean => {
  return variantImageUrls.value.some(images => 
    images.some(item => item.status === 'uploading')
  )
}

const canSubmit = computed(() => {
  return (
    selectedModel.value &&
    variants.value.length >= 2 &&
    variants.value.every((v) => v.trim()) &&
    !isStreaming.value
  )
})

const loadModels = async (options?: { searchText?: string; onlySupportsImageGen?: boolean; onlySupportsMultimodal?: boolean }) => {
  try {
    loadingModels.value = true
    const res: any = await listModels({
      pageNum: 1,
      pageSize: 50,
      searchText: options?.searchText || undefined,
      onlySupportsImageGen: options?.onlySupportsImageGen || undefined,
      onlySupportsMultimodal: options?.onlySupportsMultimodal || undefined
    })

    if (res.data && res.data.code === 0 && res.data.data && res.data.data.records) {
      const models = res.data.data.records
      modelOptions.value = models.map((m: any) => ({
        label: m.name,
        value: m.id,
        supportsMultimodal: m.supportsMultimodal,
        supportsImageGen: m.supportsImageGen,
        supportsToolCalling: m.supportsToolCalling
      }))
      // 如果当前选中的模型不在列表中，或者没有选中模型，选择第一个
      const currentModelInList = modelOptions.value.find(m => m.value === selectedModel.value)
      if (!currentModelInList && models.length > 0) {
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
  loadModels({ 
    searchText: value,
    onlySupportsImageGen: isImageMode.value ? true : undefined
  })
}

const addVariant = () => {
  if (variants.value.length < 5) {
    variants.value.push('')
    variantImageUrls.value.push([])
  }
}

const removeVariant = (idx: number) => {
  if (variants.value.length > 2) {
    variants.value.splice(idx, 1)
    variantImageUrls.value.splice(idx, 1)
  }
}

// 图像生成处理函数
const handleImageGeneration = async (promptVariantsToUse: string[], variantImageUrlsToSend: string[][]) => {
  if (!selectedModel.value) {
    message.warning('请选择图像生成模型')
    return
  }

  isStreaming.value = true

  // 如果是新会话，保存原始变体
  if (!route.query.conversationId && originalVariants.value.length === 0) {
    originalVariants.value = [...promptVariantsToUse]
  }

  console.log('📤 准备发送图像生成请求:', {
    model: selectedModel.value,
    promptVariants: promptVariantsToUse,
    variantImageUrls: variantImageUrlsToSend
  })

  // 添加用户消息
  messages.value.push({
    type: 'user',
    variants: [...promptVariantsToUse],
    variantImageUrls: variantImageUrlsToSend.some(urls => urls.length > 0) ? variantImageUrlsToSend.map(urls => [...urls]) : undefined
  })

  // 添加AI响应占位
  const assistantMsgIndex = messages.value.length
  const initialResults = promptVariantsToUse.map((_, idx) => ({
    variantIndex: idx,
    fullContent: '',
    done: false,
    hasError: false,
    generatedImages: [] as GeneratedImageVO[]
  }))

  messages.value.push({
    type: 'assistant',
    results: initialResults
  })

  // 清空已选图片
  variantImageUrls.value = variants.value.map(() => [])

  scrollToBottom()

  let currentConversationId = route.query.conversationId as string
  console.log('🖼️ 图片生成开始, currentConversationId:', currentConversationId, 'isFirst条件:', !currentConversationId)

  // 为每个变体调用图片生成接口
  // 定义生成单个变体图片的函数
  // 用于存储第一个请求返回的 messageIndex
  let sharedMessageIndex: number | undefined = undefined
  
  const generateForVariantStream = (prompt: string, variantIdx: number, conversationId?: string, msgIndex?: number, isFirst?: boolean): Promise<{ conversationId?: string, messageIndex?: number } | undefined> => {
    return new Promise((resolve) => {
      const referenceImages = variantImageUrlsToSend[variantIdx] || []
      
      const requestBody = {
        model: selectedModel.value,
        prompt: prompt,
        referenceImageUrls: referenceImages.length > 0 ? referenceImages : undefined,
        count: 1,
        conversationId: conversationId || undefined,
        models: isFirst && !conversationId ? [selectedModel.value as string] : undefined,
        conversationType: isFirst && !conversationId ? 'prompt_lab' : undefined,
        variantIndex: variantIdx,
        messageIndex: msgIndex
      }
      console.log('🖼️ 图片生成请求参数:', JSON.stringify(requestBody, null, 2))
      
      const streamController = generateImageStream(
        requestBody,
        (chunk: API.ImageStreamChunkVO) => {
          const msg = messages.value[assistantMsgIndex]
          if (!msg || !msg.results || !msg.results[variantIdx]) return
          
          if (chunk.type === 'thinking') {
            msg.results[variantIdx] = {
              ...msg.results[variantIdx],
              reasoning: chunk.fullThinking || '',
              hasReasoning: true
            }
            messages.value = [...messages.value]
            scrollToBottom()
          } else if (chunk.type === 'image' && chunk.image) {
            const currentImages = msg.results[variantIdx].generatedImages || []
            msg.results[variantIdx] = {
              ...msg.results[variantIdx],
              generatedImages: [...currentImages, chunk.image]
            }
            messages.value = [...messages.value]
            scrollToBottom()
          } else if (chunk.type === 'done') {
            msg.results[variantIdx] = {
              ...msg.results[variantIdx],
              done: true
            }
            messages.value = [...messages.value]
            resolve({ conversationId: chunk.conversationId, messageIndex: chunk.messageIndex })
          } else if (chunk.type === 'error') {
            const errorMsg = chunk.error || '图片生成失败'
            msg.results[variantIdx] = {
              ...msg.results[variantIdx],
              done: true,
              hasError: true,
              error: errorMsg
            }
            messages.value = [...messages.value]
            message.error(`变体 ${variantIdx + 1} 生成失败: ${errorMsg}`, 1)
            resolve(undefined)
          }
        },
        (error) => {
          console.error('❌ 变体', variantIdx, '图片生成失败:', error)
          const errorMsg = error.message || '图片生成失败'
          const msg = messages.value[assistantMsgIndex]
          if (msg && msg.results && msg.results[variantIdx]) {
            msg.results[variantIdx] = {
              ...msg.results[variantIdx],
              done: true,
              hasError: true,
              error: errorMsg
            }
            messages.value = [...messages.value]
          }
          message.error(`变体 ${variantIdx + 1} 生成失败: ${errorMsg}`, 1)
          resolve(undefined)
        },
        () => {
          console.log('🖼️ 变体', variantIdx, '流式响应完成')
        }
      )
    })
  }
  
  try {
    // 先执行第一个请求，获取 conversationId 和 messageIndex
    const firstResult = await generateForVariantStream(promptVariantsToUse[0], 0, currentConversationId, undefined, true)
    
    // 如果是新会话，更新 URL 和 conversationId
    if (!currentConversationId && firstResult?.conversationId) {
      currentConversationId = firstResult.conversationId
      router.replace({
        path: '/prompt-lab',
        query: { conversationId: firstResult.conversationId }
      })
    }
    
    // 保存 messageIndex 供后续变体使用
    if (firstResult?.messageIndex !== undefined) {
      sharedMessageIndex = firstResult.messageIndex
    }
    
    // 如果有多个变体，并行执行剩余的请求（使用相同的 messageIndex）
    if (promptVariantsToUse.length > 1) {
      const remainingPromises = promptVariantsToUse.slice(1).map((prompt, idx) => 
        generateForVariantStream(prompt, idx + 1, currentConversationId, sharedMessageIndex, false)
      )
      await Promise.all(remainingPromises)
    }
    
    message.success('图片生成完成')
  } catch (error) {
    console.error('图片生成失败:', error)
    message.error('图片生成失败')
  } finally {
    isStreaming.value = false
  }
}

const handleSubmit = async () => {
  if (!canSubmit.value) return

  // 检查是否有正在上传的图片
  if (hasUploadingImages()) {
    message.warning('请等待图片上传完成后再发送')
    return
  }

  // 始终使用当前输入的变体（用户可能已经修改了）
  const promptVariantsToUse = variants.value.filter(v => v.trim() !== '')

  // 收集每个变体的图片URL
  const variantImageUrlsToSend: string[][] = promptVariantsToUse.map((_, idx) => {
    return getCompletedImageUrlsForVariant(idx)
  })
  
  // 检查是否有任何图片
  const hasAnyImages = variantImageUrlsToSend.some(urls => urls.length > 0)
  
  // 图像生成模式
  if (isImageMode.value) {
    await handleImageGeneration(promptVariantsToUse, variantImageUrlsToSend)
    return
  }
  
  // 如果有图片，检查模型是否支持多模态
  if (hasAnyImages) {
    const modelOption = modelOptions.value.find(opt => opt.value === selectedModel.value)
    if (!modelOption?.supportsMultimodal) {
      message.error('当前模型不支持图片输入，请选择支持多模态的模型（如 GPT-4o、Claude 3.5 等）')
      return
    }
  }

  isStreaming.value = true

  // 如果是新会话，保存原始变体（用于后续轮次保持变体数量一致）
  if (!route.query.conversationId && originalVariants.value.length === 0) {
    originalVariants.value = [...promptVariantsToUse]
  }

  console.log('📤 准备发送Prompt Lab请求:', {
    model: selectedModel.value,
    promptVariants: promptVariantsToUse,
    variantImageUrls: variantImageUrlsToSend,
    hasAnyImages,
    webSearchEnabled: webSearchEnabled.value && hasToolCallingModel.value
  })

  // 添加用户消息（包含图片）
  messages.value.push({
    type: 'user',
    variants: [...promptVariantsToUse],
    variantImageUrls: hasAnyImages ? variantImageUrlsToSend.map(urls => [...urls]) : undefined
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

  // 清空已选图片（发送后清空）
  variantImageUrls.value = variants.value.map(() => [])

  // 滚动到底部
  scrollToBottom()

  try {
    const conversationId = route.query.conversationId as string
    const url = `${API_BASE_URL}/conversation/prompt-lab/stream`
    // 联网搜索：只有在有支持工具调用的模型时才启用
    const effectiveWebSearch = webSearchEnabled.value && hasToolCallingModel.value
    sseController.value = await createPostSSE(
      url,
      {
        model: selectedModel.value,
        promptVariants: promptVariantsToUse,
        variantImageUrls: hasAnyImages ? variantImageUrlsToSend : undefined,
        conversationId: conversationId || undefined,
        stream: true,
        webSearchEnabled: effectiveWebSearch
      },
      {
        onMessage: (chunk: StreamChunkVO) => {
          console.log('📨 Prompt Lab收到:', chunk.variantIndex, chunk.fullContent?.substring(0, 20), 'reasoning长度:', chunk.reasoning?.length)

          // 如果是新会话，保存conversationId到URL
          if (chunk.conversationId && !route.query.conversationId) {
            console.log('💾 保存新会话ID到URL:', chunk.conversationId)
            router.replace({
              path: '/prompt-lab',
              query: { conversationId: chunk.conversationId }
            })
          }

          // 更新AI响应消息
          const assistantMsg = messages.value[assistantMsgIndex]
          if (assistantMsg && assistantMsg.results && chunk.variantIndex !== undefined) {
            const variantIdx = chunk.variantIndex
            if (assistantMsg.results[variantIdx]) {
              // 直接更新数据
              assistantMsg.results[variantIdx] = { ...assistantMsg.results[variantIdx], ...chunk }
              // 如果chunk中包含messageIndex，更新消息的messageIndex
              if (chunk.messageIndex !== undefined) {
                assistantMsg.messageIndex = chunk.messageIndex
              }
              // 强制响应式更新
              messages.value = [...messages.value]
              // 滚动到底部
              scrollToBottom()
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
    const res: any = await getRating({ conversationId, messageIndex: msg.messageIndex })
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

const expandImage = (imageUrl: string) => {
  expandedImageUrl.value = imageUrl
}

const closeExpandedImage = () => {
  expandedImageUrl.value = null
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
    const res: any = await getRating({ conversationId, messageIndex })
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
      
      // 按 messageIndex 排序
      allMessages.sort((a: any, b: any) => (a.messageIndex || 0) - (b.messageIndex || 0))
      
      // 检查是否是图像生成模式（用户消息和AI响应的messageIndex是连续的）
      // 图像生成模式：messageIndex 0=用户, 1=AI, 2=用户, 3=AI
      // 普通模式：同一个messageIndex下有多个用户消息和多个AI响应
      const isImageGenMode = allMessages.every((m: any, idx: number, arr: any[]) => {
        if (idx === 0) return true
        // 检查相邻消息的 messageIndex 是否连续且不同
        return m.messageIndex !== arr[idx - 1].messageIndex
      }) && allMessages.length > 1
      
      console.log('🔍 检测到消息模式:', isImageGenMode ? '图像生成模式' : '普通模式')
      
      const messagesByIndex = new Map<number, { users: any[], assistants: any[] }>()

      if (isImageGenMode) {
        // 图像生成模式：将连续的用户消息和AI响应配对
        // 按轮次分组：每个用户消息和紧随其后的AI响应为一组
        let roundIndex = 0
        for (let i = 0; i < allMessages.length; i++) {
          const m = allMessages[i]
          if (m.role === 'user') {
            if (!messagesByIndex.has(roundIndex)) {
              messagesByIndex.set(roundIndex, { users: [], assistants: [] })
            }
            messagesByIndex.get(roundIndex)!.users.push(m)
          } else if (m.role === 'assistant') {
            if (!messagesByIndex.has(roundIndex)) {
              messagesByIndex.set(roundIndex, { users: [], assistants: [] })
            }
            messagesByIndex.get(roundIndex)!.assistants.push(m)
            // AI 响应后，准备下一轮
            roundIndex++
          }
        }
      } else {
        // 普通模式：按 messageIndex 分组
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
      }

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
          const variantMap = new Map<number, string>() // 用于按索引排序
          const variantImagesMap = new Map<number, string[]>() // 用于存储每个变体的图片

          console.log('🔍 处理用户消息，数量:', entry.users.length, 'messageIndex:', msgIndex)
          console.log('🔍 所有用户消息详情:', entry.users.map((m: any, idx: number) => ({
            index: idx,
            content: m.content,
            messageIndex: m.messageIndex,
            variantIndex: m.variantIndex,
            images: m.images
          })))

          entry.users.forEach((m: any, idx: number) => {
          const content = m.content || ''
            console.log(`  📝 用户消息[${idx}]:`, content)

            // 解析图片信息
            let imageUrls: string[] = []
            if (m.images) {
              try {
                imageUrls = JSON.parse(m.images)
                console.log(`  🖼️ 用户消息[${idx}] 图片:`, imageUrls)
              } catch (e) {
                console.error(`  ❌ 解析图片失败:`, e)
              }
            }

            // 匹配"变体X:内容"或"变体X: 内容"（冒号后可能有空格）
            // 使用更宽松的正则，允许前后有空白字符，并且匹配整个字符串
            const match = content.match(/^变体\s*(\d+)\s*:\s*(.+)$/s)
            if (match) {
              const variantIndex = parseInt(match[1], 10)
              const variantContent = match[2].trim()
              variantMap.set(variantIndex, variantContent)
              if (imageUrls.length > 0) {
                variantImagesMap.set(variantIndex, imageUrls)
              }
              console.log(`  ✅ 匹配到变体 ${variantIndex}:`, variantContent)
            } else {
              console.log(`  ⚠️ 未匹配到变体格式，原始内容:`, content)
              // 如果没有匹配到变体格式，尝试使用variantIndex（如果存在）
              if (m.variantIndex !== undefined && m.variantIndex !== null) {
                variantMap.set(m.variantIndex, content.trim())
                if (imageUrls.length > 0) {
                  variantImagesMap.set(m.variantIndex, imageUrls)
                }
                console.log(`  ✅ 使用variantIndex ${m.variantIndex}:`, content.trim())
              } else if (content.trim()) {
                // 最后回退：使用数组索引
                variantMap.set(idx, content.trim())
                if (imageUrls.length > 0) {
                  variantImagesMap.set(idx, imageUrls)
                }
                console.log(`  ✅ 使用数组索引 ${idx}:`, content.trim())
              }
            }
          })

          // 按变体索引排序
          const sortedEntries = Array.from(variantMap.entries())
            .sort((a, b) => a[0] - b[0])
          const sortedVariants = sortedEntries.map(([_, content]) => content)
          
          // 构建排序后的图片数组
          const sortedImages: string[][] = sortedEntries.map(([variantIdx, _]) => {
            return variantImagesMap.get(variantIdx) || []
          })
          const hasAnyImages = sortedImages.some(imgs => imgs.length > 0)

          currentRoundVariantCount = sortedVariants.length
          // 更新最后一轮的变体数量
          lastRoundVariantCount = sortedVariants.length
          console.log('📊 提取到的变体:', sortedVariants.length, '个', sortedVariants)
          console.log('📊 提取到的图片:', sortedImages)

          if (sortedVariants.length > 0) {
            newMessages.push({
              type: 'user',
              variants: sortedVariants,
              variantImageUrls: hasAnyImages ? sortedImages : undefined,
              messageIndex: msgIndex
        })
            console.log('✅ 添加用户消息，变体数量:', sortedVariants.length, 'messageIndex:', msgIndex, '变体:', sortedVariants, '图片:', sortedImages)
          }
      }

        // 添加AI响应
        if (entry.assistants.length > 0) {
          console.log('🔍 处理AI响应，数量:', entry.assistants.length, 'messageIndex:', msgIndex)
          entry.assistants.forEach((m: any, idx: number) => {
            console.log(`  📝 AI响应[${idx}]: variantIndex=${m.variantIndex}, content长度=${(m.content || '').length}`)
          })

          const results = entry.assistants
            .map((m: any) => {
              // 解析生成的图片
              let generatedImages: any[] = []
              if (m.images) {
                try {
                  const parsedImages = JSON.parse(m.images)
                  // 如果是图片 URL 数组，转换为 GeneratedImageVO 格式
                  if (Array.isArray(parsedImages)) {
                    generatedImages = parsedImages.map((img: any, idx: number) => {
                      if (typeof img === 'string') {
                        return { url: img, index: idx }
                      }
                      return img
                    })
                  }
                } catch (e) {
                  console.error('解析 images 失败:', e)
                }
              }
              
              return {
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
                thinkingTime: m.reasoning ? Math.max(1, Math.min(Math.ceil(m.reasoning.length / 200), 60)) : undefined,
                toolsUsed: m.toolsUsed,
                generatedImages: generatedImages.length > 0 ? generatedImages : undefined
              }
            })
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

/* 图片生成结果样式 */
.generated-images {
  display: flex;
  flex-wrap: wrap;
  gap: 12px;
  margin-bottom: 16px;
}

.generated-image-item {
  display: flex;
  flex-direction: column;
  gap: 6px;
}

.generated-image {
  max-width: 300px;
  max-height: 300px;
  width: auto;
  height: auto;
  object-fit: contain;
  border-radius: 8px;
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.1);
  cursor: pointer;
  transition: transform 0.2s;
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

.generated-image:hover {
  transform: scale(1.02);
}

.revised-prompt {
  font-size: 12px;
  color: #666;
  background: #f5f5f5;
  padding: 8px 12px;
  border-radius: 6px;
}

.revised-prompt-label {
  font-weight: 500;
  color: #1890ff;
}

/* 联网搜索信息样式 */
.web-search-info {
  background: linear-gradient(135deg, #f0f9ff 0%, #e6f7ff 100%);
  border: 1px solid #91d5ff;
  border-radius: 8px;
  padding: 12px 16px;
  margin-bottom: 12px;
}

.web-search-badge {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-bottom: 8px;
}

.web-search-icon {
  color: #1890ff;
  font-size: 16px;
}

.web-search-text {
  font-size: 13px;
  color: #1890ff;
  font-weight: 500;
}

.web-search-sources {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
}

.web-search-source-link {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  padding: 4px 10px;
  background: #fff;
  border: 1px solid #d9d9d9;
  border-radius: 4px;
  font-size: 12px;
  color: #595959;
  text-decoration: none;
  transition: all 0.2s;
  max-width: 180px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.web-search-source-link:hover {
  color: #1890ff;
  border-color: #1890ff;
  background: #f0f9ff;
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

/* 用户消息中的图片显示 */
.variant-msg-images {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  margin-bottom: 8px;
}

.variant-msg-image {
  width: 80px;
  height: 80px;
  object-fit: cover;
  border-radius: 6px;
  border: 1px solid #e5e7eb;
  cursor: pointer;
  transition: transform 0.2s;
}

.variant-msg-image:hover {
  transform: scale(1.05);
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

/* 工具图标样式 */
.tool-icon {
  width: 32px;
  height: 32px;
  border: none;
  background: #f5f5f5;
  color: #666;
  cursor: pointer;
  border-radius: 6px;
  display: flex;
  align-items: center;
  justify-content: center;
  transition: all 0.2s;
  font-size: 16px;
  flex-shrink: 0;
}

.tool-icon:hover {
  background: #e8e8e8;
  color: #333;
}

.tool-icon-active {
  background: #1890ff;
  color: #fff;
}

.tool-icon-active:hover {
  background: #40a9ff;
  color: #fff;
}

.tool-icon-disabled {
  opacity: 0.5;
  cursor: not-allowed;
}

.tool-icon-disabled:hover {
  background: #f5f5f5;
  color: #666;
}

/* 变体图片列表样式 */
.variant-image-list {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  margin-top: 8px;
  padding-top: 8px;
  border-top: 1px dashed #e5e7eb;
}

.variant-image-item {
  position: relative;
  width: 60px;
  height: 60px;
}

.variant-image-wrapper {
  width: 100%;
  height: 100%;
  border-radius: 6px;
  overflow: hidden;
  border: 1px solid #e5e7eb;
}

.variant-image-thumb {
  width: 100%;
  height: 100%;
  object-fit: cover;
}

.variant-image-delete-btn {
  position: absolute;
  top: -6px;
  right: -6px;
  width: 18px;
  height: 18px;
  padding: 0;
  border-radius: 50%;
  background: #fff;
  border: 1px solid #e5e7eb;
  color: #ff4d4f;
  font-size: 10px;
  display: flex;
  align-items: center;
  justify-content: center;
  box-shadow: 0 1px 3px rgba(0, 0, 0, 0.1);
  transition: all 0.2s;
}

.variant-image-delete-btn:hover {
  background: #fff2f0;
  border-color: #ff4d4f;
  transform: scale(1.1);
}

.variant-image-add {
  width: 60px;
  height: 60px;
  border: 1px dashed #d9d9d9;
  border-radius: 6px;
  display: flex;
  align-items: center;
  justify-content: center;
  cursor: pointer;
  color: #999;
  font-size: 18px;
  transition: all 0.2s;
}

.variant-image-add:hover {
  border-color: #1890ff;
  color: #1890ff;
}

.variant-add-image-btn {
  display: flex;
  align-items: center;
  gap: 6px;
  margin-top: 8px;
  padding: 8px 12px;
  background: #f5f5f5;
  border: 1px dashed #d9d9d9;
  border-radius: 6px;
  cursor: pointer;
  color: #666;
  font-size: 12px;
  transition: all 0.2s;
}

.variant-add-image-btn:hover {
  border-color: #1890ff;
  color: #1890ff;
  background: #f0f7ff;
}

/* 图片占位符样式 */
.image-placeholder {
  width: 100%;
  height: 100%;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  background: #f5f5f5;
  gap: 4px;
}

.image-placeholder-icon {
  font-size: 20px;
  color: #999;
}

.image-placeholder-text {
  font-size: 10px;
  color: #999;
}

.image-placeholder-error {
  background: #fff2f0;
}

.image-placeholder-error .image-placeholder-icon {
  color: #ff4d4f;
}

.image-placeholder-error .image-placeholder-text {
  color: #ff4d4f;
}

.image-upload-spinner {
  position: relative;
}

.spinner {
  width: 16px;
  height: 16px;
  border: 2px solid #e5e7eb;
  border-top-color: #1890ff;
  border-radius: 50%;
  animation: spin 1s linear infinite;
}

@keyframes spin {
  to {
    transform: rotate(360deg);
  }
}
</style>
