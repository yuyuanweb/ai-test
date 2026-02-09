// Package vo 实时成本统计视图对象
// @author <a href="https://codefather.cn">编程导航学习圈</a>
package vo

type RealtimeCostVO struct {
	TodayCost            float64 `json:"todayCost"`
	MonthCost            float64 `json:"monthCost"`
	TodayTokens          int64   `json:"todayTokens"`
	TodayApiCalls        int64   `json:"todayApiCalls"`
	AvgCostPerCall       float64 `json:"avgCostPerCall"`
	DailyBudget          float64 `json:"dailyBudget"`
	MonthlyBudget        float64 `json:"monthlyBudget"`
	DailyUsagePercent    float64 `json:"dailyUsagePercent"`
	MonthlyUsagePercent  float64 `json:"monthlyUsagePercent"`
	BudgetStatus         string  `json:"budgetStatus"`
	BudgetMessage        string  `json:"budgetMessage"`
	AlertThreshold       int     `json:"alertThreshold"`
}
