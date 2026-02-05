// Package model 评分实体
// @author <a href="https://codefather.cn">编程导航学习圈</a>
package model

import "time"

type Rating struct {
	ID                 string    `gorm:"column:id;type:varchar(36);primaryKey" json:"id"`
	ConversationID     string    `gorm:"column:conversationId;type:varchar(36);not null" json:"conversationId"`
	MessageIndex       int       `gorm:"column:messageIndex;type:int;not null" json:"messageIndex"`
	UserID             int64     `gorm:"column:userId;type:bigint;not null" json:"userId"`
	RatingType         string    `gorm:"column:ratingType;type:varchar(50);not null" json:"ratingType"`
	WinnerModel        string    `gorm:"column:winnerModel;type:varchar(100)" json:"winnerModel"`
	LoserModel         string    `gorm:"column:loserModel;type:varchar(100)" json:"loserModel"`
	WinnerVariantIndex *int      `gorm:"column:winnerVariantIndex;type:int" json:"winnerVariantIndex"`
	LoserVariantIndex  *int      `gorm:"column:loserVariantIndex;type:int" json:"loserVariantIndex"`
	CreateTime         time.Time `gorm:"column:createTime;type:datetime" json:"createTime"`
	UpdateTime         time.Time `gorm:"column:updateTime;type:datetime" json:"updateTime"`
	IsDelete           int       `gorm:"column:isDelete;type:tinyint;default:0" json:"isDelete"`
}

func (Rating) TableName() string {
	return "rating"
}
