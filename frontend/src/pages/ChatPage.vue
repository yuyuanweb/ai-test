<template>
  <div class="chat-test-page">
    <a-card title="Chat 功能测试" :bordered="false">
      <a-form layout="vertical">
        <a-form-item label="选择模型">
          <a-select v-model:value="formData.model" placeholder="请选择模型" style="width: 100%">
            <a-select-option value="deepseek/deepseek-chat">DeepSeek Chat</a-select-option>
          </a-select>
        </a-form-item>

        <a-form-item label="输入消息">
          <a-textarea
            v-model:value="formData.message"
            placeholder="请输入您的消息..."
            :rows="4"
            @keydown.ctrl.enter="sendMessage"
          />
        </a-form-item>

        <a-form-item>
          <a-space>
            <a-button
              type="primary"
              :loading="isStreaming"
              @click="sendMessage"
              :disabled="!formData.model || !formData.message"
            >
              {{ isStreaming ? '生成中...' : '发送消息 (Ctrl+Enter)' }}
            </a-button>
            <a-button @click="clearChat" :disabled="isStreaming">清空对话</a-button>
          </a-space>
        </a-form-item>
      </a-form>

      <a-divider>对话记录</a-divider>

      <div class="chat-messages" ref="messagesContainer">
        <div
          v-for="(msg, index) in messages"
          :key="index"
          class="message-item"
          :class="msg.role"
        >
          <div class="message-header">
            <span class="role-tag">{{ msg.role === 'user' ? '👤 用户' : '🤖 AI' }}</span>
            <span class="model-tag" v-if="msg.model">{{ msg.model }}</span>
          </div>
          <div class="message-content">
            <div v-if="msg.role === 'assistant'" v-html="renderMarkdown(msg.content)"></div>
            <div v-else>{{ msg.content }}</div>
          </div>
          <div class="message-meta" v-if="msg.tokens || msg.cost">
            <span v-if="msg.tokens">Tokens: {{ msg.tokens }}</span>
            <span v-if="msg.cost">Cost: ${{ msg.cost.toFixed(6) }}</span>
            <span v-if="msg.time">Time: {{ msg.time }}ms</span>
          </div>
        </div>

        <div v-if="isStreaming" class="streaming-indicator">
          <a-spin size="small" /> 生成中...
        </div>

        <a-empty v-if="messages.length === 0 && !isStreaming" description="暂无对话记录" />
      </div>
    </a-card>
  </div>
</template>

<script setup lang="ts">
import { ref, nextTick } from 'vue';
import { message as antMessage } from 'ant-design-vue';
import { marked } from 'marked';
import { API_BASE_URL } from '@/config/env';

interface Message {
  role: 'user' | 'assistant';
  content: string;
  model?: string;
  tokens?: number;
  cost?: number;
  time?: number;
}

const formData = ref({
  model: 'deepseek/deepseek-chat',
  message: ''
});

const messages = ref<Message[]>([]);
const isStreaming = ref(false);
const messagesContainer = ref<HTMLElement>();
const currentConversationId = ref<string | null>(null);

// 渲染 Markdown
const renderMarkdown = (content: string) => {
  return marked(content);
};

// 滚动到底部
const scrollToBottom = () => {
  nextTick(() => {
    if (messagesContainer.value) {
      messagesContainer.value.scrollTop = messagesContainer.value.scrollHeight;
    }
  });
};

// 发送消息
const sendMessage = async () => {
  if (!formData.value.model || !formData.value.message || isStreaming.value) {
    return;
  }

  const userMessage = formData.value.message;
  const model = formData.value.model;

  // 添加用户消息
  messages.value.push({
    role: 'user',
    content: userMessage
  });

  // 清空输入框
  formData.value.message = '';

  // 滚动到底部
  scrollToBottom();

  isStreaming.value = true;

  // 创建 AI 消息占位符
  const aiMessage: Message = {
    role: 'assistant',
    content: '',
    model: model
  };
  messages.value.push(aiMessage);

  try {
    // 构建请求 URL
    const params = new URLSearchParams({
      conversationId: currentConversationId.value || '',
      model: model,
      message: userMessage
    });

    const startTime = Date.now();
    let buffer = '';

    // 调用 SSE 接口（使用 fetch + ReadableStream）
    const urlParams = new URLSearchParams({
      prompt: userMessage,
      model: model
    });
    const response = await fetch(`${API_BASE_URL}/test/ai/stream?${urlParams}`, {
      method: 'POST',
      credentials: 'include'
    });

    if (!response.ok) {
      throw new Error(`HTTP error! status: ${response.status}`);
    }

    const reader = response.body?.getReader();
    const decoder = new TextDecoder();

    if (!reader) {
      throw new Error('无法读取响应流');
    }

    while (true) {
      const { done, value } = await reader.read();

      if (done) {
        break;
      }

      buffer += decoder.decode(value, { stream: true });
      const lines = buffer.split('\n');
      
      // 保留最后一行（可能不完整）
      buffer = lines.pop() || '';

      for (const line of lines) {
        if (!line.trim()) continue;
        
        // 处理 SSE 格式：data: {...}
        if (line.startsWith('data:')) {
          const jsonStr = line.substring(5).trim();
          
          // 跳过心跳包
          if (jsonStr === '[DONE]' || !jsonStr) {
            continue;
          }

          try {
            const data = JSON.parse(jsonStr);
            console.log('✅ 收到 SSE 数据:', {
              content: data.content,
              fullContent: data.fullContent?.substring(0, 50) + '...',
              done: data.done
            });

            // 更新对话 ID
            if (data.conversationId && !currentConversationId.value) {
              currentConversationId.value = data.conversationId;
              console.log('📝 设置对话ID:', currentConversationId.value);
            }

            // 更新内容
            if (data.fullContent !== undefined && data.fullContent !== '') {
              const lastMsg = messages.value[messages.value.length - 1];
              if (lastMsg && lastMsg.role === 'assistant') {
                lastMsg.content = data.fullContent;
                console.log('💬 更新消息内容，长度:', data.fullContent.length);
              }
              scrollToBottom();
            }

            // 完成时更新统计信息
            if (data.done) {
              const endTime = Date.now();
              const lastMsg = messages.value[messages.value.length - 1];
              if (lastMsg && lastMsg.role === 'assistant') {
                lastMsg.tokens = (data.inputTokens || 0) + (data.outputTokens || 0);
                lastMsg.cost = data.cost || 0;
                lastMsg.time = endTime - startTime;
              }
              console.log('✔️ 对话完成');
            }
          } catch (e) {
            console.error('❌ 解析 SSE 数据失败:', jsonStr, e);
          }
        }
      }
    }

    antMessage.success('消息发送成功');
  } catch (error) {
    console.error('发送消息失败:', error);
    antMessage.error('发送消息失败: ' + (error as Error).message);
    // 移除失败的 AI 消息
    messages.value.pop();
  } finally {
    isStreaming.value = false;
  }
};

// 清空对话
const clearChat = () => {
  messages.value = [];
  currentConversationId.value = null;
  antMessage.success('对话已清空');
};
</script>

<style scoped>
.chat-test-page {
  padding: 24px;
  max-width: 1200px;
  margin: 0 auto;
}

.chat-messages {
  max-height: 600px;
  overflow-y: auto;
  padding: 16px;
  background: #fafafa;
  border-radius: 8px;
}

.message-item {
  margin-bottom: 16px;
  padding: 12px;
  border-radius: 8px;
  background: white;
  box-shadow: 0 1px 2px rgba(0, 0, 0, 0.05);
}

.message-item.user {
  border-left: 3px solid #1890ff;
}

.message-item.assistant {
  border-left: 3px solid #52c41a;
}

.message-header {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-bottom: 8px;
}

.role-tag {
  font-weight: 600;
  font-size: 14px;
}

.model-tag {
  font-size: 12px;
  color: #999;
  background: #f0f0f0;
  padding: 2px 8px;
  border-radius: 4px;
}

.message-content {
  line-height: 1.6;
  color: #333;
}

.message-content :deep(pre) {
  background: #f5f5f5;
  padding: 12px;
  border-radius: 4px;
  overflow-x: auto;
}

.message-content :deep(code) {
  font-family: 'Courier New', monospace;
  background: #f5f5f5;
  padding: 2px 6px;
  border-radius: 3px;
}

.message-meta {
  margin-top: 8px;
  font-size: 12px;
  color: #999;
  display: flex;
  gap: 12px;
}

.streaming-indicator {
  text-align: center;
  padding: 16px;
  color: #1890ff;
}
</style>

