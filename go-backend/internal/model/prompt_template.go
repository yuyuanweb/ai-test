// Package model 提示词模板实体模型
// @author <a href="https://codefather.cn">编程导航学习圈</a>
package model

import (
	"database/sql/driver"
	"encoding/json"
	"errors"
	"time"
)

type PromptTemplate struct {
	ID          string         `gorm:"primaryKey;type:varchar(36)" json:"id"`
	UserID      *int64         `gorm:"column:userId;type:bigint" json:"userId"`
	Name        string         `gorm:"column:name;type:varchar(100);not null" json:"name"`
	Description string         `gorm:"column:description;type:text" json:"description"`
	Strategy    string         `gorm:"column:strategy;type:varchar(50);not null" json:"strategy"`
	Content     string         `gorm:"column:content;type:text;not null" json:"content"`
	Variables   VariablesJSON  `gorm:"column:variables;type:json" json:"variables"`
	Category    string         `gorm:"column:category;type:varchar(50)" json:"category"`
	IsPreset    int            `gorm:"column:isPreset;type:tinyint;not null;default:0" json:"isPreset"`
	UsageCount  int            `gorm:"column:usageCount;type:int;not null;default:0" json:"usageCount"`
	IsActive    int            `gorm:"column:isActive;type:tinyint;not null;default:1" json:"isActive"`
	CreateTime  time.Time      `gorm:"column:createTime;type:datetime;not null;default:CURRENT_TIMESTAMP;autoCreateTime" json:"createTime"`
	UpdateTime  time.Time      `gorm:"column:updateTime;type:datetime;not null;default:CURRENT_TIMESTAMP;autoUpdateTime" json:"updateTime"`
	IsDelete    int            `gorm:"column:isDelete;type:tinyint;not null;default:0" json:"-"`
}

func (PromptTemplate) TableName() string {
	return "prompt_template"
}

type VariablesJSON []string

func (v *VariablesJSON) Scan(value interface{}) error {
	if value == nil {
		*v = []string{}
		return nil
	}
	bytes, ok := value.([]byte)
	if !ok {
		return errors.New("failed to unmarshal VariablesJSON")
	}
	return json.Unmarshal(bytes, v)
}

func (v VariablesJSON) Value() (driver.Value, error) {
	if len(v) == 0 {
		return "[]", nil
	}
	return json.Marshal(v)
}
