// Package vo 脱敏后的登录用户信息
// @author <a href="https://codefather.cn">编程导航学习圈</a>
package vo

import "time"

type LoginUserVO struct {
	ID                   int64     `json:"id"`
	UserAccount          string    `json:"userAccount"`
	UserName             string    `json:"userName"`
	UserAvatar           string    `json:"userAvatar"`
	UserProfile          string    `json:"userProfile"`
	UserRole             string    `json:"userRole"`
	DailyBudget          float64   `json:"dailyBudget"`
	MonthlyBudget        float64   `json:"monthlyBudget"`
	BudgetAlertThreshold int       `json:"budgetAlertThreshold"`
	CreateTime           time.Time `json:"createTime"`
	UpdateTime           time.Time `json:"updateTime"`
}
