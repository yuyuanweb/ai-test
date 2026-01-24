<template>
  <div class="code-generating-hint">
    <div class="generating-header">
      <div class="file-icon-wrapper">
        <FileTextOutlined class="file-icon" />
      </div>
      <div class="generating-info">
        <div class="file-name">
          <span>{{ fileName }}</span>
          <div class="loading-dots">
            <span></span><span></span><span></span>
          </div>
        </div>
        <div class="generating-status">正在生成代码...</div>
      </div>
    </div>
    <div class="progress-bar">
      <div class="progress-fill"></div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import { FileTextOutlined } from '@ant-design/icons-vue'

const props = defineProps<{
  language?: string
}>()

const fileName = computed(() => {
  const lang = props.language?.toLowerCase() || 'html'
  
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
})
</script>

<style scoped>
.code-generating-hint {
  margin: 12px 0;
  padding: 16px;
  background: linear-gradient(135deg, #f8f9ff 0%, #f3f4ff 100%);
  border: 1px solid #e0e7ff;
  border-radius: 12px;
  box-shadow: 0 2px 8px rgba(79, 70, 229, 0.08);
  transition: all 0.3s ease;
}

.code-generating-hint:hover {
  box-shadow: 0 4px 12px rgba(79, 70, 229, 0.12);
  transform: translateY(-1px);
}

.generating-header {
  display: flex;
  align-items: flex-start;
  gap: 12px;
}

.file-icon-wrapper {
  width: 40px;
  height: 40px;
  background: linear-gradient(135deg, #6366f1 0%, #4f46e5 100%);
  border-radius: 10px;
  display: flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
  animation: pulse 2s cubic-bezier(0.4, 0, 0.6, 1) infinite;
}

.file-icon {
  font-size: 20px;
  color: #ffffff;
}

.generating-info {
  flex: 1;
}

.file-name {
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: 15px;
  font-weight: 600;
  color: #4f46e5;
  font-family: 'SF Mono', 'Consolas', 'Monaco', 'Courier New', monospace;
  margin-bottom: 4px;
}

.loading-dots {
  display: flex;
  gap: 3px;
  align-items: center;
}

.loading-dots span {
  width: 5px;
  height: 5px;
  background: #6366f1;
  border-radius: 50%;
  animation: bounce 1.4s infinite ease-in-out both;
}

.loading-dots span:nth-child(1) {
  animation-delay: -0.32s;
}

.loading-dots span:nth-child(2) {
  animation-delay: -0.16s;
}

.generating-status {
  font-size: 13px;
  color: #6b7280;
  font-weight: 400;
}

.progress-bar {
  margin-top: 12px;
  height: 3px;
  background: #e0e7ff;
  border-radius: 2px;
  overflow: hidden;
  position: relative;
}

.progress-fill {
  height: 100%;
  background: linear-gradient(90deg, #6366f1 0%, #4f46e5 100%);
  border-radius: 2px;
  animation: progress 2s ease-in-out infinite;
}

@keyframes pulse {
  0%, 100% {
    transform: scale(1);
    opacity: 1;
  }
  50% {
    transform: scale(1.05);
    opacity: 0.9;
  }
}

@keyframes bounce {
  0%, 80%, 100% {
    transform: scale(0);
    opacity: 0.3;
  }
  40% {
    transform: scale(1);
    opacity: 1;
  }
}

@keyframes progress {
  0% {
    width: 0;
    transform: translateX(0);
  }
  50% {
    width: 70%;
  }
  100% {
    width: 100%;
    transform: translateX(0);
  }
}
</style>

