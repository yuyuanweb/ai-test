// 对话控制器 - 导出duihuajiekou并添加类型定义
export * from './duihuajiekou'

/**
 * 流式响应数据块
 */
export interface StreamChunkVO {
  conversationId?: string
  modelName?: string
  variantIndex?: number
  content?: string
  fullContent?: string
  inputTokens?: number
  outputTokens?: number
  totalTokens?: number
  elapsedMs?: number
  responseTimeMs?: number
  cost?: number
  done?: boolean
  error?: string
  hasError?: boolean
  reasoning?: string
  hasReasoning?: boolean
  thinkingTime?: number
  messageIndex?: number
}
