/**
 * SSE客户端工具类
 * 用于处理POST请求的流式响应
 *
 * @author <a href="https://codefather.cn">编程导航学习圈</a>
 */

export interface SSEOptions {
  onMessage: (data: any) => void
  onError?: (error: Error) => void
  onComplete?: () => void
}

/**
 * 创建POST SSE连接
 */
export async function createPostSSE(url: string, body: any, options: SSEOptions) {
  console.log('🔗 创建SSE连接:', url)
  
  try {
    const response = await fetch(url, {
      method: 'POST',
      headers: {
        'Content-Type': 'application/json',
      },
      credentials: 'include',
      body: JSON.stringify(body),
    })

    console.log('📡 响应状态:', response.status, response.statusText)

    if (!response.ok) {
      throw new Error(`HTTP error! status: ${response.status}`)
    }

    if (!response.body) {
      throw new Error('Response body is null')
    }

    const reader = response.body.getReader()
    const decoder = new TextDecoder()
    let buffer = ''

    const processStream = async () => {
      try {
        console.log('🚀 开始处理SSE流')
        
        while (true) {
          const { done, value } = await reader.read()

          if (done) {
            console.log('✅ 流读取完成')
            options.onComplete?.()
            break
          }

          // 解码并累积到buffer
          buffer += decoder.decode(value, { stream: true })

          // 按\n\n分割事件
          const parts = buffer.split('\n\n')
          // 保留最后一个未完成的部分
          buffer = parts.pop() || ''

          for (const part of parts) {
            if (!part.trim()) continue
            
            // 查找data:行
            const lines = part.split('\n')
            for (const line of lines) {
              const trimmed = line.trim()
              if (trimmed.startsWith('data:')) {
                const jsonStr = trimmed.substring(5).trim()
                if (jsonStr) {
                  try {
                    const data = JSON.parse(jsonStr)
                    const timestamp = new Date().toISOString().split('T')[1].substring(0, 12)
                    console.log(`[${timestamp}] ✅ 收到SSE:`, data.modelName, 'content长度:', data.fullContent?.length || 0, 'done:', data.done)
                    options.onMessage(data)
                  } catch (e) {
                    console.warn('⚠️ JSON解析失败，等待更多数据')
                  }
                }
              }
            }
          }
        }
      } catch (error: any) {
        console.error('❌ 流处理错误:', error)
        options.onError?.(error)
      }
    }

    processStream()

    return {
      close: () => {
        reader.cancel()
      },
    }
  } catch (error: any) {
    console.error('❌ SSE连接错误:', error)
    options.onError?.(error)
    throw error
  }
}

