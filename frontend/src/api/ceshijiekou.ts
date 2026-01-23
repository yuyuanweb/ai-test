// @ts-ignore
/* eslint-disable */
import request from '@/request'

/** 测试多模型并行流式调用 POST /test/ai/multi-stream */
export async function testMultiStream(
  // 叠加生成的Param类型 (非body参数swagger默认没有生成对象)
  params: API.testMultiStreamParams,
  options?: { [key: string]: any }
) {
  return request<API.ServerSentEventStreamChunkVO[]>('/test/ai/multi-stream', {
    method: 'POST',
    params: {
      ...params,
    },
    ...(options || {}),
  })
}

/** 测试AI调用（非流式） POST /test/ai/simple */
export async function testAiSimple(
  // 叠加生成的Param类型 (非body参数swagger默认没有生成对象)
  params: API.testAiSimpleParams,
  options?: { [key: string]: any }
) {
  return request<API.BaseResponseMapStringObject>('/test/ai/simple', {
    method: 'POST',
    params: {
      ...params,
    },
    ...(options || {}),
  })
}

/** 测试AI流式调用 POST /test/ai/stream */
export async function testAiStream(
  // 叠加生成的Param类型 (非body参数swagger默认没有生成对象)
  params: API.testAiStreamParams,
  options?: { [key: string]: any }
) {
  return request<API.ServerSentEventStreamChunkVO[]>('/test/ai/stream', {
    method: 'POST',
    params: {
      // model has a default value: openai/gpt-4o-mini
      model: 'openai/gpt-4o-mini',
      ...params,
    },
    ...(options || {}),
  })
}

/** 测试基本接口 GET /test/hello */
export async function hello(options?: { [key: string]: any }) {
  return request<API.BaseResponseString>('/test/hello', {
    method: 'GET',
    ...(options || {}),
  })
}
