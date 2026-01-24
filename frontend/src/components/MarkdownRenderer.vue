<template>
  <div class="markdown-renderer">
    <!-- 混合渲染：普通文本和代码块交替显示 -->
    <template v-for="(segment, index) in contentSegments" :key="index">
      <!-- 普通Markdown文本 -->
      <div v-if="segment.type === 'text'" class="markdown-body" v-html="segment.html"></div>
      
      <!-- 代码块预览组件 -->
      <CodePreview v-else-if="segment.type === 'code'" :code-block="segment.block" />
    </template>
  </div>
</template>

<script setup lang="ts">
/**
 * Markdown 渲染组件
 *
 * @author <a href="https://codefather.cn">编程导航学习圈</a>
 */
import { computed } from 'vue'
import { marked } from 'marked'
import hljs from 'highlight.js/lib/core'
import CodePreview from './CodePreview.vue'

// 注册更多语言支持
import javascript from 'highlight.js/lib/languages/javascript'
import typescript from 'highlight.js/lib/languages/typescript'
import python from 'highlight.js/lib/languages/python'
import java from 'highlight.js/lib/languages/java'
import cpp from 'highlight.js/lib/languages/cpp'
import go from 'highlight.js/lib/languages/go'
import rust from 'highlight.js/lib/languages/rust'
import sql from 'highlight.js/lib/languages/sql'
import bash from 'highlight.js/lib/languages/bash'
import json from 'highlight.js/lib/languages/json'
import xml from 'highlight.js/lib/languages/xml'
import css from 'highlight.js/lib/languages/css'
import markdown from 'highlight.js/lib/languages/markdown'

hljs.registerLanguage('javascript', javascript)
hljs.registerLanguage('typescript', typescript)
hljs.registerLanguage('python', python)
hljs.registerLanguage('java', java)
hljs.registerLanguage('cpp', cpp)
hljs.registerLanguage('c++', cpp)
hljs.registerLanguage('go', go)
hljs.registerLanguage('rust', rust)
hljs.registerLanguage('sql', sql)
hljs.registerLanguage('bash', bash)
hljs.registerLanguage('shell', bash)
hljs.registerLanguage('json', json)
hljs.registerLanguage('xml', xml)
hljs.registerLanguage('html', xml)
hljs.registerLanguage('css', css)
hljs.registerLanguage('markdown', markdown)

interface CodeBlock {
  language: string
  code: string
  sanitizedHtml?: string
}

interface ContentSegment {
  type: 'text' | 'code'
  html?: string
  block?: CodeBlock
}

const props = defineProps<{
  content: string
  codeBlocks?: CodeBlock[]
}>()

// 配置marked（代码块将由CodePreview组件处理，这里不高亮）
marked.setOptions({
  breaks: true,
  gfm: true,
})

/**
 * 从内容中提取代码块
 */
const extractCodeBlocks = (text: string): CodeBlock[] => {
  const codeBlockPattern = /```(\w*)\n([\s\S]*?)```/g
  const blocks: CodeBlock[] = []
  let match

  while ((match = codeBlockPattern.exec(text)) !== null) {
    const language = match[1] || 'text'
    const code = match[2]
    
    blocks.push({
      language: language.toLowerCase(),
      code: code,
      sanitizedHtml: language.toLowerCase() === 'html' ? code : undefined
    })
  }

  return blocks
}

/**
 * 将内容分割成文本和代码块片段
 */
const contentSegments = computed((): ContentSegment[] => {
  if (!props.content) return []

  try {
    // 提取所有代码块
    const extractedBlocks = extractCodeBlocks(props.content)
    
    if (extractedBlocks.length === 0) {
      // 没有代码块，直接渲染整个内容
      return [{
        type: 'text',
        html: marked(props.content)
      }]
    }

    // 有代码块，需要分割内容
    const segments: ContentSegment[] = []
    const codeBlockPattern = /```\w*\n[\s\S]*?```/g
    
    let lastIndex = 0
    let blockIndex = 0
    let match

    const content = props.content
    // 重置正则的lastIndex
    codeBlockPattern.lastIndex = 0

    while ((match = codeBlockPattern.exec(content)) !== null) {
      // 添加代码块之前的文本
      if (match.index > lastIndex) {
        const textContent = content.substring(lastIndex, match.index)
        if (textContent.trim()) {
          segments.push({
            type: 'text',
            html: marked(textContent)
          })
        }
      }

      // 添加代码块
      if (blockIndex < extractedBlocks.length) {
        segments.push({
          type: 'code',
          block: extractedBlocks[blockIndex]
        })
        blockIndex++
      }

      lastIndex = match.index + match[0].length
    }

    // 添加最后剩余的文本
    if (lastIndex < content.length) {
      const textContent = content.substring(lastIndex)
      if (textContent.trim()) {
        segments.push({
          type: 'text',
          html: marked(textContent)
        })
      }
    }

    return segments

  } catch (err) {
    console.error('内容分割失败:', err)
    return [{
      type: 'text',
      html: marked(props.content)
    }]
  }
})
</script>

<style scoped>
.markdown-renderer {
  width: 100%;
}

.markdown-body {
  font-size: 14px;
  line-height: 1.7;
  color: #374151;
  word-wrap: break-word;
}

/* 思考内容中的markdown样式更小 */
.thinking-content .markdown-body {
  font-size: 12px;
  color: #4b5563;
}

.thinking-content .markdown-body :deep(code) {
  font-size: 11px;
}

.thinking-content .markdown-body :deep(pre) {
  padding: 8px;
  font-size: 11px;
}

/* 标题样式 */
.markdown-body :deep(h1),
.markdown-body :deep(h2),
.markdown-body :deep(h3),
.markdown-body :deep(h4),
.markdown-body :deep(h5),
.markdown-body :deep(h6) {
  margin-top: 24px;
  margin-bottom: 16px;
  font-weight: 600;
  line-height: 1.25;
  color: #1f2937;
}

.markdown-body :deep(h1) {
  font-size: 2em;
  border-bottom: 1px solid #e5e7eb;
  padding-bottom: 0.3em;
}

.markdown-body :deep(h2) {
  font-size: 1.5em;
  border-bottom: 1px solid #e5e7eb;
  padding-bottom: 0.3em;
}

.markdown-body :deep(h3) {
  font-size: 1.25em;
}

/* 段落样式 */
.markdown-body :deep(p) {
  margin-top: 0;
  margin-bottom: 16px;
}

/* 列表样式 */
.markdown-body :deep(ul),
.markdown-body :deep(ol) {
  margin-top: 0;
  margin-bottom: 16px;
  padding-left: 2em;
}

.markdown-body :deep(li) {
  margin-top: 0.25em;
}

/* 代码块样式（深色主题） */
.markdown-body :deep(pre) {
  background-color: #0d1117 !important;
  border-radius: 8px;
  padding: 16px;
  overflow: auto;
  margin-bottom: 16px;
  border: 1px solid #30363d;
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.2);
}

.markdown-body :deep(pre code) {
  background-color: transparent !important;
  padding: 0;
  font-size: 13px;
  border-radius: 0;
  font-family: 'SF Mono', 'Consolas', 'Monaco', 'Courier New', monospace;
  display: block;
  line-height: 1.6;
  color: #c9d1d9;
}

.markdown-body :deep(code) {
  background-color: rgba(175, 184, 193, 0.2);
  color: #ff7b72;
  padding: 0.2em 0.4em;
  margin: 0;
  font-size: 85%;
  border-radius: 4px;
  font-family: 'SF Mono', 'Consolas', 'Monaco', 'Courier New', monospace;
  font-weight: 500;
}

/* highlight.js 语法高亮增强（GitHub Dark主题配色） */
.markdown-body :deep(.hljs) {
  background: transparent !important;
}

.markdown-body :deep(.hljs-keyword),
.markdown-body :deep(.hljs-selector-tag),
.markdown-body :deep(.hljs-literal),
.markdown-body :deep(.hljs-section),
.markdown-body :deep(.hljs-link) {
  color: #ff7b72;
  font-weight: 600;
}

.markdown-body :deep(.hljs-string),
.markdown-body :deep(.hljs-attr),
.markdown-body :deep(.hljs-attribute) {
  color: #a5d6ff;
}

.markdown-body :deep(.hljs-number),
.markdown-body :deep(.hljs-regexp),
.markdown-body :deep(.hljs-meta) {
  color: #79c0ff;
}

.markdown-body :deep(.hljs-built_in),
.markdown-body :deep(.hljs-builtin-name) {
  color: #ffa657;
}

.markdown-body :deep(.hljs-comment),
.markdown-body :deep(.hljs-quote) {
  color: #8b949e;
  font-style: italic;
}

.markdown-body :deep(.hljs-tag),
.markdown-body :deep(.hljs-name),
.markdown-body :deep(.hljs-selector-id),
.markdown-body :deep(.hljs-selector-class) {
  color: #7ee787;
}

.markdown-body :deep(.hljs-function),
.markdown-body :deep(.hljs-title) {
  color: #d2a8ff;
}

.markdown-body :deep(.hljs-variable),
.markdown-body :deep(.hljs-template-variable) {
  color: #ffa657;
}

/* 引用样式 */
.markdown-body :deep(blockquote) {
  margin: 0 0 16px 0;
  padding: 0 1em;
  color: #6b7280;
  border-left: 0.25em solid #d1d5db;
}

/* 链接样式 */
.markdown-body :deep(a) {
  color: #58a6ff;
  text-decoration: none;
}

.markdown-body :deep(a:hover) {
  text-decoration: underline;
}

/* 表格样式 */
.markdown-body :deep(table) {
  border-collapse: collapse;
  width: 100%;
  margin-bottom: 16px;
}

.markdown-body :deep(table th),
.markdown-body :deep(table td) {
  padding: 6px 13px;
  border: 1px solid #d1d5db;
}

.markdown-body :deep(table th) {
  font-weight: 600;
  background-color: #f3f4f6;
}

/* 水平线样式 */
.markdown-body :deep(hr) {
  height: 0.25em;
  padding: 0;
  margin: 24px 0;
  background-color: #e5e7eb;
  border: 0;
}

/* 图片样式 */
.markdown-body :deep(img) {
  max-width: 100%;
  box-sizing: content-box;
}
</style>
