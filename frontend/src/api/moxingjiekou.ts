// @ts-ignore
/* eslint-disable */
import request from '@/request'

/** 获取所有模型列表 GET /model/all */
export async function getAllModels(options?: { [key: string]: any }) {
  return request<API.BaseResponseListModelVO>('/model/all', {
    method: 'GET',
    ...(options || {}),
  })
}

/** 分页查询模型列表 POST /model/list */
export async function listModels(body: API.ModelQueryRequest, options?: { [key: string]: any }) {
  return request<API.BaseResponsePageModelVO>('/model/list', {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json',
    },
    data: body,
    ...(options || {}),
  })
}
