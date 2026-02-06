// Package model 用户模型使用统计实体模型
// @author <a href="https://codefather.cn">编程导航学习圈</a>
package model

import (
	"time"
)

type UserModelUsage struct {
	ID          string    `gorm:"primaryKey;type:varchar(36)" json:"id"`
	UserID      int64     `gorm:"column:userId;type:bigint;not null;uniqueIndex:uk_user_model" json:"userId"`
	ModelName   string    `gorm:"column:modelName;type:varchar(100);not null;uniqueIndex:uk_user_model;index:idx_model" json:"modelName"`
	TotalTokens int64     `gorm:"column:totalTokens;type:bigint;not null;default:0" json:"totalTokens"`
	TotalCost   float64   `gorm:"column:totalCost;type:decimal(12,6);not null;default:0" json:"totalCost"`
	CreateTime  time.Time `gorm:"column:createTime;type:datetime;not null;default:CURRENT_TIMESTAMP;autoCreateTime" json:"createTime"`
	UpdateTime  time.Time `gorm:"column:updateTime;type:datetime;not null;default:CURRENT_TIMESTAMP;autoUpdateTime" json:"updateTime"`
	IsDelete    int       `gorm:"column:isDelete;type:tinyint;not null;default:0" json:"-"`
}

func (UserModelUsage) TableName() string {
	return "user_model_usage"
}
