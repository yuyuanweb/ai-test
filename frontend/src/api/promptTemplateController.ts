import request from '@/request'

/**
 * 提示词模板
 */
export interface PromptTemplateVO {
  id?: string
  name?: string
  description?: string
  strategy?: string
  strategyName?: string
  content?: string
  variables?: string[]
  category?: string
  isPreset?: boolean
  usageCount?: number
  isActive?: boolean
  createTime?: string
}

/**
 * 创建模板请求
 */
export interface CreatePromptTemplateRequest {
  name: string
  description?: string
  strategy: string
  content: string
  variables?: string[]
  category?: string
}

/**
 * 更新模板请求
 */
export interface UpdatePromptTemplateRequest {
  id: string
  name?: string
  description?: string
  strategy?: string
  content?: string
  variables?: string[]
  category?: string
  isActive?: boolean
}

/**
 * 获取模板列表
 */
export async function listTemplates(params?: { strategy?: string }) {
  return request<API.BaseResponse<PromptTemplateVO[]>>({
    url: '/prompt/template/list',
    method: 'GET',
    params
  })
}

/**
 * 根据ID获取模板
 */
export async function getTemplate(templateId: string) {
  return request<API.BaseResponse<PromptTemplateVO>>({
    url: '/prompt/template/get',
    method: 'GET',
    params: { templateId }
  })
}

/**
 * 创建模板
 */
export async function createTemplate(data: CreatePromptTemplateRequest) {
  return request<API.BaseResponse<string>>({
    url: '/prompt/template/create',
    method: 'POST',
    data
  })
}

/**
 * 更新模板
 */
export async function updateTemplate(data: UpdatePromptTemplateRequest) {
  return request<API.BaseResponse<boolean>>({
    url: '/prompt/template/update',
    method: 'POST',
    data
  })
}

/**
 * 删除模板
 */
export async function deleteTemplate(templateId: string) {
  return request<API.BaseResponse<boolean>>({
    url: '/prompt/template/delete',
    method: 'POST',
    data: { id: templateId }
  })
}

/**
 * 增加使用次数
 */
export async function incrementUsage(templateId: string) {
  return request<API.BaseResponse<boolean>>({
    url: '/prompt/template/increment-usage',
    method: 'POST',
    params: { templateId }
  })
}
