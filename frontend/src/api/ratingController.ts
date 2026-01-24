import request from '@/request'

export interface RatingAddRequest {
  conversationId: string
  messageIndex: number
  ratingType: string
  winnerModel?: string
  loserModel?: string
  winnerVariantIndex?: number
  loserVariantIndex?: number
}

export interface RatingVO {
  id: string
  conversationId: string
  messageIndex: number
  userId: number
  ratingType: string
  winnerModel?: string
  loserModel?: string
  winnerVariantIndex?: number
  loserVariantIndex?: number
  createTime: string
}

export const addRating = (data: RatingAddRequest) => {
  return request.post('/rating/add', data)
}

export const getRating = (conversationId: string, messageIndex: number) => {
  return request.get('/rating/get', {
    params: { conversationId, messageIndex }
  })
}

export const getRatingsByConversationId = (conversationId: string) => {
  return request.get('/rating/list', {
    params: { conversationId }
  })
}

export const deleteRating = (conversationId: string, messageIndex: number) => {
  return request.delete('/rating/delete', {
    params: { conversationId, messageIndex }
  })
}
