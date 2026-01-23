<template>
  <div class="markdown-renderer">
    <div class="markdown-body" v-html="renderedContent"></div>
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

const props = defineProps<{
  content: string
}>()

marked.setOptions({
  breaks: true,
  gfm: true,
  highlight: (code: string, lang: string) => {
    if (lang && hljs.getLanguage(lang)) {
      try {
        return hljs.highlight(code, { language: lang }).value
      } catch (err) {
        console.error('代码高亮失败:', err)
      }
    }
    return code
  }
})

const renderedContent = computed(() => {
  if (!props.content) return ''
  try {
    return marked(props.content)
  } catch (err) {
    console.error('Markdown渲染失败:', err)
    return props.content
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

.markdown-body :deep(p) {
  margin-top: 0;
  margin-bottom: 16px;
}

.markdown-body :deep(ul),
.markdown-body :deep(ol) {
  margin-top: 0;
  margin-bottom: 16px;
  padding-left: 2em;
}

.markdown-body :deep(li) {
  margin-top: 0.25em;
}

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

.markdown-body :deep(blockquote) {
  margin: 0 0 16px 0;
  padding: 0 1em;
  color: #6b7280;
  border-left: 0.25em solid #d1d5db;
}

.markdown-body :deep(a) {
  color: #58a6ff;
  text-decoration: none;
}

.markdown-body :deep(a:hover) {
  text-decoration: underline;
}

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

.markdown-body :deep(hr) {
  height: 0.25em;
  padding: 0;
  margin: 24px 0;
  background-color: #e5e7eb;
  border: 0;
}

.markdown-body :deep(img) {
  max-width: 100%;
  box-sizing: content-box;
}
</style>
