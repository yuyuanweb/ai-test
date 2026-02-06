// Package model 场景实体模型
// @author <a href="https://codefather.cn">编程导航学习圈</a>
package model

import (
	"time"
)

type Scene struct {
	ID          string     `gorm:"primaryKey;type:varchar(36)" json:"id"`
	UserID      *int64     `gorm:"column:userId;type:bigint" json:"userId"`
	Name        string     `gorm:"column:name;type:varchar(100);not null" json:"name"`
	Description string     `gorm:"column:description;type:text" json:"description"`
	Category    string     `gorm:"column:category;type:varchar(50)" json:"category"`
	IsPreset    int        `gorm:"column:isPreset;type:tinyint;not null;default:0" json:"isPreset"`
	IsActive    int        `gorm:"column:isActive;type:tinyint;not null;default:1" json:"isActive"`
	CreateTime  time.Time  `gorm:"column:createTime;type:datetime;not null;default:CURRENT_TIMESTAMP;autoCreateTime" json:"createTime"`
	UpdateTime  time.Time  `gorm:"column:updateTime;type:datetime;not null;default:CURRENT_TIMESTAMP;autoUpdateTime" json:"updateTime"`
	IsDelete    int        `gorm:"column:isDelete;type:tinyint;not null;default:0" json:"-"`
}

func (Scene) TableName() string {
	return "scene"
}
