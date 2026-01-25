import request from '../request'

export interface ReportSummaryVO {
  totalCost?: number
  avgResponseTimeMs?: number
  totalTokens?: number
  totalResults?: number
  modelCount?: number
}

export interface ModelStatisticsVO {
  modelName: string
  testCount: number
  avgResponseTimeMs?: number
  avgInputTokens?: number
  avgOutputTokens?: number
  totalTokens?: number
  totalCost?: number
  avgCost?: number
  avgUserRating?: number
  avgAiScore?: number
}

export interface RadarSeriesVO {
  modelName: string
  values: number[]
}

export interface RadarChartDataVO {
  dimensions: string[]
  series: RadarSeriesVO[]
}

export interface BarSeriesVO {
  name: string
  data: number[]
  unit?: string
}

export interface BarChartDataVO {
  categories: string[]
  series: BarSeriesVO[]
}

export interface TestResultVO {
  id: string
  taskId: string
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
}

export interface ReportVO {
  taskId: string
  taskName?: string
  summary: ReportSummaryVO
  modelStatistics: ModelStatisticsVO[]
  radarChart: RadarChartDataVO
  barChart: BarChartDataVO
  testResults: TestResultVO[]
}

export function generateReport(taskId: string) {
  return request.get<ReportVO>('/report/generate', { params: { taskId } })
}
