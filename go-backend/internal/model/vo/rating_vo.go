// Package vo 评分视图对象
// @author <a href="https://codefather.cn">编程导航学习圈</a>
package vo

import "time"

type RatingVO struct {
	ID                 string    `json:"id"`
	ConversationID     string    `json:"conversationId"`
	MessageIndex       int       `json:"messageIndex"`
	UserID             int64     `json:"userId"`
	RatingType         string    `json:"ratingType"`
	WinnerModel        string    `json:"winnerModel"`
	LoserModel         string    `json:"loserModel"`
	WinnerVariantIndex *int      `json:"winnerVariantIndex"`
	LoserVariantIndex  *int      `json:"loserVariantIndex"`
	CreateTime         time.Time `json:"createTime"`
	UpdateTime         time.Time `json:"updateTime"`
}
