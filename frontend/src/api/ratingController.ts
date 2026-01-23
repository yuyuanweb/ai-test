// @ts-ignore
/* eslint-disable */
import request from '@/request'

/** 此处后端没有提供注释 POST /rating/add */
export async function addRating(body: API.RatingAddRequest, options?: { [key: string]: any }) {
  return request<API.BaseResponseBoolean>('/rating/add', {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json',
    },
    data: body,
    ...(options || {}),
  })
}

/** 此处后端没有提供注释 DELETE /rating/delete */
export async function deleteRating(
  // 叠加生成的Param类型 (非body参数swagger默认没有生成对象)
  params: API.deleteRatingParams,
  options?: { [key: string]: any }
) {
  return request<API.BaseResponseBoolean>('/rating/delete', {
    method: 'DELETE',
    params: {
      ...params,
    },
    ...(options || {}),
  })
}

/** 此处后端没有提供注释 GET /rating/get */
export async function getRating(
  // 叠加生成的Param类型 (非body参数swagger默认没有生成对象)
  params: API.getRatingParams,
  options?: { [key: string]: any }
) {
  return request<API.BaseResponseRatingVO>('/rating/get', {
    method: 'GET',
    params: {
      ...params,
    },
    ...(options || {}),
  })
}
