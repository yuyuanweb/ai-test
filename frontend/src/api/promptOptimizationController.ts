import request from '@/request'

/**
 * 提示词优化请求
 */
export interface PromptOptimizationRequest {
  originalPrompt: string
  aiResponse?: string
  evaluationModel?: string
}

/**
 * 提示词优化结果
 */
export interface PromptOptimizationVO {
  issues: string[]
  optimizedPrompt: string
  improvements: string[]
}

/**
 * 分析并优化提示词
 */
export async function optimizePrompt(params: PromptOptimizationRequest) {
  return request<API.BaseResponse<PromptOptimizationVO>>({
    url: '/prompt/optimization/analyze',
    method: 'POST',
    data: params
  })
}
