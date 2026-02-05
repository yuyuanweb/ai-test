// Package dto 评分相关请求DTO
// @author <a href="https://codefather.cn">编程导航学习圈</a>
package dto

type RatingAddRequest struct {
	ConversationID     string `json:"conversationId" binding:"required"`
	MessageIndex       *int   `json:"messageIndex"`
	RatingType         string `json:"ratingType" binding:"required"`
	WinnerModel        string `json:"winnerModel"`
	LoserModel         string `json:"loserModel"`
	WinnerVariantIndex *int   `json:"winnerVariantIndex"`
	LoserVariantIndex  *int   `json:"loserVariantIndex"`
}
