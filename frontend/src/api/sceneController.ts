import request from '../request'

export interface Scene {
  id: string
  userId?: number
  name: string
  description?: string
  category?: string
  isPreset?: number
  isActive?: number
  createTime?: string
  updateTime?: string
}

export interface ScenePrompt {
  id: string
  sceneId: string
  userId: number
  promptIndex: number
  title: string
  content: string
  difficulty?: string
  tags?: string
  expectedOutput?: string
  createTime?: string
  updateTime?: string
}

export interface CreateSceneRequest {
  name: string
  description?: string
  category?: string
}

export interface UpdateSceneRequest {
  id: string
  name?: string
  description?: string
  category?: string
  isActive?: number
}

export interface AddScenePromptRequest {
  sceneId: string
  title: string
  content: string
  difficulty?: string
  expectedOutput?: string
}

export interface UpdateScenePromptRequest {
  id: string
  title?: string
  content?: string
  difficulty?: string
  expectedOutput?: string
}

export function createScene(data: CreateSceneRequest) {
  return request.post<string>('/scene/create', data)
}

export function updateScene(data: UpdateSceneRequest) {
  return request.post<boolean>('/scene/update', data)
}

export function deleteScene(id: string) {
  return request.post<boolean>('/scene/delete', { id })
}

export function getScene(id: string) {
  return request.get<Scene>('/scene/get', { params: { id } })
}

export function listScenes(params: {
  pageNum?: number
  pageSize?: number
  category?: string
  isPreset?: boolean
}) {
  return request.get<{
    records: Scene[]
    total: number
    totalRow: number
    pageNum: number
    pageSize: number
  }>('/scene/list/page', { params })
}

export function getScenePrompts(sceneId: string) {
  return request.get<ScenePrompt[]>('/scene/prompts', { params: { sceneId } })
}

export function addScenePrompt(data: AddScenePromptRequest) {
  return request.post<string>('/scene/prompt/add', data)
}

export function updateScenePrompt(data: UpdateScenePromptRequest) {
  return request.post<boolean>('/scene/prompt/update', data)
}

export function deleteScenePrompt(id: string) {
  return request.post<boolean>('/scene/prompt/delete', { id })
}
