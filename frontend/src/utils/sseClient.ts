/**
 * SSE客户端工具类
 * 用于处理POST请求的流式响应；支持 business-error 事件（限流等业务错误）
 *
 * @author <a href="https://codefather.cn">编程导航学习圈</a>
 */

export interface SSEOptions {
  onMessage: (data: any) => void
  onError?: (error: Error) => void
  onComplete?: () => void
  /** 限流等业务错误（后端 event: business-error），收到后应停止 loading、关闭连接 */
  onBusinessError?: (data: { error: boolean; code: number; message: string }) => void
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
        Accept: 'text/event-stream',
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

            const lines = part.split('\n')
            let eventType = ''
            for (const line of lines) {
              const trimmed = line.trim()
              if (trimmed.startsWith('event:')) {
                eventType = trimmed.slice(6).trim()
                continue
              }
              if (trimmed.startsWith('data:')) {
                const jsonStr = trimmed.slice(5).trim()
                if (!jsonStr) continue
                try {
                  const data = JSON.parse(jsonStr)
                  if (eventType === 'business-error') {
                    console.warn('SSE business-error:', data)
                    options.onBusinessError?.(data as { error: boolean; code: number; message: string })
                    reader.cancel()
                    return
                  }
                  const timestamp = new Date().toISOString().split('T')[1].substring(0, 12)
                  console.log(`[${timestamp}] ✅ 收到SSE:`, data.modelName, 'content长度:', data.fullContent?.length || 0, 'done:', data.done)
                  options.onMessage(data)
                } catch (e) {
                  if (eventType !== 'business-error') console.warn('⚠️ JSON解析失败，等待更多数据')
                }
                eventType = ''
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

