import request from '../request'

export interface TestTask {
  id: string
  userId: number
  name?: string
  sceneId: string
  models: string
  config?: string
  status: string
  totalSubtasks: number
  completedSubtasks: number
  startedAt?: string
  completedAt?: string
  createTime?: string
  updateTime?: string
}

export interface TestResult {
  id: string
  taskId: string
  userId: number
  sceneId: string
  promptId: string
  modelName: string
  inputPrompt: string
  outputText: string
  reasoning?: string
  responseTimeMs?: number
  inputTokens?: number
  outputTokens?: number
  cost?: number
  userRating?: number
  aiScore?: string
  createTime?: string
  updateTime?: string
}

export interface CreateBatchTestRequest {
  name?: string
  sceneId: string
  models: string[]
  temperature?: number
  topP?: number
  maxTokens?: number
  topK?: number
  frequencyPenalty?: number
  presencePenalty?: number
}

export interface TaskProgressVO {
  taskId: string
  percentage: number
  completedSubtasks: number
  totalSubtasks: number
  currentModel?: string
  currentPrompt?: string
  status: string
  timestamp: number
}

export function createBatchTestTask(data: CreateBatchTestRequest) {
  return request.post<string>('/batch-test/create', data)
}

export function getTask(taskId: string) {
  return request.get<TestTask>('/batch-test/task/get', { params: { taskId } })
}

export interface TaskQueryRequest {
  pageNum?: number
  pageSize?: number
  status?: string
  keyword?: string
  category?: string
  startTime?: string
  endTime?: string
}

export function listTasks(data: TaskQueryRequest) {
  return request.post<{
    records: TestTask[]
    total: number
    totalRow: number
    pageNum: number
    pageSize: number
  }>('/batch-test/task/list/page', data)
}

export function deleteTask(id: string) {
  return request.post<boolean>('/batch-test/task/delete', { id })
}

export function getTaskResults(taskId: string) {
  return request.get<TestResult[]>('/batch-test/result/list', { params: { taskId } })
}

export interface UpdateTestResultRatingRequest {
  resultId: string
  userRating?: number
}

export function updateTestResultRating(data: UpdateTestResultRatingRequest) {
  return request.post<boolean>('/batch-test/result/rating', data)
}
