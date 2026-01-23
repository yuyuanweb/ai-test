// @ts-ignore
/* eslint-disable */
import request from '@/request'

/** 代码模式(流式) POST /conversation/code-mode/stream */
export async function codeModeStream(body: API.CodeModeRequest, options?: { [key: string]: any }) {
  return request<API.ServerSentEventStreamChunkVO[]>('/conversation/code-mode/stream', {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json',
    },
    data: body,
    ...(options || {}),
  })
}

/** 创建对话 POST /conversation/create */
export async function createConversation(
  body: API.CreateConversationRequest,
  options?: { [key: string]: any }
) {
  return request<API.BaseResponseString>('/conversation/create', {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json',
    },
    data: body,
    ...(options || {}),
  })
}

/** 删除对话 POST /conversation/delete */
export async function deleteConversation(
  body: API.DeleteRequest,
  options?: { [key: string]: any }
) {
  return request<API.BaseResponseBoolean>('/conversation/delete', {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json',
    },
    data: body,
    ...(options || {}),
  })
}

/** 获取对话详情 GET /conversation/get */
export async function getConversation(
  // 叠加生成的Param类型 (非body参数swagger默认没有生成对象)
  params: API.getConversationParams,
  options?: { [key: string]: any }
) {
  return request<API.BaseResponseConversation>('/conversation/get', {
    method: 'GET',
    params: {
      ...params,
    },
    ...(options || {}),
  })
}

/** 获取对话列表 GET /conversation/list */
export async function listConversations(
  // 叠加生成的Param类型 (非body参数swagger默认没有生成对象)
  params: API.listConversationsParams,
  options?: { [key: string]: any }
) {
  return request<API.BaseResponsePageConversation>('/conversation/list', {
    method: 'GET',
    params: {
      // pageNum has a default value: 1
      pageNum: '1',
      // pageSize has a default value: 10
      pageSize: '10',
      ...params,
    },
    ...(options || {}),
  })
}

/** 获取对话的所有消息 GET /conversation/messages */
export async function getConversationMessages(
  // 叠加生成的Param类型 (非body参数swagger默认没有生成对象)
  params: API.getConversationMessagesParams,
  options?: { [key: string]: any }
) {
  return request<API.BaseResponseListConversationMessage>('/conversation/messages', {
    method: 'GET',
    params: {
      ...params,
    },
    ...(options || {}),
  })
}

/** Prompt Lab单模型多提示词对比(流式) POST /conversation/prompt-lab/stream */
export async function promptLabStream(
  body: API.PromptLabRequest,
  options?: { [key: string]: any }
) {
  return request<API.ServerSentEventStreamChunkVO[]>('/conversation/prompt-lab/stream', {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json',
    },
    data: body,
    ...(options || {}),
  })
}

/** Side-by-Side并排对比(流式) POST /conversation/side-by-side/stream */
export async function sideBySideStream(
  body: API.SideBySideRequest,
  options?: { [key: string]: any }
) {
  return request<API.ServerSentEventStreamChunkVO[]>('/conversation/side-by-side/stream', {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json',
    },
    data: body,
    ...(options || {}),
  })
}
