// @ts-ignore
/* eslint-disable */
import request from '@/request'

/** 图片上传（多模态输入） POST /upload/image */
export async function uploadImage(
  body: FormData,
  options?: { [key: string]: any }
) {
  return request<API.BaseResponseUploadImageVO>('/upload/image', {
    method: 'POST',
    data: body,
    ...(options || {}),
  })
}

