// Package model 对话记录实体
// @author <a href="https://codefather.cn">编程导航学习圈</a>
package model

import "time"

type Conversation struct {
	ID                 string    `gorm:"column:id;type:varchar(36);primaryKey" json:"id"`
	UserID             int64     `gorm:"column:userId;type:bigint;not null" json:"userId"`
	Title              string    `gorm:"column:title;type:varchar(200)" json:"title"`
	ConversationType   string    `gorm:"column:conversationType;type:varchar(50)" json:"conversationType"`
	CodePreviewEnabled bool      `gorm:"column:codePreviewEnabled;type:tinyint" json:"codePreviewEnabled"`
	IsAnonymous        bool      `gorm:"column:isAnonymous;type:tinyint" json:"isAnonymous"`
	ModelMapping       *string   `gorm:"column:modelMapping;type:json" json:"modelMapping"`
	Models             string    `gorm:"column:models;type:json" json:"models"`
	TotalTokens        int       `gorm:"column:totalTokens;type:int" json:"totalTokens"`
	TotalCost          float64   `gorm:"column:totalCost;type:decimal(10,6)" json:"totalCost"`
	CreateTime         time.Time `gorm:"column:createTime;type:datetime" json:"createTime"`
	UpdateTime         time.Time `gorm:"column:updateTime;type:datetime" json:"updateTime"`
	IsDelete           int       `gorm:"column:isDelete;type:tinyint;default:0" json:"isDelete"`
}

func (Conversation) TableName() string {
	return "conversation"
}
