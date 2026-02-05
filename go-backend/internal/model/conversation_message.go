// Package model 对话消息实体
// @author <a href="https://codefather.cn">编程导航学习圈</a>
package model

import "time"

type ConversationMessage struct {
	ID             string    `gorm:"column:id;type:varchar(36);primaryKey" json:"id"`
	ConversationID string    `gorm:"column:conversationId;type:varchar(36);not null" json:"conversationId"`
	UserID         int64     `gorm:"column:userId;type:bigint;not null" json:"userId"`
	MessageIndex   int       `gorm:"column:messageIndex;type:int;not null" json:"messageIndex"`
	Role           string    `gorm:"column:role;type:varchar(20);not null" json:"role"`
	ModelName      string    `gorm:"column:modelName;type:varchar(100)" json:"modelName"`
	VariantIndex   *int      `gorm:"column:variantIndex;type:int" json:"variantIndex"`
	Content        string    `gorm:"column:content;type:text;not null" json:"content"`
	Images         *string   `gorm:"column:images;type:json" json:"images"`
	ToolsUsed      *string   `gorm:"column:toolsUsed;type:json" json:"toolsUsed"`
	ResponseTimeMs int       `gorm:"column:responseTimeMs;type:int" json:"responseTimeMs"`
	InputTokens    int       `gorm:"column:inputTokens;type:int" json:"inputTokens"`
	OutputTokens   int       `gorm:"column:outputTokens;type:int" json:"outputTokens"`
	Cost           float64   `gorm:"column:cost;type:decimal(10,6)" json:"cost"`
	Reasoning      string    `gorm:"column:reasoning;type:text" json:"reasoning"`
	CodeBlocks     *string   `gorm:"column:codeBlocks;type:text" json:"codeBlocks"`
	CreateTime     time.Time `gorm:"column:createTime;type:datetime" json:"createTime"`
	UpdateTime     time.Time `gorm:"column:updateTime;type:datetime" json:"updateTime"`
	IsDelete       int       `gorm:"column:isDelete;type:tinyint;default:0" json:"isDelete"`
}

func (ConversationMessage) TableName() string {
	return "conversation_message"
}
