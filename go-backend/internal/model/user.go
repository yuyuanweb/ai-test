// Package model 用户实体模型
// @author <a href="https://codefather.cn">编程导航学习圈</a>
package model

import (
	"time"
)

type User struct {
	ID                   int64      `gorm:"primaryKey;autoIncrement" json:"id"`
	UserAccount          string     `gorm:"column:userAccount;type:varchar(256);not null;uniqueIndex" json:"userAccount"`
	UserPassword         string     `gorm:"column:userPassword;type:varchar(512);not null" json:"-"`
	UserName             string     `gorm:"column:userName;type:varchar(256)" json:"userName"`
	UserAvatar           string     `gorm:"column:userAvatar;type:varchar(1024)" json:"userAvatar"`
	UserProfile          string     `gorm:"column:userProfile;type:varchar(512)" json:"userProfile"`
	UserRole             string     `gorm:"column:userRole;type:varchar(256);not null;default:user" json:"userRole"`
	DailyBudget          float64    `gorm:"column:dailyBudget;type:decimal(10,2)" json:"dailyBudget"`
	MonthlyBudget        float64    `gorm:"column:monthlyBudget;type:decimal(10,2)" json:"monthlyBudget"`
	BudgetAlertThreshold int        `gorm:"column:budgetAlertThreshold;type:int;default:80" json:"budgetAlertThreshold"`
	EditTime             *time.Time `gorm:"column:editTime;type:datetime;default:CURRENT_TIMESTAMP" json:"editTime"`
	CreateTime           time.Time  `gorm:"column:createTime;type:datetime;not null;default:CURRENT_TIMESTAMP;autoCreateTime" json:"createTime"`
	UpdateTime           time.Time  `gorm:"column:updateTime;type:datetime;not null;default:CURRENT_TIMESTAMP;autoUpdateTime" json:"updateTime"`
	IsDelete             int        `gorm:"column:isDelete;type:tinyint;not null;default:0" json:"-"`
}

func (User) TableName() string {
	return "user"
}
