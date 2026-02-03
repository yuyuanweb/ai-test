// @ts-ignore
/* eslint-disable */
import request from '@/request'

/** 获取成本统计 GET /statistics/cost */
export async function getCostStatistics(
  params?: { days?: number },
  options?: { [key: string]: any }
) {
  return request<API.BaseResponseCostStatisticsVO>('/statistics/cost', {
    method: 'GET',
    params: {
      ...params,
    },
    ...(options || {}),
  })
}

/** 获取使用统计 GET /statistics/usage */
export async function getUsageStatistics(
  params?: { days?: number },
  options?: { [key: string]: any }
) {
  return request<API.BaseResponseUsageStatisticsVO>('/statistics/usage', {
    method: 'GET',
    params: {
      ...params,
    },
    ...(options || {}),
  })
}

/** 获取性能统计 GET /statistics/performance */
export async function getPerformanceStatistics(options?: { [key: string]: any }) {
  return request<API.BaseResponsePerformanceStatisticsVO>('/statistics/performance', {
    method: 'GET',
    ...(options || {}),
  })
}

/** 获取实时成本监控 GET /statistics/realtime */
export async function getRealtimeCost(options?: { [key: string]: any }) {
  return request<API.BaseResponseRealtimeCostVO>('/statistics/realtime', {
    method: 'GET',
    ...(options || {}),
  })
}
