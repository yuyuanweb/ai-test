// Package vo 用户统计数据视图对象
// @author <a href="https://codefather.cn">编程导航学习圈</a>
package vo

type UserStatisticsVO struct {
	TotalModels               int64   `json:"totalModels"`
	TotalTokens               int64   `json:"totalTokens"`
	TotalCost                 float64 `json:"totalCost"`
	TodayCost                 float64 `json:"todayCost"`
	MonthCost                 float64 `json:"monthCost"`
	DailyBudget               float64 `json:"dailyBudget"`
	MonthlyBudget             float64 `json:"monthlyBudget"`
	BudgetAlertThreshold      int     `json:"budgetAlertThreshold"`
	DailyBudgetUsagePercent   float64 `json:"dailyBudgetUsagePercent"`
	MonthlyBudgetUsagePercent float64 `json:"monthlyBudgetUsagePercent"`
	DailyBudgetAlert          bool    `json:"dailyBudgetAlert"`
	MonthlyBudgetAlert        bool    `json:"monthlyBudgetAlert"`
}
