<template>
  <div class="code-mode-page-wrapper">
  <div class="code-mode-page">
    <!-- 左侧会话列表 -->
    <aside class="left-sidebar">
      <div class="logo-section">
        <img src="@/assets/logo.png" alt="Logo" class="logo-img" />
        <h2 class="app-name">代码模式</h2>
      </div>

      <nav class="nav-buttons">
        <button class="nav-btn" @click="handleNewChat">
          <EditOutlined />
          <span>新对话</span>
        </button>
        <button class="nav-btn" @click="handleBackToNormal">
          <ArrowLeftOutlined />
          <span>返回普通模式</span>
        </button>
      </nav>

      <div class="history-area" @scroll="handleScroll" ref="historyAreaRef">
        <!-- 今天 -->
        <div v-if="todayConversations.length > 0" class="history-group">
          <div class="group-label">今天</div>
          <div
            v-for="conv in todayConversations"
            :key="conv.id"
            class="history-item"
            :class="{ active: conv.id === currentConversationId }"
            @click="openConversation(conv.id)"
          >
            <CodeOutlined class="item-icon" />
            <span class="history-title">{{ conv.title || '无标题对话' }}</span>
          </div>
        </div>

        <!-- 昨天 -->
        <div v-if="yesterdayConversations.length > 0" class="history-group">
          <div class="group-label">昨天</div>
          <div
            v-for="conv in yesterdayConversations"
            :key="conv.id"
            class="history-item"
            :class="{ active: conv.id === currentConversationId }"
            @click="openConversation(conv.id)"
          >
            <CodeOutlined class="item-icon" />
            <span class="history-title">{{ conv.title || '无标题对话' }}</span>
          </div>
        </div>

        <!-- 更早 -->
        <div v-if="olderConversations.length > 0" class="history-group">
          <div
            v-for="conv in olderConversations"
            :key="conv.id"
            class="history-item"
            :class="{ active: conv.id === currentConversationId }"
            @click="openConversation(conv.id)"
          >
            <CodeOutlined class="item-icon" />
            <span class="history-title">{{ conv.title || '无标题对话' }}</span>
          </div>
        </div>

        <!-- 加载更多 -->
        <div v-if="loading && currentPage > 1" class="loading-more">
          <a-spin size="small" />
        </div>

        <!-- 没有更多 -->
        <div v-if="!hasMore && allConversations.length > 0" class="no-more">
          <span>没有更多对话了</span>
        </div>
      </div>
    </aside>

    <!-- 中间对话区域 -->
    <div class="middle-chat">
      <!-- 顶部模式选择和模型选择器 -->
      <div class="top-models-bar">
        <!-- 模式选择下拉框 - 单独一行 -->
        <div class="mode-selector-row">
          <a-select
            v-model:value="currentMode"
            style="width: 200px; flex-shrink: 0;"
            @change="handleModeChange"
          >
            <a-select-option value="model-compare">
              <SwapOutlined style="margin-right: 8px" />
              模型对比
            </a-select-option>
            <a-select-option value="prompt-experiment">
              <ExperimentOutlined style="margin-right: 8px" />
              提示词实验
            </a-select-option>
          </a-select>
        </div>

        <!-- 模型选择器 - 第二行 -->
        <div class="models-selector">
          <!-- 模型对比模式：多个模型选择器 -->
          <template v-if="currentMode === 'model-compare'">
            <template v-for="(model, index) in selectedModels" :key="index">
              <span v-if="index > 0" class="vs-label">vs</span>
              <a-select
                :value="selectedModels[index]"
                @update:value="(val) => updateModelAtIndex(index, val)"
                placeholder="选择模型"
                style="width: 180px; flex-shrink: 0;"
                show-search
                :options="getAvailableOptionsForIndex(index)"
                :loading="loadingModels"
              />
            </template>
            
            <a-button 
              v-if="selectedModels.length < 8" 
              type="dashed" 
              size="small"
              @click="addModel"
              style="flex-shrink: 0;"
            >
              +
            </a-button>
            <a-button 
              v-if="selectedModels.length > 1" 
              type="dashed" 
              size="small"
              danger
              @click="removeModel"
              style="flex-shrink: 0;"
            >
              -
            </a-button>
          </template>

          <!-- 提示词实验模式：单个模型选择器 -->
          <template v-else-if="currentMode === 'prompt-experiment'">
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
          </template>
        </div>
      </div>

      <!-- 欢迎界面 -->
      <div v-if="messages.length === 0" class="welcome-view">
        <h1 class="main-title">你想做什么？</h1>
        <p class="sub-title">描述你想创建的网站或应用...</p>
        
        <!-- 快捷示例 -->
        <div class="quick-examples">
          <button class="example-btn" @click="useExample('创建一个精美的餐厅菜单页面，使用卡片布局展示菜品，包含图片、名称、描述和价格')">
            🍽 餐厅菜单
          </button>
          <button class="example-btn" @click="useExample('创建一个iOS风格的控制中心界面，包含亮度、音量、WiFi、蓝牙等控制开关')">
            📱 iOS控制中心
          </button>
          <button class="example-btn" @click="useExample('创建一个功能完整的科学计算器，支持基本运算、三角函数、对数等科学计算')">
            🧮 科学计算器
          </button>
          <button class="example-btn" @click="useExample('创建一个现代化的个人作品集网站，包含导航栏、项目展示、技能介绍等模块')">
            💼 个人作品集
          </button>
        </div>
      </div>

      <!-- 消息列表 -->
      <div v-else class="messages-wrapper" ref="messagesWrapper">
        <div v-for="(msg, idx) in messages" :key="idx" class="msg-block">
          <!-- 用户消息 -->
          <div v-if="msg.type === 'user'" class="user-msg">
            <div class="user-bubble">
              <!-- 提示词实验模式：显示变体标签 -->
              <template v-if="currentMode === 'prompt-experiment' && msg.variantIndex !== undefined">
                <span class="variant-label-inline">变体 {{ msg.variantIndex + 1 }}:</span>
                <span class="variant-content-inline">{{ msg.content }}</span>
              </template>
              <!-- 模型对比模式：普通显示 -->
              <template v-else>
                {{ msg.content }}
              </template>
            </div>
          </div>

          <!-- AI回答 - 只显示文本描述，不显示代码 -->
          <div v-if="msg.type === 'assistant'" class="ai-msg">
            <div class="ai-responses">
              <div 
                v-for="(resp, respIndex) in msg.responses" 
                :key="respIndex"
                class="response-item"
              >
                <div class="response-header">
                  <img 
                    :src="getProviderIcon(resp.modelName)" 
                    :alt="getModelName(resp.modelName)"
                    class="model-icon"
                  />
                  <span class="model-name">{{ getModelName(resp.modelName) }}</span>
                  <div class="stats">
                    <span v-if="resp.responseTimeMs">{{ (resp.responseTimeMs / 1000).toFixed(2) }}s</span>
                    <span v-if="resp.cost">${{ resp.cost.toFixed(4) }}</span>
                  </div>
                </div>
                <div class="response-content">
                  <!-- 错误提示 -->
                  <div v-if="resp.hasError" class="error-message">
                    <a-alert
                      type="error"
                      :message="`调用失败: ${resp.error || '未知错误'}`"
                      show-icon
                      closable
                    />
                  </div>
                  <!-- 生成过程中 - 显示文本内容（流式） -->
                  <template v-else-if="!resp.done && !resp.hasError">
                    <!-- 思考过程（流式显示） -->
                    <details v-if="resp.hasReasoning && resp.reasoning" class="thinking-details" open>
                      <summary class="thinking-summary">
                        <span class="thinking-title">
                          💭 思考中... {{ Math.ceil((resp.reasoning?.length || 0) / 10) }}字
                        </span>
                      </summary>
                      <div class="thinking-content">
                        <MarkdownRenderer :content="resp.reasoning || ''" />
                      </div>
                    </details>
                    <!-- 文本描述（流式显示） -->
                    <div v-if="getTextWithoutCode(resp.fullContent || '').trim()" class="text-description streaming">
                      <MarkdownRenderer :content="getTextWithoutCode(resp.fullContent || '')" />
                    </div>
                    <!-- 代码生成中 - 显示折叠的代码块 + 动画 -->
                    <div v-if="hasCodeContent(resp.fullContent || '')" class="code-block-container generating">
                      <div 
                        class="code-block-header generating"
                        @click="toggleCodeBlock(resp.modelName, 0)"
                      >
                        <div class="code-block-title">
                          <FileTextOutlined style="margin-right: 8px; color: #1e293b;" />
                          <span class="filename">{{ getFileName(getCodeLanguage(resp.fullContent || '')) }}</span>
                          <div class="generating-dots">
                            <span></span><span></span><span></span>
                          </div>
                        </div>
                        <div class="code-block-actions-right">
                          <span class="generating-status">生成中...</span>
                          <span class="expand-icon">
                            <DownOutlined v-if="!isCodeBlockExpanded(resp.modelName, 0)" />
                            <UpOutlined v-else />
                          </span>
                        </div>
                      </div>
                      <!-- 展开显示生成中的代码 -->
                      <div 
                        v-show="isCodeBlockExpanded(resp.modelName, 0)"
                        class="code-block-content generating"
                        :ref="el => { if (el) highlightGeneratingCode(el as HTMLElement, resp.modelName) }"
                      >
                        <pre><code :class="`language-${getCodeLanguage(resp.fullContent || '')}`">{{ getGeneratingCode(resp.fullContent || '') }}</code></pre>
                      </div>
                    </div>
                    <!-- 加载中（无内容） -->
                    <div v-if="!resp.fullContent || resp.fullContent.trim() === ''" class="typing-dots">
                      <span></span><span></span><span></span>
                    </div>
                  </template>
                  <!-- 代码生成完成 - 折叠显示 -->
                  <template v-else-if="resp.done && resp.hasCodeBlocks && resp.codeBlocks && resp.codeBlocks.length > 0">
                    <!-- 思考过程（完成） -->
                    <details v-if="resp.hasReasoning && resp.reasoning" class="thinking-details">
                      <summary class="thinking-summary">
                        <span class="thinking-title">
                          💭 思考了 {{ resp.thinkingTime || calculateThinkingTime(resp.reasoning) }} 秒
                        </span>
                      </summary>
                      <div class="thinking-content">
                        <MarkdownRenderer :content="resp.reasoning || ''" />
                      </div>
                    </details>
                    <!-- 文本描述（如果有） -->
                    <div v-if="getTextWithoutCode(resp.fullContent || '').trim()" class="text-description">
                      <MarkdownRenderer :content="getTextWithoutCode(resp.fullContent || '')" />
                    </div>
                    <!-- 代码块 -->
                    <div 
                      v-for="(block, idx) in resp.codeBlocks" 
                      :key="idx" 
                      class="code-block-container"
                      ref="codeBlockRefs"
                    >
                      <div 
                        class="code-block-header"
                        @click="toggleCodeBlock(resp.modelName, idx)"
                      >
                        <div class="code-block-title">
                          <FileTextOutlined style="margin-right: 8px; color: #1e293b;" />
                          <span class="filename">{{ getFileName(block.language) }}</span>
                          <span class="file-size">{{ formatCodeSize(block.code) }}</span>
                        </div>
                        <div class="code-block-actions">
                          <a-button 
                            type="text" 
                            size="small"
                            @click.stop="copyCode(block.code)"
                            title="复制代码"
                          >
                            <CopyOutlined />
                          </a-button>
                          <span class="expand-icon">
                            <DownOutlined v-if="!isCodeBlockExpanded(resp.modelName, idx)" />
                            <UpOutlined v-else />
                          </span>
                        </div>
                      </div>
                      <div 
                        v-show="isCodeBlockExpanded(resp.modelName, idx)"
                        class="code-block-content"
                      >
                        <pre><code :class="`language-${block.language}`">{{ block.code }}</code></pre>
                      </div>
                    </div>
                  </template>
                  <!-- 只有文本内容（无代码） -->
                  <template v-else-if="resp.done && getTextWithoutCode(resp.fullContent || '').trim()">
                    <!-- 思考过程 -->
                    <details v-if="resp.hasReasoning && resp.reasoning" class="thinking-details">
                      <summary class="thinking-summary">
                        <span class="thinking-title">
                          💭 思考了 {{ resp.thinkingTime || calculateThinkingTime(resp.reasoning) }} 秒
                        </span>
                      </summary>
                      <div class="thinking-content">
                        <MarkdownRenderer :content="resp.reasoning || ''" />
                      </div>
                    </details>
                    <MarkdownRenderer :content="getTextWithoutCode(resp.fullContent || '')" />
                  </template>
                </div>
              </div>
            </div>
            
            <!-- 评分按钮 - 只在所有响应完成后显示 -->
            <div
              v-if="msg.type === 'assistant' && msg.responses && msg.responses.every((r: any) => r.done) && shouldShowRating(msg, idx)"
              class="rating-section"
            >
              <div class="rating-buttons">
                <!-- 模型对比模式：显示"X模型更好"按钮 -->
                <template v-if="currentMode === 'model-compare'">
                  <button
                    v-for="(resp, respIdx) in msg.responses"
                    :key="`model-better-${respIdx}`"
                    class="rating-btn"
                    :class="{ 'rating-selected': isModelSelected(msg, resp.modelName, respIdx) }"
                    @click="handleRating(idx, 'model_better', resp.modelName)"
                  >
                    {{ getModelName(resp.modelName) }} 更好
                  </button>
                </template>
                
                <!-- 提示词实验模式：显示"变体X更好"按钮 -->
                <template v-else-if="currentMode === 'prompt-experiment'">
                  <button
                    v-for="variantMsg in getSameRoundVariantMessages(msg, idx)"
                    :key="`variant-better-${variantMsg.variantIndex}`"
                    class="rating-btn"
                    :class="{ 'rating-selected': isVariantSelectedForRound(msg, variantMsg.variantIndex) }"
                    @click="handleVariantRatingForRound(msg, variantMsg.variantIndex)"
                  >
                    变体 {{ (variantMsg.variantIndex !== undefined ? variantMsg.variantIndex : 0) + 1 }} 更好
                  </button>
                </template>
                
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
        <!-- 模型对比模式：普通输入框 -->
        <div v-if="currentMode === 'model-compare'" class="input-card">
          <textarea
            v-model="userInput"
            placeholder="描述你想创建的网站或应用...&#10;例如：创建一个精美的待办事项列表，支持添加、删除、标记完成等功能"
            :disabled="isLoading"
            @keydown.enter.exact.prevent="sendMessage"
            class="text-input"
          ></textarea>

          <div class="bottom-bar">
            <div class="left-tools">
              <button class="tool-icon code-mode-active" title="代码模式已启用">
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

        <!-- 提示词实验模式：提示词变体输入框 -->
        <div v-else-if="currentMode === 'prompt-experiment'" class="variants-panel">
          <div class="panel-header">
            <span class="panel-title">提示词变体 ({{ variants.length }}/5)</span>
            <div class="header-actions">
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
                    :disabled="!variants[idx] || variants[idx].trim() === '' || isLoading"
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
                :disabled="isLoading"
                class="variant-input-inline"
                rows="3"
                @focus="selectVariant(idx)"
                @click.stop
              ></textarea>
            </div>
          </div>

          <button
            class="submit-btn"
            :class="{ disabled: !canSubmitPrompt }"
            :disabled="!canSubmitPrompt"
            @click="handlePromptSubmit"
          >
            {{ isLoading ? '运行中...' : '开始实验' }}
          </button>
        </div>
      </div>
    </div>

    <!-- 右侧预览区域（60%宽度） -->
    <div class="right-preview">
      <!-- 模型选择tab / 变体选择tab -->
      <div class="preview-tabs">
        <div class="tabs-header">
          <div class="tabs-left">
            <!-- 模型对比模式：显示模型tab -->
            <template v-if="currentMode === 'model-compare'">
              <div 
                v-for="(model, index) in selectedModels" 
                :key="index"
                class="tab-item"
                :class="{ active: activePreviewTab === index }"
                @click="activePreviewTab = index"
              >
                <img 
                  :src="getProviderIcon(model)" 
                  :alt="getModelName(model)"
                  class="tab-icon"
                />
                <span class="tab-name">{{ getModelName(model) }}</span>
              </div>
            </template>
            
            <!-- 提示词实验模式：显示变体tab -->
            <template v-else-if="currentMode === 'prompt-experiment'">
              <div 
                v-for="(variantIndex, index) in getCurrentVariants()" 
                :key="variantIndex"
                class="tab-item"
                :class="{ active: activePreviewTab === variantIndex }"
                @click="activePreviewTab = variantIndex"
              >
                <span class="tab-name">变体 {{ variantIndex + 1 }}</span>
              </div>
            </template>
          </div>
          <div class="tabs-actions">
            <a-button 
              v-if="currentPreviewHtml"
              type="primary" 
              size="small"
              @click="downloadHtml"
              title="下载HTML文件"
              class="download-btn"
            >
              <template #icon><DownloadOutlined /></template>
              下载 HTML
            </a-button>
          </div>
        </div>
      </div>

      <!-- 预览内容 -->
      <div class="preview-content">
        <div v-if="currentPreviewHtml" class="preview-wrapper">
          <iframe
            :srcdoc="currentPreviewHtml"
            sandbox="allow-scripts"
            class="preview-iframe"
            title="网页预览"
          ></iframe>
        </div>
        <div v-else class="preview-empty">
          <CodeOutlined style="font-size: 48px; color: #d1d5db; margin-bottom: 16px;" />
          <p style="font-size: 14px; color: #6b7280;">生成HTML代码后，预览将显示在这里</p>
          <p style="font-size: 12px; color: #9ca3af; margin-top: 8px;">请在左侧输入框描述你想要的网页</p>
        </div>
      </div>
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
import { ref, computed, onMounted, nextTick, watch } from 'vue'
import { useRouter, useRoute } from 'vue-router'
import { message } from 'ant-design-vue'
import {
  EditOutlined,
  CodeOutlined,
  SendOutlined,
  ArrowLeftOutlined,
  DownloadOutlined,
  FileTextOutlined,
  CopyOutlined,
  DownOutlined,
  UpOutlined,
  SwapOutlined,
  ExperimentOutlined,
  ExpandOutlined,
  ThunderboltOutlined,
} from '@ant-design/icons-vue'
import { createPostSSE } from '@/utils/sseClient'
import { API_BASE_URL } from '@/config/env'
import { listConversations, getConversation, getConversationMessages } from '@/api/conversationController'
import { listModels, type ModelVO } from '@/api/modelController'
import { addRating, getRating, getRatingsByConversationId, type RatingVO } from '@/api/ratingController'
import { optimizePrompt, type PromptOptimizationVO } from '@/api/promptOptimizationController'
import MarkdownRenderer from '@/components/MarkdownRenderer.vue'
import CodeGeneratingHint from '@/components/CodeGeneratingHint.vue'
import { useLoginUserStore } from '@/stores/loginUser'
import hljs from 'highlight.js/lib/core'
import xml from 'highlight.js/lib/languages/xml'
import javascript from 'highlight.js/lib/languages/javascript'
import css from 'highlight.js/lib/languages/css'
import 'highlight.js/styles/github.css'

hljs.registerLanguage('html', xml)
hljs.registerLanguage('xml', xml)
hljs.registerLanguage('javascript', javascript)
hljs.registerLanguage('css', css)

interface Msg {
  type: 'user' | 'assistant'
  content?: string
  responses?: any[]
  variantIndex?: number  // 变体索引（用于提示词实验模式）
  messageIndex?: number  // 消息索引（用于评分）
  rating?: RatingVO  // 评分信息
}

const router = useRouter()
const route = useRoute()
const loginUserStore = useLoginUserStore()
const loginUser = computed(() => loginUserStore.loginUser)

const currentMode = ref<'model-compare' | 'prompt-experiment'>('model-compare')
const selectedModels = ref<string[]>([])
const selectedModel = ref<string>()
const variants = ref<string[]>(['', ''])
const expandedVariantIndex = ref<number | null>(null)
const modelOptions = ref<{ label: string; value: string }[]>([])
const loadingModels = ref(false)
const userInput = ref('')
const isLoading = ref(false)
const messages = ref<Msg[]>([])
const activePreviewTab = ref(0)
const messagesWrapper = ref<HTMLElement | null>(null)
const currentConversationId = ref<string>()
const expandedCodeBlocks = ref<Set<string>>(new Set())
const codeBlockRefs = ref<HTMLElement[]>([])
const optimizingIndex = ref<number | null>(null)  // 正在优化的变体索引
const optimizationDrawerVisible = ref(false)  // 优化结果抽屉显示状态
const optimizationResult = ref<PromptOptimizationVO | null>(null)  // 优化结果
const currentOptimizingVariantIndex = ref<number | null>(null)  // 当前正在优化的变体索引
const selectedVariantIndex = ref<number>(0)  // 当前选中的变体索引

const todayConversations = ref<any[]>([])
const yesterdayConversations = ref<any[]>([])
const olderConversations = ref<any[]>([])
const currentPage = ref(1)
const hasMore = ref(true)
const loading = ref(false)
const historyAreaRef = ref<HTMLElement | null>(null)

const canSend = computed(() => {
  if (currentMode.value === 'model-compare') {
    return userInput.value.trim() && selectedModels.value.length >= 1 && !isLoading.value
  }
  return false
})

const canSubmitPrompt = computed(() => {
  return (
    selectedModel.value &&
    variants.value.length >= 2 &&
    variants.value.every((v) => v.trim()) &&
    !isLoading.value
  )
})

const allConversations = computed(() => {
  return [...todayConversations.value, ...yesterdayConversations.value, ...olderConversations.value]
})

const getCurrentVariants = () => {
  if (currentMode.value !== 'prompt-experiment') {
    return []
  }
  
  // 从消息中提取所有变体索引
  const variantIndices = new Set<number>()
  messages.value.forEach(msg => {
    if (msg.type === 'user' && msg.variantIndex !== undefined) {
      variantIndices.add(msg.variantIndex)
    } else if (msg.type === 'assistant' && msg.responses) {
      msg.responses.forEach((resp: any) => {
        if (resp.variantIndex !== undefined) {
          variantIndices.add(resp.variantIndex)
        }
      })
    }
  })
  
  // 如果没有从消息中找到变体，使用输入框中的变体数量
  if (variantIndices.size === 0) {
    const indices = variants.value.filter(v => v.trim()).map((_, idx) => idx)
    // 如果activePreviewTab不在有效范围内，重置为0
    if (indices.length > 0 && !indices.includes(activePreviewTab.value)) {
      activePreviewTab.value = indices[0]
    }
    return indices
  }
  
  const sortedIndices = Array.from(variantIndices).sort((a, b) => a - b)
  // 如果activePreviewTab不在有效范围内，重置为第一个变体
  if (sortedIndices.length > 0 && !sortedIndices.includes(activePreviewTab.value)) {
    activePreviewTab.value = sortedIndices[0]
  }
  return sortedIndices
}

const currentPreviewHtml = computed(() => {
  console.log('🔍 [预览计算] 开始计算预览HTML')
  console.log('🔍 [预览计算] messages总数:', messages.value.length)
  console.log('🔍 [预览计算] 当前模式:', currentMode.value)
  console.log('🔍 [预览计算] activePreviewTab:', activePreviewTab.value)
  
  // 提示词实验模式：根据变体索引查找对应的消息
  if (currentMode.value === 'prompt-experiment') {
    // 查找所有包含指定变体索引的assistant消息（从后往前查找，找到最新的）
    const targetVariantIndex = activePreviewTab.value
    console.log('🔍 [预览计算] 查找变体', targetVariantIndex, '的HTML')
    
    // 先打印所有消息的变体索引，方便调试
    console.log('🔍 [预览计算] 所有消息的变体索引:')
    messages.value.forEach((msg, idx) => {
      if (msg.type === 'assistant' && msg.responses) {
        msg.responses.forEach((r: any, rIdx: number) => {
          console.log(`  消息[${idx}].响应[${rIdx}]: variantIndex=${r.variantIndex}, hasCodeBlocks=${r.hasCodeBlocks}, codeBlocksCount=${r.codeBlocks?.length || 0}`)
          if (r.codeBlocks && r.codeBlocks.length > 0) {
            r.codeBlocks.forEach((b: any, bIdx: number) => {
              console.log(`    代码块[${bIdx}]: language=${b.language}, codeLength=${b.code?.length || 0}, sanitizedHtmlLength=${b.sanitizedHtml?.length || 0}`)
            })
          }
        })
      }
    })
    
    for (let i = messages.value.length - 1; i >= 0; i--) {
      const msg = messages.value[i]
      if (msg.type === 'assistant' && msg.responses) {
        // 查找匹配的响应
        const response = msg.responses.find((r: any) => {
          const rVariantIndex = r.variantIndex !== undefined && r.variantIndex !== null ? r.variantIndex : -1
          const match = rVariantIndex === targetVariantIndex
          console.log(`🔍 [预览计算] 检查响应: variantIndex=${rVariantIndex}, target=${targetVariantIndex}, match=${match}`)
          return match
        })
        
        if (response) {
          console.log('🔍 [预览计算] 找到变体', targetVariantIndex, '的响应:', {
            variantIndex: response.variantIndex,
            hasCodeBlocks: response.hasCodeBlocks,
            codeBlocksCount: response.codeBlocks?.length || 0,
            codeBlocks: response.codeBlocks
          })
          
          if (response.codeBlocks && response.codeBlocks.length > 0) {
            console.log('🔍 [预览计算] 代码块列表:', response.codeBlocks.map((b: any) => ({
              language: b.language,
              codeLength: b.code?.length || 0,
              sanitizedHtmlLength: b.sanitizedHtml?.length || 0
            })))
            
            const htmlBlock = response.codeBlocks.find((b: any) => b.language === 'html')
            if (htmlBlock) {
              const html = htmlBlock.code || htmlBlock.sanitizedHtml || ''
              console.log('✅ [预览计算] 找到变体', targetVariantIndex, '的HTML，长度:', html.length)
              if (html.length > 0) {
                return html
              } else {
                console.log('⚠️ [预览计算] HTML代码块存在但内容为空')
              }
            } else {
              console.log('❌ [预览计算] 没有找到language=html的代码块，可用语言:', response.codeBlocks.map((b: any) => b.language))
            }
          } else {
            console.log('❌ [预览计算] 响应没有codeBlocks或codeBlocks为空')
          }
        }
      }
    }
    console.log('❌ [预览计算] 未找到变体', targetVariantIndex, '的HTML')
    return ''
  }
  
  // 模型对比模式：原有逻辑
  const lastMsg = messages.value[messages.value.length - 1]
  if (!lastMsg) {
    console.log('❌ [预览计算] 没有消息')
    return ''
  }
  
  console.log('🔍 [预览计算] 最后一条消息类型:', lastMsg.type)
  
  if (lastMsg.type !== 'assistant') {
    console.log('❌ [预览计算] 不是assistant消息')
    return ''
  }
  
  if (!lastMsg.responses) {
    console.log('❌ [预览计算] 没有responses')
    return ''
  }
  
  console.log('🔍 [预览计算] responses数量:', lastMsg.responses.length)
  
  const currentResponse = lastMsg.responses[activePreviewTab.value]
  if (!currentResponse) {
    console.log('❌ [预览计算] 当前tab没有响应', activePreviewTab.value)
    return ''
  }
  
  console.log('🔍 [预览计算] 当前响应详情:', {
    modelName: currentResponse.modelName,
    done: currentResponse.done,
    hasCodeBlocks: currentResponse.hasCodeBlocks,
    codeBlocks类型: typeof currentResponse.codeBlocks,
    codeBlocks是数组: Array.isArray(currentResponse.codeBlocks),
    codeBlocksCount: currentResponse.codeBlocks?.length || 0,
    codeBlocks内容: currentResponse.codeBlocks
  })
  
  if (!currentResponse.codeBlocks) {
    console.log('❌ [预览计算] codeBlocks是null或undefined')
    return ''
  }
  
  if (currentResponse.codeBlocks.length === 0) {
    console.log('❌ [预览计算] codeBlocks是空数组')
    return ''
  }
  
  console.log('🔍 [预览计算] 遍历codeBlocks查找HTML:')
  currentResponse.codeBlocks.forEach((block: any, index: number) => {
    console.log(`  - Block ${index}:`, {
      language: block.language,
      codeLength: block.code?.length || 0,
      sanitizedHtmlLength: block.sanitizedHtml?.length || 0
    })
  })
  
  const htmlBlock = currentResponse.codeBlocks.find((b: any) => b.language === 'html')
  if (!htmlBlock) {
    console.log('❌ [预览计算] 没有找到language=html的代码块')
    console.log('   所有语言类型:', currentResponse.codeBlocks.map((b: any) => b.language))
    return ''
  }
  
  const html = htmlBlock.code || htmlBlock.sanitizedHtml || ''
  console.log('✅ [预览计算] 找到HTML代码块！')
  console.log('   - code长度:', htmlBlock.code?.length || 0)
  console.log('   - sanitizedHtml长度:', htmlBlock.sanitizedHtml?.length || 0)
  console.log('   - 最终使用的HTML长度:', html.length)
  console.log('   - HTML开头:', html.substring(0, 200))
  
  return html
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
      modelOptions.value = models.map((m: ModelVO) => ({
        label: m.name,
        value: m.id,
      }))
      
      if (currentMode.value === 'model-compare') {
        if (selectedModels.value.length === 0 && models.length >= 1) {
          // 默认选择第一个模型，如果有多于1个模型，也选择第二个（方便对比）
          if (models.length >= 2) {
            selectedModels.value = [models[0].id, models[1].id]
          } else {
            selectedModels.value = [models[0].id]
          }
        }
      } else {
        // 提示词实验模式：默认选择第一个模型
        if (!selectedModel.value && models.length > 0) {
          selectedModel.value = models[0].id
        }
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

const handleModeChange = (mode: string) => {
  currentMode.value = mode as 'model-compare' | 'prompt-experiment'
  // 切换模式时清空消息和输入
  messages.value = []
  userInput.value = ''
  variants.value = ['', '']
  currentConversationId.value = undefined
  activePreviewTab.value = 0
  
  // 切换到提示词实验模式时，确保模型已选择
  if (mode === 'prompt-experiment' && !selectedModel.value && modelOptions.value.length > 0) {
    selectedModel.value = modelOptions.value[0].value
  }
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

const expandVariant = (index: number) => {
  expandedVariantIndex.value = index
}

const closeExpandedVariant = () => {
  expandedVariantIndex.value = null
}

// 选中变体
const selectVariant = (index: number) => {
  selectedVariantIndex.value = index
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
      // 查找最后一个assistant消息中对应变体的响应
      const lastAssistantMsg = messages.value
        .filter(m => m.type === 'assistant')
        .pop()
      if (lastAssistantMsg && lastAssistantMsg.responses) {
        const variantResp = lastAssistantMsg.responses.find((r: any) => r.variantIndex === variantIndex)
        if (variantResp && variantResp.fullContent) {
          aiResponse = variantResp.fullContent
        }
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

const getAvailableOptionsForIndex = (currentIndex: number) => {
  const selectedByOthers = selectedModels.value
    .map((model, idx) => idx !== currentIndex ? model : null)
    .filter(m => m) as string[]
  
  return modelOptions.value.filter(option => 
    !selectedByOthers.includes(option.value)
  )
}

const updateModelAtIndex = (index: number, value: string) => {
  const isDuplicate = selectedModels.value.some((m, idx) => 
    idx !== index && m === value
  )
  
  if (isDuplicate) {
    message.warning('该模型已被选择，请选择其他模型')
    return
  }
  
  const newModels = [...selectedModels.value]
  newModels[index] = value
  selectedModels.value = newModels
}

const addModel = () => {
  if (selectedModels.value.length < 8) {
    const unusedModel = modelOptions.value.find(m => !selectedModels.value.includes(m.value))
    if (unusedModel) {
      selectedModels.value = [...selectedModels.value, unusedModel.value]
    }
  }
}

const removeModel = () => {
  if (selectedModels.value.length > 1) {
    selectedModels.value = selectedModels.value.slice(0, -1)
    if (activePreviewTab.value >= selectedModels.value.length) {
      activePreviewTab.value = selectedModels.value.length - 1
    }
  }
}

const handleNewChat = () => {
  currentConversationId.value = undefined
  messages.value = []
  userInput.value = ''
  variants.value = ['', '']  // 清除提示词变体输入框
  activePreviewTab.value = 0
  router.push('/code-mode')
}

const downloadHtml = () => {
  if (!currentPreviewHtml.value) {
    message.warning('没有可下载的HTML内容')
    return
  }
  
  const lastMsg = messages.value[messages.value.length - 1]
  if (!lastMsg || lastMsg.type !== 'assistant') {
    return
  }
  
  const currentResponse = lastMsg.responses?.[activePreviewTab.value]
  if (!currentResponse) {
    return
  }
  
  const modelName = currentResponse.modelName?.replace(/\//g, '-') || 'unknown'
  const timestamp = new Date().toISOString().replace(/[:.]/g, '-').slice(0, -5)
  const fileName = `${modelName}_${timestamp}.html`
  
  const blob = new Blob([currentPreviewHtml.value], { type: 'text/html;charset=utf-8' })
  const url = URL.createObjectURL(blob)
  const link = document.createElement('a')
  link.href = url
  link.download = fileName
  document.body.appendChild(link)
  link.click()
  document.body.removeChild(link)
  URL.revokeObjectURL(url)
  
  message.success(`HTML文件已下载: ${fileName}`)
}

const toggleCodeBlock = (modelName: string, blockIndex: number) => {
  const key = `${modelName}-${blockIndex}`
  if (expandedCodeBlocks.value.has(key)) {
    expandedCodeBlocks.value.delete(key)
  } else {
    expandedCodeBlocks.value.add(key)
  }
  expandedCodeBlocks.value = new Set(expandedCodeBlocks.value)
}

const isCodeBlockExpanded = (modelName: string, blockIndex: number) => {
  const key = `${modelName}-${blockIndex}`
  return expandedCodeBlocks.value.has(key)
}

const formatCodeSize = (code: string) => {
  const bytes = new Blob([code]).size
  if (bytes < 1024) {
    return `${bytes} B`
  } else if (bytes < 1024 * 1024) {
    return `${(bytes / 1024).toFixed(1)} KB`
  } else {
    return `${(bytes / (1024 * 1024)).toFixed(1)} MB`
  }
}

const copyCode = (code: string) => {
  navigator.clipboard.writeText(code).then(() => {
    message.success('代码已复制到剪贴板')
  }).catch(() => {
    message.error('复制失败')
  })
}

const handleBackToNormal = () => {
  router.push('/side-by-side')
}

const useExample = (text: string) => {
  if (currentMode.value === 'prompt-experiment') {
    // 提示词实验模式：填充到所有变体输入框
    variants.value = variants.value.map(() => text)
  } else {
    // 模型对比模式：填充到普通输入框
    userInput.value = text
  }
}

const sendMessage = async () => {
  if (!canSend.value) return

  const text = userInput.value.trim()
  userInput.value = ''
  isLoading.value = true

  messages.value.push({ 
    type: 'user', 
    content: text
  })

  const assistantMsgIndex = messages.value.length
  const initialResponses = selectedModels.value.map(model => ({
    modelName: model,
    fullContent: '',
    done: false,
    hasError: false,
    codeBlocks: []
  }))
  
  messages.value.push({ 
    type: 'assistant', 
    responses: initialResponses
  })
  
  scrollToBottom()

  try {
    const url = `${API_BASE_URL}/conversation/code-mode/stream`
    
    await createPostSSE(
      url,
      { 
        conversationId: currentConversationId.value, 
        models: selectedModels.value, 
        prompt: text,
        stream: true 
      },
      {
        onMessage: (chunk: any) => {
          console.log('📨 CodeMode收到chunk:', {
            modelName: chunk.modelName,
            done: chunk.done,
            hasError: chunk.hasError,
            error: chunk.error,
            hasCodeBlocks: chunk.hasCodeBlocks,
            codeBlocksCount: chunk.codeBlocks?.length || 0,
            contentLength: chunk.fullContent?.length || 0,
            hasCodeContent: hasCodeContent(chunk.fullContent || ''),
            codeLanguage: getCodeLanguage(chunk.fullContent || '')
          })
          
          // 检查是否有错误
          if (chunk.hasError) {
            console.error('❌ 模型调用失败:', chunk.modelName, chunk.error)
            message.error(`${chunk.modelName} 调用失败: ${chunk.error || '未知错误'}`)
          }
          
          if (chunk.conversationId && !currentConversationId.value) {
            currentConversationId.value = chunk.conversationId
          }
          
          const msg = messages.value[assistantMsgIndex]
          if (!msg || !msg.responses) return
          
          // 设置messageIndex（从chunk中获取，如果没有则使用当前索引）
          if (chunk.messageIndex !== undefined && msg.messageIndex === undefined) {
            msg.messageIndex = chunk.messageIndex
          }
          
          const idx = msg.responses.findIndex((r: any) => r.modelName === chunk.modelName)
          if (idx >= 0) {
            msg.responses[idx] = { ...msg.responses[idx], ...chunk }
            messages.value = [...messages.value]
            
            if (chunk.done) {
              console.log('✅ 模型完成:', chunk.modelName, 'hasError:', chunk.hasError)
              const allDone = msg.responses.every((r: any) => r.done)
              if (allDone) {
                console.log('🎉 所有模型完成')
                isLoading.value = false
                highlightCode()
                scrollToBottom()
              }
            }
          }
        },
        onError: (err) => {
          console.error('SSE错误:', err)
          isLoading.value = false
          message.error('请求失败')
        },
        onBusinessError: (data) => {
          isLoading.value = false
          message.error(data.message || '请求过于频繁，请稍后再试')
        },
        onComplete: () => {
          isLoading.value = false
        },
      }
    )
  } catch (err: any) {
    console.error('发送失败:', err)
    isLoading.value = false
    message.error('发送失败')
  }
}

const handlePromptSubmit = async () => {
  if (!canSubmitPrompt.value) return

  isLoading.value = true
  const promptVariantsToUse = variants.value.filter(v => v.trim() !== '')

  // 为每个变体创建独立的用户消息和AI响应消息对
  // 格式：提示词1 -> AI响应1, 提示词2 -> AI响应2
  const variantMessageIndices: number[] = []
  
  promptVariantsToUse.forEach((variant, idx) => {
    // 添加用户消息（单个变体）
    messages.value.push({
      type: 'user',
      content: variant,
      variantIndex: idx  // 保存变体索引
    })
    
    // 添加AI响应占位（单个变体）
    const assistantMsgIndex = messages.value.length
    variantMessageIndices.push(assistantMsgIndex)
    
    messages.value.push({
      type: 'assistant',
      responses: [{
        modelName: selectedModel.value!,
        variantIndex: idx,
        fullContent: '',
        done: false,
        hasError: false,
        codeBlocks: []
      }]
    })
  })

  scrollToBottom()

  try {
    const url = `${API_BASE_URL}/conversation/code-mode/prompt-lab/stream`
    
    // 保存 variantMessageIndices 的引用，以便在回调中使用
    const currentVariantIndices = [...variantMessageIndices]
    
    await createPostSSE(
      url,
      {
        conversationId: currentConversationId.value,
        model: selectedModel.value,
        promptVariants: promptVariantsToUse,
        stream: true
      },
      {
        onMessage: (chunk: any) => {
          console.log('📨 CodeMode Prompt Lab收到chunk:', {
            variantIndex: chunk.variantIndex,
            done: chunk.done,
            hasError: chunk.hasError,
            error: chunk.error,
            contentLength: chunk.fullContent?.length || 0,
            hasCodeBlocks: chunk.hasCodeBlocks,
            codeBlocksCount: chunk.codeBlocks?.length || 0
          })

          if (chunk.conversationId && !currentConversationId.value) {
            currentConversationId.value = chunk.conversationId
            // 延迟更新URL，避免触发watch导致消息被清空
            // 在流式传输完成后再更新URL
            // router.replace({
            //   path: '/code-mode',
            //   query: { conversationId: chunk.conversationId }
            // })
          }

          // 根据variantIndex找到对应的消息索引
          if (chunk.variantIndex !== undefined) {
            const variantIdx = chunk.variantIndex
            if (variantIdx >= 0 && variantIdx < currentVariantIndices.length) {
              const msgIndex = currentVariantIndices[variantIdx]
              const msg = messages.value[msgIndex]
              
              if (!msg || !msg.responses || msg.responses.length === 0) {
                console.warn('⚠️ 消息或responses不存在:', { msgIndex, msg })
                return
              }

              // 检查是否有错误
              if (chunk.hasError) {
                console.error('❌ 变体调用失败:', chunk.variantIndex, chunk.error)
                message.error(`变体 ${chunk.variantIndex + 1} 调用失败: ${chunk.error || '未知错误'}`)
              }

              // 设置messageIndex（从chunk中获取，如果没有则使用当前索引）
              if (chunk.messageIndex !== undefined && msg.messageIndex === undefined) {
                msg.messageIndex = chunk.messageIndex
              }
              
              // 更新响应（每个变体只有一个响应）
              msg.responses[0] = { 
                ...msg.responses[0], 
                ...chunk,
                // 确保保留必要的字段
                modelName: msg.responses[0].modelName || chunk.modelName || selectedModel.value,
                variantIndex: variantIdx
              }
              // 强制响应式更新
              messages.value = [...messages.value]
              console.log('✅ 更新变体响应:', variantIdx, 'done:', chunk.done)

              // 检查是否完成
              if (chunk.done) {
                // 检查所有变体是否都完成
                const allDone = currentVariantIndices.every(idx => {
                  const m = messages.value[idx]
                  return m && m.responses && m.responses.length > 0 && m.responses[0].done
                })
                
                console.log('📊 变体完成状态:', {
                  currentDone: chunk.done,
                  variantIndex: chunk.variantIndex,
                  allDone,
                  totalVariants: currentVariantIndices.length
                })
                
                if (allDone) {
                  console.log('🎉 所有变体完成')
                  isLoading.value = false
                  highlightCode()
                  scrollToBottom()
                }
              }
            } else {
              console.warn('⚠️ 变体索引超出范围:', variantIdx, '总变体数:', currentVariantIndices.length)
            }
          } else {
            console.warn('⚠️ chunk没有variantIndex:', chunk)
          }
        },
        onError: (err) => {
          console.error('SSE错误:', err)
          isLoading.value = false
          message.error('请求失败')
        },
        onBusinessError: (data) => {
          isLoading.value = false
          message.error(data.message || '请求过于频繁，请稍后再试')
        },
        onComplete: () => {
          isLoading.value = false
          // 流式传输完成后再更新URL，避免触发watch导致消息被清空
          if (currentConversationId.value && !route.query.conversationId) {
            router.replace({
              path: '/code-mode',
              query: { conversationId: currentConversationId.value }
            })
          }
        },
      }
    )
  } catch (err: any) {
    console.error('提交失败:', err)
    isLoading.value = false
    message.error('提交失败')
  }
}

const loadConversations = async (append: boolean = false) => {
  if (!loginUser.value.id || loading.value) return
  
  try {
    loading.value = true
    
    const res: any = await listConversations({ 
      pageNum: currentPage.value, 
      pageSize: 50,
      codePreviewEnabled: 1
    })
    if (res.data && res.data.code === 0 && res.data.data && res.data.data.records) {
      const conversations = res.data.data.records
      const totalPages = res.data.data.totalPage || 1
      
      hasMore.value = currentPage.value < totalPages
      
      const now = new Date()
      const today = new Date(now.getFullYear(), now.getMonth(), now.getDate())
      const yesterday = new Date(today.getTime() - 24 * 60 * 60 * 1000)
      
      const todayList = conversations.filter((c: any) => new Date(c.createTime) >= today)
      const yesterdayList = conversations.filter((c: any) => {
        const createTime = new Date(c.createTime)
        return createTime >= yesterday && createTime < today
      })
      const olderList = conversations.filter((c: any) => new Date(c.createTime) < yesterday)
      
      if (append) {
        todayConversations.value = [...todayConversations.value, ...todayList]
        yesterdayConversations.value = [...yesterdayConversations.value, ...yesterdayList]
        olderConversations.value = [...olderConversations.value, ...olderList]
      } else {
        todayConversations.value = todayList
        yesterdayConversations.value = yesterdayList
        olderConversations.value = olderList
      }
    }
  } catch (error) {
    console.error('加载对话列表失败:', error)
  } finally {
    loading.value = false
  }
}

const handleScroll = (e: Event) => {
  const target = e.target as HTMLElement
  if (target.scrollHeight - target.scrollTop - target.clientHeight < 100 && hasMore.value && !loading.value) {
    currentPage.value++
    loadConversations(true)
  }
}

const openConversation = (id: string) => {
  router.push(`/code-mode?conversationId=${id}`)
}

const scrollToBottom = async () => {
  await nextTick()
  if (messagesWrapper.value) {
    messagesWrapper.value.scrollTo({
      top: messagesWrapper.value.scrollHeight,
      behavior: 'smooth'
    })
  }
}

const highlightCode = () => {
  nextTick(() => {
    document.querySelectorAll('.code-block-content pre code').forEach((block) => {
      hljs.highlightElement(block as HTMLElement)
    })
  })
}

const highlightedElements = new Set<string>()

const highlightGeneratingCode = (element: HTMLElement, modelName: string) => {
  // 使用唯一标识避免重复高亮
  const key = `${modelName}-generating`
  
  if (!highlightedElements.has(key)) {
    nextTick(() => {
      const codeBlock = element.querySelector('code')
      if (codeBlock) {
        hljs.highlightElement(codeBlock as HTMLElement)
        highlightedElements.add(key)
        
        // 使用 watch 监听内容变化，重新高亮
        setTimeout(() => {
          highlightedElements.delete(key)
        }, 100)
      }
    })
  }
}

const getTextWithoutCode = (text: string) => {
  // 移除完整的代码块
  let result = text.replace(/```[\w]*\n[\s\S]*?```/g, '')
  // 移除未完成的代码块（生成中）
  result = result.replace(/```[\w]*[\s\S]*$/g, '')
  return result.trim()
}

const hasCodeContent = (text: string) => {
  // 检测是否包含代码块标记（即使还没结束）
  return /```[\w]*/.test(text)
}

const getCodeLanguage = (text: string) => {
  const match = text.match(/```(\w+)/)
  return match ? match[1] : 'html'
}

const getGeneratingCode = (text: string) => {
  // 提取正在生成的代码内容（即使代码块还没结束）
  const match = text.match(/```[\w]*\n([\s\S]*)/)
  if (match) {
    const code = match[1]
    // 如果有结束标记，移除它
    return code.replace(/```\s*$/, '')
  }
  return ''
}

const calculateThinkingTime = (reasoning: string | undefined) => {
  if (!reasoning) return 0
  // 简单估算：每200个字符约1秒
  const estimatedSeconds = Math.ceil(reasoning.length / 200)
  return Math.max(1, Math.min(estimatedSeconds, 60)) // 最少1秒，最多60秒
}

const getFileName = (language: string) => {
  const lang = language?.toLowerCase() || 'html'
  
  const fileExtensions: Record<string, string> = {
    'html': 'index.html',
    'javascript': 'script.js',
    'typescript': 'index.ts',
    'python': 'script.py',
    'java': 'Main.java',
    'cpp': 'main.cpp',
    'c++': 'main.cpp',
    'go': 'main.go',
    'rust': 'main.rs',
    'css': 'styles.css',
    'json': 'data.json',
    'xml': 'data.xml',
    'sql': 'query.sql',
    'bash': 'script.sh',
    'shell': 'script.sh',
    'markdown': 'README.md',
  }
  
  return fileExtensions[lang] || `code.${lang}`
}

const getModelName = (id: string) => {
  return id.split('/').pop() || id
}

const getProviderIcon = (modelId: string) => {
  const id = modelId.toLowerCase()
  if (id.includes('openai') || id.includes('gpt')) return '/src/assets/provider-icons/openai.png'
  if (id.includes('anthropic') || id.includes('claude')) return '/src/assets/provider-icons/anthropic.png'
  if (id.includes('google') || id.includes('gemini')) return '/src/assets/provider-icons/google.png'
  if (id.includes('deepseek')) return '/src/assets/provider-icons/deepseek.png'
  if (id.includes('qwen')) return '/src/assets/provider-icons/qwen.png'
  if (id.includes('zhipu') || id.includes('glm')) return '/src/assets/provider-icons/zhipu.png'
  return '/src/assets/provider-icons/default.png'
}

const loadConversationHistory = async () => {
  const conversationId = route.query.conversationId as string
  if (!conversationId) {
    console.log('📭 没有conversationId，跳过加载历史')
    return
  }

  try {
    console.log('📡 开始加载代码模式历史会话:', conversationId)
    isLoading.value = true
    
    // 1. 加载会话详情，获取模型配置
    const conversationRes: any = await getConversation({ conversationId })
    console.log('📡 会话详情响应:', conversationRes)
    
    let conversation: any = null
    if (conversationRes.data && conversationRes.data.code === 0 && conversationRes.data.data) {
      conversation = conversationRes.data.data
      console.log('📋 会话信息:', conversation)
      
      // 先不设置模式，等加载消息后再根据消息内容判断
      
      // 解析模型配置（后端存储的是JSON数组字符串）
      if (conversation.models) {
        try {
          // 如果是JSON数组字符串，解析它
          const modelIds = typeof conversation.models === 'string' 
            ? JSON.parse(conversation.models) 
            : conversation.models
          
          if (currentMode.value === 'model-compare') {
            selectedModels.value = modelIds
            console.log('🤖 恢复模型配置:', modelIds)
          } else if (currentMode.value === 'prompt-experiment') {
            // 提示词实验模式：使用第一个模型
            if (Array.isArray(modelIds) && modelIds.length > 0) {
              selectedModel.value = modelIds[0]
              console.log('🤖 恢复提示词实验模型:', modelIds[0])
            } else if (typeof modelIds === 'string') {
              selectedModel.value = modelIds
              console.log('🤖 恢复提示词实验模型:', modelIds)
            }
          }
        } catch (e) {
          console.error('❌ 解析模型配置失败:', e)
          // 降级处理：尝试按逗号分隔（兼容旧数据）
          const modelIds = conversation.models.split(',').map((m: string) => m.trim())
          if (currentMode.value === 'model-compare') {
            selectedModels.value = modelIds
          } else if (currentMode.value === 'prompt-experiment' && modelIds.length > 0) {
            selectedModel.value = modelIds[0]
          }
        }
      }
    }
    
    // 2. 加载消息历史
    const res: any = await getConversationMessages({ conversationId })
    console.log('📡 消息历史响应:', res)
    
    if (res.data && res.data.code === 0 && res.data.data) {
      const historyMessages = res.data.data as any[]
      console.log('📨 原始历史消息数量:', historyMessages.length)
      
      // 判断是否为提示词实验模式（根据消息中的variantIndex）
      // 注意：后端使用的是 CODE_MODE 类型，所以需要通过消息中的 variantIndex 来判断
      const isPromptExperiment = historyMessages.some((m: any) => m.variantIndex !== null && m.variantIndex !== undefined)
      
      // 根据判断结果设置模式
      if (isPromptExperiment) {
        currentMode.value = 'prompt-experiment'
        console.log('🔄 根据消息内容检测到提示词实验模式，切换到提示词实验')
      } else {
        currentMode.value = 'model-compare'
        console.log('🔄 检测到模型对比模式，切换到模型对比')
      }
      
      // 在if块外声明loadedMessages，确保在最后可以使用
      let loadedMessages: Msg[] = []
      
      if (isPromptExperiment) {
        // 提示词实验模式：使用 messageIndex/2 + variantIndex 排序
        // 确保显示顺序为：用户消息1 -> AI消息1 -> 用户消息2 -> AI消息2
        console.log('📨 提示词实验模式 - 原始消息数量:', historyMessages.length)
        
        // 为每条消息计算排序键：messageIndex/2 + variantIndex
        const messagesWithSortKey = historyMessages.map((msg: any) => {
          const msgIdx = msg.messageIndex || 0
          const variantIdx = msg.variantIndex !== null && msg.variantIndex !== undefined ? msg.variantIndex : 0
          // 计算排序键：messageIndex/2 + variantIndex
          // 这样同一轮对话的不同变体会按variantIndex排序
          const sortKey = Math.floor(msgIdx / 2) * 1000 + variantIdx * 10 + (msg.role === 'user' ? 0 : 1)
          return {
            ...msg,
            sortKey
          }
        })
        
        // 按排序键排序
        messagesWithSortKey.sort((a, b) => a.sortKey - b.sortKey)
        
        console.log('📨 提示词实验模式 - 排序后的消息:', messagesWithSortKey.map((m: any) => ({
          messageIndex: m.messageIndex,
          variantIndex: m.variantIndex,
          role: m.role,
          sortKey: m.sortKey,
          content: m.content?.substring(0, 30) + '...'
        })))
        
        // 转换为UI需要的格式
        loadedMessages = []
        for (const msg of messagesWithSortKey) {
          if (msg.role === 'user') {
            loadedMessages.push({
              type: 'user',
              content: msg.content,
              variantIndex: msg.variantIndex !== null && msg.variantIndex !== undefined ? msg.variantIndex : undefined
            })
          } else if (msg.role === 'assistant') {
            // 解析codeBlocks JSON字符串
            let codeBlocks = []
            if (msg.codeBlocks) {
              try {
                codeBlocks = typeof msg.codeBlocks === 'string' 
                  ? JSON.parse(msg.codeBlocks) 
                  : msg.codeBlocks
              } catch (e) {
                console.error('❌ 解析codeBlocks失败:', e, 'raw:', msg.codeBlocks)
                codeBlocks = []
              }
            }
            
            loadedMessages.push({
              type: 'assistant',
              messageIndex: msg.messageIndex,
              responses: [{
                modelName: msg.modelName,
                variantIndex: msg.variantIndex !== null && msg.variantIndex !== undefined ? msg.variantIndex : undefined,
                fullContent: msg.content || '',
                done: true,
                hasCodeBlocks: codeBlocks.length > 0,
                codeBlocks: codeBlocks,
                responseTimeMs: msg.responseTimeMs,
                cost: msg.cost,
                hasError: false
              }]
            })
          }
        }
        
        console.log('✅ 提示词实验模式 - 加载完成，消息数量:', loadedMessages.length)
        messages.value = loadedMessages
        
        // 从历史消息中提取变体数量和内容，设置输入框
        // 只从第一轮对话（messageIndex=0）中提取，避免多轮对话时重复
        const variantMap = new Map<number, string>()
        const firstRoundMessages = historyMessages.filter((msg: any) => {
          const msgIdx = msg.messageIndex || 0
          return msgIdx === 0 || msgIdx === 1  // messageIndex 0是用户消息，1是assistant消息
        })
        
        firstRoundMessages.forEach((msg: any) => {
          if (msg.role === 'user' && msg.variantIndex !== null && msg.variantIndex !== undefined) {
            variantMap.set(msg.variantIndex, msg.content || '')
          }
        })
        
        // 按variantIndex排序
        const sortedVariants = Array.from(variantMap.entries())
          .sort((a, b) => a[0] - b[0])
          .map(([_, content]) => content)
        
        console.log('📝 从第一轮对话提取到的变体数量和内容:', sortedVariants.length, sortedVariants)
        
        // 设置变体输入框（至少2个，最多5个）
        if (sortedVariants.length >= 2) {
          // 如果变体数量超过5个，只取前5个
          variants.value = sortedVariants.slice(0, 5)
          console.log('✅ 已设置变体输入框，数量:', variants.value.length)
        } else if (sortedVariants.length === 1) {
          // 如果只有1个变体，补充一个空输入框（满足最小2个的要求）
          variants.value = [sortedVariants[0], '']
          console.log('✅ 已设置变体输入框（补充空输入框），数量:', variants.value.length)
        } else {
          // 如果没有找到变体，保持默认的2个空输入框
          variants.value = ['', '']
          console.log('⚠️ 未找到变体，使用默认的2个空输入框')
        }
      } else {
        // 模型对比模式：按messageIndex分组
        const messagesByIndex = new Map<number, any[]>()
        historyMessages.forEach((msg: any) => {
          const idx = msg.messageIndex || 0
          if (!messagesByIndex.has(idx)) {
            messagesByIndex.set(idx, [])
          }
          messagesByIndex.get(idx)!.push(msg)
        })
        
        console.log('📨 模型对比模式 - 分组后的消息:', messagesByIndex)
        
        // 转换为UI需要的格式
        loadedMessages = []
        const sortedIndexes = Array.from(messagesByIndex.keys()).sort((a, b) => a - b)
        
        for (const idx of sortedIndexes) {
          const msgs = messagesByIndex.get(idx)!
          const userMsg = msgs.find(m => m.role === 'user')
          const assistantMsgs = msgs.filter(m => m.role === 'assistant')
          
          if (userMsg) {
            loadedMessages.push({
              type: 'user',
              content: userMsg.content
            })
          }
          
          if (assistantMsgs.length > 0) {
            const responses = assistantMsgs.map(m => {
              // 解析codeBlocks JSON字符串
              let codeBlocks = []
              if (m.codeBlocks) {
                try {
                  codeBlocks = typeof m.codeBlocks === 'string' 
                    ? JSON.parse(m.codeBlocks) 
                    : m.codeBlocks
                  console.log('✅ 解析codeBlocks成功:', {
                    modelName: m.modelName,
                    codeBlocksType: typeof m.codeBlocks,
                    codeBlocksCount: codeBlocks.length,
                    codeBlocks: codeBlocks
                  })
                } catch (e) {
                  console.error('❌ 解析codeBlocks失败:', e, 'raw:', m.codeBlocks)
                  codeBlocks = []
                }
              } else {
                console.log('⚠️ 消息没有codeBlocks字段:', m.modelName)
              }
              
              const response = {
                modelName: m.modelName,
                fullContent: m.content || '',
                done: true,
                hasCodeBlocks: codeBlocks.length > 0,
                codeBlocks: codeBlocks,
                responseTimeMs: m.responseTimeMs,
                cost: m.cost,
                hasError: false
              }
              
              console.log('📦 构建的response对象:', {
                modelName: response.modelName,
                hasCodeBlocks: response.hasCodeBlocks,
                codeBlocksCount: response.codeBlocks?.length,
                contentLength: response.fullContent?.length
              })
              
              return response
            })
            
            loadedMessages.push({
              type: 'assistant',
              messageIndex: idx,
              responses
            })
          }
        }
        
        messages.value = loadedMessages
      }
      
      currentConversationId.value = conversationId
      console.log('✅ 历史消息加载完成:', loadedMessages.length, '条')
      
      // 如果是提示词实验模式，设置默认的预览tab为第一个变体
      if (isPromptExperiment && loadedMessages.length > 0) {
        // 等待nextTick确保messages已更新
        await nextTick()
        const variants = getCurrentVariants()
        console.log('🔄 [加载历史] 获取到的变体列表:', variants)
        if (variants.length > 0) {
          activePreviewTab.value = variants[0]
          console.log('🔄 [加载历史] 设置默认预览tab为变体', variants[0])
        } else {
          console.log('⚠️ [加载历史] 没有找到变体，activePreviewTab保持为', activePreviewTab.value)
        }
      }
      
      // 加载评分信息
      if (conversationId && loadedMessages.length > 0) {
        try {
          const ratingsRes = await getRatingsByConversationId(conversationId)
          if (ratingsRes.data && ratingsRes.data.code === 0 && ratingsRes.data.data) {
            const ratings = ratingsRes.data.data
            console.log('📊 加载评分信息:', ratings.length, '条')
            
            // 将评分信息关联到对应的消息
            // 在提示词实验模式下，同一轮对话的多个变体消息有相同的 messageIndex
            // 需要将评分信息关联到所有匹配的消息上
            ratings.forEach((rating: RatingVO) => {
              loadedMessages.forEach((m: Msg) => {
                if (m.type === 'assistant' && m.messageIndex === rating.messageIndex) {
                  m.rating = rating
                }
              })
            })
            
            console.log('📊 评分信息关联完成，已关联的消息:', loadedMessages.filter((m: Msg) => m.rating).length)
            
            // 更新messages
            messages.value = loadedMessages
          }
        } catch (error) {
          console.error('❌ 加载评分失败:', error)
        }
      }
      
      await nextTick()
      highlightCode()
      scrollToBottom()
    }
  } catch (error) {
    console.error('❌ 加载历史会话失败:', error)
    message.error('加载历史会话失败')
  } finally {
    isLoading.value = false
  }
}

// 判断是否应该显示评分按钮
const shouldShowRating = (msg: Msg, msgIndex: number) => {
  if (!msg || msg.type !== 'assistant' || !msg.responses) {
    return false
  }
  
  // 模型对比模式：需要至少2个响应
  if (currentMode.value === 'model-compare') {
    return msg.responses.length >= 2
  }
  
  // 提示词实验模式：只在一轮对话的最后一个变体下方显示评分按钮
  if (currentMode.value === 'prompt-experiment') {
    // 如果当前消息有多个响应（不应该发生，但为了安全）
    if (msg.responses.length >= 2) {
      return true
    }
    
    // 检查是否有其他 assistant 消息具有相同的 messageIndex
    // 在提示词实验模式下，同一轮对话的不同变体会有相同的 messageIndex
    if (msg.messageIndex !== undefined) {
      const sameRoundMessages = messages.value.filter((m: Msg) => 
        m.type === 'assistant' && 
        m.messageIndex === msg.messageIndex &&
        m.responses && 
        m.responses.length > 0 &&
        m.responses.every((r: any) => r.done)
      )
      
      // 如果有2个或更多同一轮对话的响应
      if (sameRoundMessages.length >= 2) {
        // 找到当前消息在同一轮对话中的位置
        const currentVariantIndex = msg.responses[0]?.variantIndex
        if (currentVariantIndex !== undefined) {
          // 获取所有变体的 variantIndex，并排序
          const variantIndices = sameRoundMessages
            .map((m: Msg) => m.responses?.[0]?.variantIndex)
            .filter((idx: number | undefined) => idx !== undefined)
            .sort((a: number, b: number) => a - b)
          
          // 如果当前消息是最后一个变体（variantIndex 最大），才显示评分按钮
          const maxVariantIndex = Math.max(...variantIndices)
          return currentVariantIndex === maxVariantIndex
        }
      }
    }
    
    return false
  }
  
  return false
}

// 判断模型按钮是否应该被选中
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

// 判断变体按钮是否应该被选中（提示词实验模式）
const isVariantSelected = (msg: Msg, variantIndex: number | undefined, respIndex: number) => {
  if (!msg.rating) return false
  
  const rating = msg.rating
  // 对于提示词实验模式，使用variantIndex来判断
  if (variantIndex !== undefined) {
    // 如果rating中有winnerModel，需要找到对应的variantIndex
    if (rating.ratingType === 'model_better' && rating.winnerModel) {
      // 在提示词实验模式下，winnerModel实际上存储的是variantIndex
      // 需要从responses中找到对应的variantIndex
      const winnerResp = msg.responses?.find((r: any) => r.modelName === rating.winnerModel)
      if (winnerResp && winnerResp.variantIndex === variantIndex) {
        return true
      }
    }
  }
  
  return false
}

// 处理用户评分（模型对比模式）
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
            userId: loginUser.value?.id || 0,
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
  }
}

// 获取同一轮对话的所有变体消息（提示词实验模式）
const getSameRoundVariantMessages = (msg: Msg, msgIndex: number) => {
  if (!msg || msg.messageIndex === undefined) {
    return []
  }
  
  // 找到所有具有相同 messageIndex 的 assistant 消息
  const sameRoundMessages = messages.value.filter((m: Msg) => 
    m.type === 'assistant' && 
    m.messageIndex === msg.messageIndex &&
    m.responses && 
    m.responses.length > 0 &&
    m.responses.every((r: any) => r.done)
  )
  
  // 提取每个消息的变体信息
  return sameRoundMessages.map((m: Msg) => {
    const resp = m.responses?.[0]
    return {
      variantIndex: resp?.variantIndex,
      modelName: resp?.modelName
    }
  }).filter((v: any) => v.variantIndex !== undefined)
}

// 判断变体按钮是否应该被选中（提示词实验模式，同一轮对话）
const isVariantSelectedForRound = (msg: Msg, variantIndex: number | undefined) => {
  if (variantIndex === undefined) {
    return false
  }
  
  // 在提示词实验模式下，同一轮对话的多个消息有相同的 messageIndex
  // 需要从同一轮对话的所有消息中查找评分信息
  if (msg.messageIndex !== undefined) {
    // 找到同一轮对话的所有消息，查找评分信息
    const sameRoundMessages = messages.value.filter((m: Msg) => 
      m.type === 'assistant' && 
      m.messageIndex === msg.messageIndex
    )
    
    // 从同一轮对话的消息中查找评分信息（可能在任何一条消息上）
    const rating = sameRoundMessages.find((m: Msg) => m.rating)?.rating
    
    if (!rating) {
      return false
    }
    
    // 在提示词实验模式下，直接比较 winnerVariantIndex
    if (rating.ratingType === 'model_better' && rating.winnerVariantIndex !== undefined && rating.winnerVariantIndex !== null) {
      return rating.winnerVariantIndex === variantIndex
    }
  }
  
  return false
}

// 处理变体评分（提示词实验模式，同一轮对话）
const handleVariantRatingForRound = async (msg: Msg, variantIndex: number | undefined) => {
  if (!msg || msg.type !== 'assistant' || variantIndex === undefined) {
    return
  }
  
  const conversationId = route.query.conversationId as string
  if (!conversationId) {
    message.warning('请先开始对话')
    return
  }
  
  try {
    // 提交评分，直接使用 variantIndex
    const res: any = await addRating({
      conversationId,
      messageIndex: msg.messageIndex!,
      ratingType: 'model_better',
      winnerModel: undefined,  // 在提示词实验模式下，不需要 winnerModel
      loserModel: undefined,
      winnerVariantIndex: variantIndex,  // 直接使用 variantIndex
      loserVariantIndex: undefined
    })
    
    if (res.data && res.data.code === 0) {
      // 更新所有同一轮对话的消息的评分信息
      const sameRoundMessages = messages.value.filter((m: Msg) => 
        m.type === 'assistant' && 
        m.messageIndex === msg.messageIndex
      )
      
      sameRoundMessages.forEach((m: Msg) => {
        m.rating = {
          id: '',
          conversationId,
          messageIndex: msg.messageIndex!,
          userId: loginUser.value?.id || 0,
          ratingType: 'model_better',
          winnerModel: undefined,
          loserModel: undefined,
          winnerVariantIndex: variantIndex,
          loserVariantIndex: undefined,
          createTime: new Date().toISOString()
        } as RatingVO
      })
      
      // 强制触发响应式更新
      messages.value = [...messages.value]
      await nextTick()
      message.success('评分成功')
    }
  } catch (error) {
    console.error('评分失败:', error)
    message.error('评分失败')
  }
}

// 处理变体评分（提示词实验模式）
const handleVariantRating = async (msgIndex: number, variantIndex: number | undefined) => {
  const msg = messages.value[msgIndex]
  if (!msg || msg.type !== 'assistant' || !msg.responses || variantIndex === undefined) {
    return
  }

  // 找到对应variantIndex的响应
  const variantResp = msg.responses.find((r: any) => r.variantIndex === variantIndex)
  if (!variantResp || !variantResp.modelName) {
    console.error('未找到对应的变体响应')
    return
  }

  // 使用模型名称作为winnerModel（在提示词实验模式下，每个变体对应一个模型响应）
  await handleRating(msgIndex, 'model_better', variantResp.modelName)
}

// 监听路由参数变化
watch(() => route.query.conversationId, (newId, oldId) => {
  console.log('🔄 conversationId变化:', oldId, '->', newId, 'isLoading:', isLoading.value)
  
  // 如果正在加载中（流式传输），不要加载历史，避免清空正在显示的消息
  if (isLoading.value) {
    console.log('⏸️ 正在流式传输，跳过加载历史会话')
    return
  }
  
  if (newId && newId !== oldId) {
    console.log('📥 加载历史会话')
    loadConversationHistory()
  } else if (!newId && oldId) {
    // 切换到新对话，清空状态
    console.log('🆕 切换到新对话')
    messages.value = []
    userInput.value = ''
    variants.value = ['', '']  // 清除提示词变体输入框
    currentConversationId.value = undefined
    activePreviewTab.value = 0
  }
}, { immediate: true })

onMounted(() => {
  loadModels()
  loadConversations()
})
</script>

<style scoped>
.code-mode-page-wrapper {
  position: fixed;
  top: 0;
  left: 0;
  right: 0;
  bottom: 0;
  margin: 0;
  padding: 0;
  overflow: hidden;
}

.code-mode-page {
  display: flex;
  height: 100vh;
  width: 100vw;
  background: #fff;
  overflow: hidden;
  box-sizing: border-box;
  margin: 0;
  padding: 0;
}

.left-sidebar {
  width: 260px;
  min-width: 260px;
  max-width: 260px;
  background: #f7f7f8;
  border-right: 1px solid #e5e7eb;
  display: flex;
  flex-direction: column;
}

.logo-section {
  padding: 16px;
  display: flex;
  align-items: center;
  gap: 10px;
  border-bottom: 1px solid #e5e7eb;
}

.logo-img {
  width: 28px;
  height: 28px;
}

.app-name {
  margin: 0;
  font-size: 16px;
  font-weight: 600;
  color: #111827;
}

.nav-buttons {
  padding: 12px;
  border-bottom: 1px solid #e5e7eb;
}

.nav-btn {
  width: 100%;
  padding: 10px 14px;
  background: transparent;
  border: none;
  border-radius: 8px;
  display: flex;
  align-items: center;
  gap: 10px;
  cursor: pointer;
  font-size: 14px;
  color: #374151;
  transition: background 0.15s;
  margin-bottom: 4px;
}

.nav-btn:hover {
  background: #e5e7eb;
}

.history-area {
  flex: 1;
  padding: 16px 12px;
  overflow-y: auto;
}

.history-group {
  margin-bottom: 20px;
}

.group-label {
  font-size: 11px;
  color: #6b7280;
  font-weight: 600;
  margin-bottom: 8px;
}

.history-item {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 8px 12px;
  border-radius: 6px;
  cursor: pointer;
  transition: background 0.15s;
  margin-bottom: 2px;
}

.history-item:hover {
  background: #e5e7eb;
}

.history-item.active {
  background: #e0e7ff;
  color: #4f46e5;
}

.item-icon {
  font-size: 14px;
  color: #6b7280;
}

.history-item.active .item-icon {
  color: #4f46e5;
}

.history-title {
  font-size: 13px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.loading-more,
.no-more {
  text-align: center;
  padding: 12px;
  font-size: 11px;
  color: #9ca3af;
}

.middle-chat {
  width: calc(40% - 104px);
  min-width: calc(40% - 104px);
  max-width: calc(40% - 104px);
  display: flex;
  flex-direction: column;
  border-right: 1px solid #e5e7eb;
  position: relative;
  overflow: hidden;
}

.top-models-bar {
  padding: 12px 16px;
  background: #ffffff;
  border-bottom: 1px solid #e5e7eb;
  flex-shrink: 0;
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.mode-selector-row {
  display: flex;
  align-items: center;
  flex-shrink: 0;
}

.models-selector {
  display: flex;
  align-items: center;
  gap: 12px;
  overflow-x: auto;
  scrollbar-width: thin;
}

.models-selector::-webkit-scrollbar {
  height: 4px;
}

.models-selector::-webkit-scrollbar-thumb {
  background: #d1d5db;
  border-radius: 2px;
}

.vs-label {
  font-size: 13px;
  color: #9ca3af;
  font-weight: 500;
}

.welcome-view {
  flex: 1;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  padding: 40px 20px;
  overflow-y: auto;
}

.main-title {
  font-size: 32px;
  font-weight: 400;
  color: #111827;
  margin: 0 0 12px 0;
}

.sub-title {
  font-size: 16px;
  color: #6b7280;
  margin: 0 0 40px 0;
}

.quick-examples {
  display: grid;
  grid-template-columns: repeat(2, 1fr);
  gap: 12px;
  max-width: 600px;
  width: 100%;
}

.example-btn {
  padding: 16px 20px;
  background: #ffffff;
  border: 1px solid #e5e7eb;
  border-radius: 8px;
  cursor: pointer;
  font-size: 13px;
  color: #374151;
  transition: all 0.15s;
  text-align: left;
  line-height: 1.5;
}

.example-btn:hover {
  border-color: #4f46e5;
  background: #f5f3ff;
}

.messages-wrapper {
  flex: 1;
  overflow-y: auto;
  padding: 20px;
  padding-bottom: 300px;
  min-height: 0;
}

.msg-block {
  margin-bottom: 24px;
}

.user-msg {
  display: flex;
  justify-content: flex-end;
  margin-bottom: 16px;
}

.user-bubble {
  background: #f3f4f6;
  padding: 12px 16px;
  border-radius: 12px;
  max-width: 80%;
  font-size: 14px;
  color: #111827;
  display: flex;
  align-items: flex-start;
  gap: 8px;
}

.variant-label-inline {
  font-weight: 600;
  color: #4f46e5;
  flex-shrink: 0;
}

.variant-content-inline {
  flex: 1;
}

.ai-msg {
  margin-bottom: 20px;
}

.ai-responses {
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.response-item {
  background: #f9fafb;
  border: 1px solid #e5e7eb;
  border-radius: 8px;
  padding: 12px;
}

.response-header {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-bottom: 8px;
  padding-bottom: 8px;
  border-bottom: 1px solid #e5e7eb;
}

.model-icon {
  width: 18px;
  height: 18px;
  border-radius: 4px;
}

.model-name {
  font-size: 13px;
  font-weight: 500;
  color: #374151;
  flex: 1;
}

.stats {
  display: flex;
  gap: 8px;
  font-size: 11px;
  color: #9ca3af;
}

.response-content {
  font-size: 13px;
  line-height: 1.6;
  color: #4b5563;
  word-wrap: break-word;
  word-break: break-word;
  overflow-wrap: break-word;
  max-width: 100%;
  overflow-x: hidden;
}

.text-description {
  margin-bottom: 12px;
  padding-bottom: 12px;
  border-bottom: 1px solid #f0f0f0;
}

/* 覆盖 MarkdownRenderer 的深色代码块样式，改为浅色 */
.response-content :deep(.markdown-body pre) {
  background-color: #f8f9fa !important;
  border: 1px solid #e5e7eb !important;
  box-shadow: none !important;
}

.response-content :deep(.markdown-body pre code) {
  color: #24292f !important;
  background-color: transparent !important;
}

.response-content :deep(.markdown-body code) {
  background-color: rgba(175, 184, 193, 0.2) !important;
  color: #24292f !important;
}

/* 覆盖 highlight.js 深色主题，改为浅色 */
.response-content :deep(.hljs) {
  background: #f8f9fa !important;
  color: #24292f !important;
}

.response-content :deep(.hljs-keyword),
.response-content :deep(.hljs-selector-tag),
.response-content :deep(.hljs-literal),
.response-content :deep(.hljs-section),
.response-content :deep(.hljs-link) {
  color: #d73a49 !important;
  font-weight: 600;
}

.response-content :deep(.hljs-string),
.response-content :deep(.hljs-attr),
.response-content :deep(.hljs-attribute) {
  color: #032f62 !important;
}

.response-content :deep(.hljs-number) {
  color: #005cc5 !important;
}

.response-content :deep(.hljs-comment) {
  color: #6a737d !important;
  font-style: italic;
}

.response-content :deep(.hljs-function),
.response-content :deep(.hljs-title) {
  color: #6f42c1 !important;
  font-weight: 600;
}

.response-content :deep(.hljs-built_in),
.response-content :deep(.hljs-class) {
  color: #e36209 !important;
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

.code-generated-hint {
  display: flex;
  align-items: center;
  padding: 12px 16px;
  background: #ecfdf5;
  border: 1px solid #a7f3d0;
  border-radius: 8px;
  font-size: 14px;
  margin: 8px 0;
}

.error-message {
  margin: 8px 0;
}

@keyframes bounce {
  0%, 60%, 100% {
    transform: translateY(0);
  }
  30% {
    transform: translateY(-6px);
  }
}

.input-zone {
  position: absolute;
  bottom: 0;
  left: 0;
  right: 0;
  background: linear-gradient(180deg, transparent, #fff 20%);
  padding: 40px 20px 20px;
}

.input-card {
  background: #fff;
  border: 1px solid #d1d5db;
  border-radius: 12px;
  box-shadow: 0 2px 6px rgba(0, 0, 0, 0.04);
}

.text-input {
  width: 100%;
  border: none;
  outline: none;
  padding: 15px 18px;
  font-size: 14px;
  line-height: 1.5;
  resize: none;
  min-height: 60px;
  max-height: 150px;
}

.text-input::placeholder {
  color: #9ca3af;
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
  transition: all 0.15s;
  font-size: 16px;
}

.tool-icon:hover {
  background: #f3f4f6;
}

.code-mode-active {
  background: #e0e7ff;
  color: #4f46e5;
}

.send-icon {
  width: 36px;
  height: 36px;
  border: none;
  background: #111827;
  color: #fff;
  border-radius: 8px;
  cursor: pointer;
  display: flex;
  align-items: center;
  justify-content: center;
  transition: background 0.15s;
}

.send-icon:hover:not(:disabled) {
  background: #374151;
}

.send-icon:disabled {
  background: #d1d5db;
  cursor: not-allowed;
}

.right-preview {
  width: calc(60% - 188px);
  min-width: calc(60% - 188px);
  max-width: calc(60% - 188px);
  display: flex;
  flex-direction: column;
  background: #fafafa;
  overflow: hidden;
  margin-right: 32px;
}

.preview-tabs {
  background: #ffffff;
  border-bottom: 1px solid #e5e7eb;
  flex-shrink: 0;
}

.tabs-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 12px 16px;
  gap: 16px;
}

.tabs-left {
  display: flex;
  gap: 8px;
  overflow-x: auto;
  scrollbar-width: thin;
  flex: 1;
}

.tabs-actions {
  display: flex;
  gap: 8px;
  flex-shrink: 0;
  align-items: center;
}

.download-btn {
  border-radius: 6px;
  font-weight: 500;
  box-shadow: 0 2px 4px rgba(0, 0, 0, 0.1);
  transition: all 0.3s ease;
}

.download-btn:hover {
  box-shadow: 0 4px 8px rgba(0, 0, 0, 0.15);
  transform: translateY(-1px);
}

.download-btn:active {
  transform: translateY(0);
}

.tabs-header::-webkit-scrollbar {
  height: 4px;
}

.tabs-header::-webkit-scrollbar-thumb {
  background: #d1d5db;
  border-radius: 2px;
}

.tab-item {
  display: flex;
  align-items: center;
  gap: 6px;
  padding: 8px 14px;
  background: #f3f4f6;
  border: 1px solid #e5e7eb;
  border-radius: 6px;
  cursor: pointer;
  transition: all 0.15s;
  white-space: nowrap;
  flex-shrink: 0;
}

.tab-item:hover {
  background: #e5e7eb;
}

.tab-item.active {
  background: #4f46e5;
  border-color: #4f46e5;
  color: #ffffff;
}

.tab-icon {
  width: 16px;
  height: 16px;
  border-radius: 3px;
}

.tab-name {
  font-size: 13px;
  font-weight: 500;
}

.tab-item.active .tab-name {
  color: #ffffff;
}

.preview-content {
  flex: 1;
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 0;
  overflow: hidden;
}

.preview-wrapper {
  width: 100%;
  height: 100%;
  background: #ffffff;
  border: none;
  border-radius: 0;
  overflow: hidden;
  box-shadow: none;
  padding: 0;
  margin: 0;
  box-sizing: border-box;
}

.preview-iframe {
  width: 100%;
  height: 100%;
  border: none;
  border-radius: 4px;
  background: #fff;
}

.preview-empty {
  text-align: center;
  color: #9ca3af;
  display: flex;
  flex-direction: column;
  align-items: center;
}

.preview-empty p {
  margin: 4px 0;
}

/* 代码块折叠样式 */
.code-block-container {
  margin: 12px 0;
  border: 1px solid #1e293b;
  border-radius: 8px;
  overflow: hidden;
  background: #ffffff;
  transition: all 0.3s ease;
}

.code-block-container:hover {
  border-color: #0f172a;
  box-shadow: 0 2px 8px rgba(15, 23, 42, 0.15);
}

.code-block-container.generating {
  border: 1px solid #3b82f6;
  background: linear-gradient(135deg, #f8f9ff 0%, #f3f4ff 100%);
  box-shadow: 0 2px 8px rgba(59, 130, 246, 0.1);
  animation: pulse-border 2s ease-in-out infinite;
}

@keyframes pulse-border {
  0%, 100% {
    border-color: #3b82f6;
    box-shadow: 0 2px 8px rgba(59, 130, 246, 0.1);
  }
  50% {
    border-color: #6366f1;
    box-shadow: 0 4px 12px rgba(99, 102, 241, 0.2);
  }
}

.code-block-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 12px 16px;
  background: #f8fafc;
  cursor: pointer;
  user-select: none;
  transition: background 0.2s ease;
}

.code-block-header:hover {
  background: #f1f5f9;
}

.code-block-header.generating {
  background: linear-gradient(135deg, #f8f9ff 0%, #f3f4ff 100%);
  cursor: pointer;
  padding: 16px;
}

.code-block-header.generating:hover {
  background: linear-gradient(135deg, #f0f1ff 0%, #e8eaff 100%);
}

.code-block-actions-right {
  display: flex;
  align-items: center;
  gap: 12px;
}

.code-block-title {
  display: flex;
  align-items: center;
  font-size: 14px;
  font-weight: 500;
  color: #1e293b;
}

.filename {
  color: #0f172a;
  font-family: 'SF Mono', 'Consolas', 'Monaco', 'Courier New', monospace;
  margin-right: 12px;
  font-weight: 600;
}

.file-size {
  font-size: 12px;
  color: #64748b;
  font-weight: 400;
}

.code-block-actions {
  display: flex;
  align-items: center;
  gap: 8px;
}

.expand-icon {
  color: #64748b;
  font-size: 12px;
  transition: transform 0.3s ease;
}

.generating-dots {
  display: flex;
  gap: 4px;
  align-items: center;
  margin-left: 8px;
}

.generating-dots span {
  width: 5px;
  height: 5px;
  background: #3b82f6;
  border-radius: 50%;
  animation: bounce-dot 1.4s infinite ease-in-out both;
}

.generating-dots span:nth-child(1) {
  animation-delay: -0.32s;
}

.generating-dots span:nth-child(2) {
  animation-delay: -0.16s;
}

@keyframes bounce-dot {
  0%, 80%, 100% {
    transform: scale(0.8);
    opacity: 0.5;
  }
  40% {
    transform: scale(1.2);
    opacity: 1;
  }
}

.generating-status {
  font-size: 12px;
  color: #6366f1;
  font-weight: 500;
  animation: fade-pulse 2s ease-in-out infinite;
}

@keyframes fade-pulse {
  0%, 100% {
    opacity: 0.6;
  }
  50% {
    opacity: 1;
  }
}

.code-block-content {
  border-top: 1px solid #e5e7eb;
  background: #f8f9fa;
  max-height: 400px;
  overflow: auto;
  animation: slideDown 0.3s ease;
}

.code-block-content.generating {
  background: #ffffff;
  border-top: 1px solid #e0e7ff;
  position: relative;
}

.code-block-content.generating::after {
  content: '';
  position: absolute;
  bottom: 0;
  left: 0;
  right: 0;
  height: 3px;
  background: linear-gradient(90deg, #6366f1 0%, #4f46e5 50%, #6366f1 100%);
  background-size: 200% 100%;
  animation: shimmer 2s linear infinite;
}

@keyframes shimmer {
  0% {
    background-position: -200% 0;
  }
  100% {
    background-position: 200% 0;
  }
}

@keyframes slideDown {
  from {
    max-height: 0;
    opacity: 0;
  }
  to {
    max-height: 400px;
    opacity: 1;
  }
}

.code-block-content pre {
  margin: 0;
  padding: 16px;
  background: #f8f9fa;
}

.code-block-content code {
  font-family: 'SF Mono', 'Consolas', 'Monaco', 'Courier New', monospace;
  font-size: 13px;
  line-height: 1.6;
  color: #24292f;
  display: block;
  white-space: pre;
  overflow-x: auto;
}

/* 浅色主题代码高亮 */
.code-block-content :deep(.hljs-keyword),
.code-block-content :deep(.hljs-selector-tag),
.code-block-content :deep(.hljs-tag),
.code-block-content :deep(.hljs-name) {
  color: #d73a49;
  font-weight: 600;
}

.code-block-content :deep(.hljs-string),
.code-block-content :deep(.hljs-attr),
.code-block-content :deep(.hljs-attribute) {
  color: #032f62;
}

.code-block-content :deep(.hljs-number),
.code-block-content :deep(.hljs-literal) {
  color: #005cc5;
}

.code-block-content :deep(.hljs-comment) {
  color: #6a737d;
  font-style: italic;
}

.code-block-content :deep(.hljs-function),
.code-block-content :deep(.hljs-title) {
  color: #6f42c1;
  font-weight: 600;
}

.code-block-content :deep(.hljs-built_in),
.code-block-content :deep(.hljs-class) {
  color: #e36209;
}

.code-block-content::-webkit-scrollbar {
  width: 8px;
  height: 8px;
}

.code-block-content::-webkit-scrollbar-track {
  background: #f1f3f5;
}

.code-block-content::-webkit-scrollbar-thumb {
  background: #c1c7cd;
  border-radius: 4px;
}

.code-block-content::-webkit-scrollbar-thumb:hover {
  background: #a8aeb5;
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
  font-size: 13px;
  user-select: none;
  display: flex;
  align-items: center;
  list-style: none;
  position: relative;
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

.thinking-content::-webkit-scrollbar {
  width: 6px;
}

.thinking-content::-webkit-scrollbar-track {
  background: #f1f1f1;
  border-radius: 3px;
}

.thinking-content::-webkit-scrollbar-thumb {
  background: #c1c7cd;
  border-radius: 3px;
}

.thinking-content::-webkit-scrollbar-thumb:hover {
  background: #a8aeb5;
}

/* 提示词实验模式样式 */
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

/* 评分样式 */
.rating-section {
  margin-top: 16px;
  padding-top: 16px;
  border-top: 1px solid #e5e7eb;
}

.rating-buttons {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  align-items: center;
}

.rating-btn {
  padding: 6px 14px;
  border: 1px solid #d1d5db;
  border-radius: 6px;
  background: #ffffff;
  color: #374151;
  font-size: 13px;
  font-weight: 500;
  cursor: pointer;
  transition: all 0.15s;
}

.rating-btn:hover {
  background: #f3f4f6;
  border-color: #9ca3af;
}

.rating-btn.rating-selected {
  background: #4f46e5;
  border-color: #4f46e5;
  color: #ffffff;
}

.rating-btn.rating-selected:hover {
  background: #4338ca;
  border-color: #4338ca;
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
</style>
