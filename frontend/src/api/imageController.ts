// @ts-ignore
/* eslint-disable */
import request from '@/request'

/** 生成图片（文本 / 图生图） POST /image/generate */
export async function generateImage(
  body: API.GenerateImageRequest,
  options?: { [key: string]: any }
) {
  return request<API.BaseResponseListGeneratedImageVO>('/image/generate', {
    method: 'POST',
    data: body,
    ...(options || {}),
  })
}

/** 流式生成图片（输出思考过程） POST /image/generate/stream */
export function generateImageStream(
  body: API.GenerateImageRequest,
  onChunk: (chunk: API.ImageStreamChunkVO) => void,
  onError?: (error: any) => void,
  onComplete?: () => void
) {
  const controller = new AbortController()
  
  fetch('/api/image/generate/stream', {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json',
    },
    body: JSON.stringify(body),
    signal: controller.signal,
    credentials: 'include'
  })
    .then(async (response) => {
      if (!response.ok) {
        // 尝试解析错误响应体
        let errorMessage = `HTTP error! status: ${response.status}`
        try {
          const errorBody = await response.json()
          if (errorBody.message) {
            errorMessage = errorBody.message
          }
        } catch (e) {
          // 无法解析 JSON，使用默认错误信息
        }
        throw new Error(errorMessage)
      }
      
      const reader = response.body?.getReader()
      if (!reader) {
        throw new Error('No reader available')
      }
      
      const decoder = new TextDecoder()
      let buffer = ''
      
      while (true) {
        const { done, value } = await reader.read()
        if (done) break
        
        buffer += decoder.decode(value, { stream: true })
        const lines = buffer.split('\n')
        buffer = lines.pop() || ''
        
        for (const line of lines) {
          if (line.startsWith('data:')) {
            const data = line.slice(5).trim()
            if (data && data !== '[DONE]') {
              try {
                const chunk = JSON.parse(data) as API.ImageStreamChunkVO
                // 如果是错误类型的chunk，也调用onError
                if (chunk.type === 'error' && chunk.error) {
                  onChunk(chunk)
                } else {
                  onChunk(chunk)
                }
              } catch (e) {
                console.warn('Failed to parse SSE data:', data)
              }
            }
          }
        }
      }
      
      onComplete?.()
    })
    .catch((error) => {
      if (error.name !== 'AbortError') {
        onError?.(error)
      }
    })
  
  return {
    abort: () => controller.abort()
  }
}
