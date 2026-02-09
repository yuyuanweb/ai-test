// Package dto 预算更新请求
// @author <a href="https://codefather.cn">编程导航学习圈</a>
package dto

type BudgetUpdateRequest struct {
	DailyBudget          *float64 `json:"dailyBudget"`
	MonthlyBudget        *float64 `json:"monthlyBudget"`
	BudgetAlertThreshold *int     `json:"budgetAlertThreshold"`
}
