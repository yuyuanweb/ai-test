// Package model 批量测试任务实体模型
// @author <a href="https://codefather.cn">编程导航学习圈</a>
package model

import (
	"time"
)

type TestTask struct {
	ID                string     `gorm:"primaryKey;type:varchar(36)" json:"id"`
	UserID            int64      `gorm:"column:userId;type:bigint;not null;index:idx_user_created" json:"userId"`
	Name              string     `gorm:"column:name;type:varchar(200)" json:"name"`
	SceneID           string     `gorm:"column:sceneId;type:varchar(36);not null;index:idx_scene" json:"sceneId"`
	Models            string     `gorm:"column:models;type:json;not null" json:"models"`
	Config            string     `gorm:"column:config;type:json" json:"config"`
	Status            string     `gorm:"column:status;type:varchar(20);not null;index:idx_status" json:"status"`
	TotalSubtasks     int        `gorm:"column:totalSubtasks;type:int;not null;default:0" json:"totalSubtasks"`
	CompletedSubtasks int        `gorm:"column:completedSubtasks;type:int;not null;default:0" json:"completedSubtasks"`
	StartedAt         *time.Time `gorm:"column:startedAt;type:datetime" json:"startedAt"`
	CompletedAt       *time.Time `gorm:"column:completedAt;type:datetime" json:"completedAt"`
	CreateTime        time.Time  `gorm:"column:createTime;type:datetime;not null;default:CURRENT_TIMESTAMP;autoCreateTime" json:"createTime"`
	UpdateTime        time.Time  `gorm:"column:updateTime;type:datetime;not null;default:CURRENT_TIMESTAMP;autoUpdateTime" json:"updateTime"`
	IsDelete          int        `gorm:"column:isDelete;type:tinyint;not null;default:0" json:"-"`
}

func (TestTask) TableName() string {
	return "test_task"
}
