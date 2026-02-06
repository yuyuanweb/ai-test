// Package model 场景提示词实体模型
// @author <a href="https://codefather.cn">编程导航学习圈</a>
package model

import (
	"time"
)

type ScenePrompt struct {
	ID             string    `gorm:"primaryKey;type:varchar(36)" json:"id"`
	SceneID        string    `gorm:"column:sceneId;type:varchar(36);not null;index:idx_scene" json:"sceneId"`
	UserID         int64     `gorm:"column:userId;type:bigint;not null;index:idx_user" json:"userId"`
	PromptIndex    int       `gorm:"column:promptIndex;type:int;not null;index:idx_scene" json:"promptIndex"`
	Title          string    `gorm:"column:title;type:varchar(200);not null" json:"title"`
	Content        string    `gorm:"column:content;type:text;not null" json:"content"`
	Difficulty     string    `gorm:"column:difficulty;type:varchar(20)" json:"difficulty"`
	Tags           string    `gorm:"column:tags;type:json" json:"tags"`
	ExpectedOutput string    `gorm:"column:expectedOutput;type:text" json:"expectedOutput"`
	CreateTime     time.Time `gorm:"column:createTime;type:datetime;not null;default:CURRENT_TIMESTAMP;autoCreateTime" json:"createTime"`
	UpdateTime     time.Time `gorm:"column:updateTime;type:datetime;not null;default:CURRENT_TIMESTAMP;autoUpdateTime" json:"updateTime"`
	IsDelete       int       `gorm:"column:isDelete;type:tinyint;not null;default:0" json:"-"`
}

func (ScenePrompt) TableName() string {
	return "scene_prompt"
}
