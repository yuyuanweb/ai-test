// Package model 批量测试结果实体模型
// @author <a href="https://codefather.cn">编程导航学习圈</a>
package model

import (
	"time"
)

type TestResult struct {
	ID             string    `gorm:"primaryKey;type:varchar(36)" json:"id"`
	TaskID         string    `gorm:"column:taskId;type:varchar(36);not null;index:idx_task;index:idx_task_create" json:"taskId"`
	UserID         int64     `gorm:"column:userId;type:bigint;not null;index:idx_user" json:"userId"`
	SceneID        string    `gorm:"column:sceneId;type:varchar(36);not null;index:idx_scene" json:"sceneId"`
	PromptID       string    `gorm:"column:promptId;type:varchar(36);not null;index:idx_prompt" json:"promptId"`
	ModelName      string    `gorm:"column:modelName;type:varchar(100);not null;index:idx_model" json:"modelName"`
	InputPrompt    string    `gorm:"column:inputPrompt;type:text;not null" json:"inputPrompt"`
	OutputText     string    `gorm:"column:outputText;type:text;not null" json:"outputText"`
	Reasoning      string    `gorm:"column:reasoning;type:text" json:"reasoning"`
	ResponseTimeMs int       `gorm:"column:responseTimeMs;type:int" json:"responseTimeMs"`
	InputTokens    int       `gorm:"column:inputTokens;type:int" json:"inputTokens"`
	OutputTokens   int       `gorm:"column:outputTokens;type:int" json:"outputTokens"`
	Cost           float64   `gorm:"column:cost;type:decimal(10,6)" json:"cost"`
	UserRating     *int      `gorm:"column:userRating;type:int" json:"userRating"`
	AIScore        string    `gorm:"column:aiScore;type:json" json:"aiScore"`
	CreateTime     time.Time `gorm:"column:createTime;type:datetime;not null;default:CURRENT_TIMESTAMP;autoCreateTime;index:idx_task_create" json:"createTime"`
	UpdateTime     time.Time `gorm:"column:updateTime;type:datetime;not null;default:CURRENT_TIMESTAMP;autoUpdateTime" json:"updateTime"`
	IsDelete       int       `gorm:"column:isDelete;type:tinyint;not null;default:0" json:"-"`
}

func (TestResult) TableName() string {
	return "test_result"
}
