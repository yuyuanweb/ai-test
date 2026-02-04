// Package dto 用户更新请求
// @author <a href="https://codefather.cn">编程导航学习圈</a>
package dto

type UserUpdateRequest struct {
	ID                   *int64   `json:"id"`
	UserName             string   `json:"userName"`
	UserAvatar           string   `json:"userAvatar"`
	UserProfile          string   `json:"userProfile"`
	UserRole             string   `json:"userRole"`
	DailyBudget          *float64 `json:"dailyBudget"`
	MonthlyBudget        *float64 `json:"monthlyBudget"`
	BudgetAlertThreshold *int     `json:"budgetAlertThreshold"`
}
