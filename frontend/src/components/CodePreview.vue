<template>
  <div class="code-preview-container">
    <!-- HTML预览：默认显示预览，可切换到代码 -->
    <template v-if="isHtml">
      <!-- 预览视图 -->
      <div v-if="!showCode" class="preview-view">
        <div class="preview-header">
          <span class="preview-title">预览效果</span>
          <div class="header-actions">
            <button class="action-btn" title="查看代码" @click="showCode = true">
              <CodeOutlined /> 代码
            </button>
            <button class="action-btn" title="复制代码" @click="copyCode">
              <CopyOutlined />
            </button>
            <button class="action-btn" title="全屏" @click="expandPreview">
              <ExpandOutlined />
            </button>
          </div>
        </div>
        <iframe
          ref="previewFrame"
          :srcdoc="sanitizedHtml"
          sandbox="allow-scripts"
          class="preview-iframe"
          title="代码预览"
        ></iframe>
      </div>

      <!-- 代码视图 -->
      <div v-else class="code-view">
        <div class="code-header">
          <div class="header-left">
            <span class="language-tag">{{ displayLanguage }}</span>
          </div>
          <div class="action-buttons">
            <button class="action-btn" title="返回预览" @click="showCode = false">
              <EyeOutlined /> 预览
            </button>
            <button class="action-btn" title="复制代码" @click="copyCode">
              <CopyOutlined />
            </button>
            <button class="action-btn" title="下载" @click="downloadCode">
              <DownloadOutlined />
            </button>
          </div>
        </div>
        <div ref="editorContainer" class="editor-container"></div>
      </div>
    </template>

    <!-- 非HTML代码：只显示代码 -->
    <template v-else>
      <div class="code-view">
        <div class="code-header">
          <div class="header-left">
            <span class="language-tag">{{ displayLanguage }}</span>
          </div>
          <div class="action-buttons">
            <button class="action-btn" title="复制代码" @click="copyCode">
              <CopyOutlined />
            </button>
            <button class="action-btn" title="下载" @click="downloadCode">
              <DownloadOutlined />
            </button>
          </div>
        </div>
        <div ref="editorContainer" class="editor-container"></div>
      </div>
    </template>

    <!-- 全屏预览模态框 -->
    <a-modal
      v-model:open="fullScreenPreview"
      title="全屏预览"
      width="95%"
      :footer="null"
      @cancel="fullScreenPreview = false"
    >
      <iframe
        v-if="fullScreenPreview"
        :srcdoc="sanitizedHtml"
        sandbox="allow-scripts"
        class="fullscreen-iframe"
        title="全屏代码预览"
      ></iframe>
    </a-modal>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted, onUnmounted, watch } from 'vue'
import { message } from 'ant-design-vue'
import { 
  CopyOutlined, 
  DownloadOutlined, 
  EyeOutlined, 
  EyeInvisibleOutlined, 
  ExpandOutlined,
  CodeOutlined
} from '@ant-design/icons-vue'
import * as monaco from 'monaco-editor'

interface CodeBlock {
  language: string
  code: string
  sanitizedHtml?: string
}

interface Props {
  codeBlock: CodeBlock
  isCodeMode?: boolean
}

const props = withDefaults(defineProps<Props>(), {
  isCodeMode: false
})

const editorContainer = ref<HTMLElement>()
const previewFrame = ref<HTMLIFrameElement>()
const showCode = ref(!props.isCodeMode)
const fullScreenPreview = ref(false)
let editor: monaco.editor.IStandaloneCodeEditor | null = null

const isHtml = ref(props.codeBlock.language.toLowerCase() === 'html')
const displayLanguage = ref(props.codeBlock.language.toUpperCase())
// 使用原始code用于预览（后端已不再清理HTML，直接返回完整代码）
// iframe的sandbox属性提供安全隔离
const sanitizedHtml = ref(props.codeBlock.code || props.codeBlock.sanitizedHtml || '')

const getMonacoLanguage = (lang: string): string => {
  const languageMap: Record<string, string> = {
    'js': 'javascript',
    'ts': 'typescript',
    'py': 'python',
    'sh': 'shell',
    'bash': 'shell',
    'yml': 'yaml',
    'dockerfile': 'dockerfile',
    'md': 'markdown'
  }
  return languageMap[lang.toLowerCase()] || lang.toLowerCase()
}

const initEditor = () => {
  if (!editorContainer.value) return

  if (editor) {
    editor.dispose()
  }

  editor = monaco.editor.create(editorContainer.value, {
    value: props.codeBlock.code,
    language: getMonacoLanguage(props.codeBlock.language),
    theme: 'vs',
    readOnly: true,
    minimap: { enabled: false },
    scrollBeyondLastLine: false,
    automaticLayout: true,
    fontSize: 13,
    lineNumbers: 'on',
    roundedSelection: false,
    fontFamily: "'SF Mono', 'Consolas', 'Monaco', 'Courier New', monospace",
    renderWhitespace: 'selection',
    scrollbar: {
      verticalScrollbarSize: 8,
      horizontalScrollbarSize: 8,
      alwaysConsumeMouseWheel: false
    },
    overviewRulerLanes: 0
  })
  
  const domNode = editor.getDomNode()
  if (domNode) {
    domNode.addEventListener('wheel', (e: WheelEvent) => {
      const scrollTop = editor.getScrollTop()
      const scrollHeight = editor.getScrollHeight()
      const clientHeight = domNode.clientHeight
      
      if ((scrollTop === 0 && e.deltaY < 0) || 
          (scrollTop + clientHeight >= scrollHeight - 1 && e.deltaY > 0)) {
        return
      }
      
      e.stopPropagation()
    }, { passive: true })
  }
}

const copyCode = async () => {
  try {
    await navigator.clipboard.writeText(props.codeBlock.code)
    message.success('代码已复制')
  } catch (error) {
    message.error('复制失败')
  }
}

const downloadCode = () => {
  const fileExtension = getFileExtension(props.codeBlock.language)
  const fileName = `code.${fileExtension}`
  const blob = new Blob([props.codeBlock.code], { type: 'text/plain' })
  const url = URL.createObjectURL(blob)
  const a = document.createElement('a')
  a.href = url
  a.download = fileName
  document.body.appendChild(a)
  a.click()
  document.body.removeChild(a)
  URL.revokeObjectURL(url)
  message.success(`已下载 ${fileName}`)
}

const expandPreview = () => {
  fullScreenPreview.value = true
}

const getFileExtension = (language: string): string => {
  const extensionMap: Record<string, string> = {
    html: 'html',
    css: 'css',
    javascript: 'js',
    typescript: 'ts',
    python: 'py',
    java: 'java',
    cpp: 'cpp',
    c: 'c',
    go: 'go',
    rust: 'rs',
    json: 'json',
    xml: 'xml',
    yaml: 'yaml',
    markdown: 'md',
    sql: 'sql',
    shell: 'sh',
    bash: 'sh'
  }
  return extensionMap[language.toLowerCase()] || 'txt'
}

watch(() => props.codeBlock, () => {
  if (editor && showCode.value) {
    editor.setValue(props.codeBlock.code)
  }
  isHtml.value = props.codeBlock.language.toLowerCase() === 'html'
  displayLanguage.value = props.codeBlock.language.toUpperCase()
  // 优先使用原始code
  sanitizedHtml.value = props.codeBlock.code || props.codeBlock.sanitizedHtml || ''
}, { deep: true })

watch(showCode, (newVal) => {
  if (newVal) {
    setTimeout(() => {
      initEditor()
    }, 100)
  }
})

onMounted(() => {
  if (!isHtml.value || showCode.value) {
    initEditor()
  }
})

onUnmounted(() => {
  if (editor) {
    editor.dispose()
    editor = null
  }
})
</script>

<style scoped>
.code-preview-container {
  margin: 16px 0;
  border: 1px solid #e5e7eb;
  border-radius: 6px;
  overflow: hidden;
  background: #ffffff;
}

/* 预览视图 */
.preview-view {
  display: flex;
  flex-direction: column;
}

.preview-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 10px 14px;
  background: #f8f9fa;
  border-bottom: 1px solid #e5e7eb;
}

.preview-title {
  font-size: 13px;
  font-weight: 500;
  color: #374151;
}

.header-actions {
  display: flex;
  gap: 6px;
}

.preview-iframe {
  width: 100%;
  height: 500px;
  border: none;
  background: #ffffff;
  display: block;
}

/* 代码视图 */
.code-view {
  display: flex;
  flex-direction: column;
}

.code-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 8px 12px;
  background: #f8f9fa;
  border-bottom: 1px solid #e5e7eb;
}

.header-left {
  display: flex;
  align-items: center;
  gap: 6px;
}

.language-tag {
  font-size: 12px;
  font-weight: 400;
  color: #6b7280;
}

.action-buttons {
  display: flex;
  gap: 4px;
}

.action-btn {
  padding: 4px 10px;
  border: none;
  background: transparent;
  color: #6b7280;
  cursor: pointer;
  border-radius: 4px;
  display: flex;
  align-items: center;
  gap: 4px;
  transition: all 0.15s;
  font-size: 12px;
}

.action-btn:hover {
  background: #e5e7eb;
  color: #374151;
}

.editor-container {
  height: 400px;
}

.fullscreen-iframe {
  width: 100%;
  height: 75vh;
  border: none;
  background: #ffffff;
}
</style>
