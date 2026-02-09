// Package vo 预算状态视图对象
// @author <a href="https://codefather.cn">编程导航学习圈</a>
package vo

type BudgetStatusVO struct {
	CanProceed           bool    `json:"canProceed"`
	Status               string  `json:"status"`
	Message              string  `json:"message"`
	TodayCost            float64 `json:"todayCost"`
	MonthCost            float64 `json:"monthCost"`
	DailyBudget          float64 `json:"dailyBudget"`
	MonthlyBudget        float64 `json:"monthlyBudget"`
	DailyUsagePercent    float64 `json:"dailyUsagePercent"`
	MonthlyUsagePercent  float64 `json:"monthlyUsagePercent"`
}

const (
	BudgetStatusNormal   = "normal"
	BudgetStatusWarning  = "warning"
	BudgetStatusExceeded = "exceeded"
)

func BudgetStatusNormalVO(todayCost, monthCost, dailyBudget, monthlyBudget float64) BudgetStatusVO {
	return BudgetStatusVO{
		CanProceed:    true,
		Status:        BudgetStatusNormal,
		Message:       "预算充足",
		TodayCost:     todayCost,
		MonthCost:     monthCost,
		DailyBudget:   dailyBudget,
		MonthlyBudget: monthlyBudget,
	}
}

func BudgetStatusWarningVO(message string, todayCost, monthCost, dailyBudget, monthlyBudget float64) BudgetStatusVO {
	return BudgetStatusVO{
		CanProceed:    true,
		Status:        BudgetStatusWarning,
		Message:       message,
		TodayCost:     todayCost,
		MonthCost:     monthCost,
		DailyBudget:   dailyBudget,
		MonthlyBudget: monthlyBudget,
	}
}

func BudgetStatusExceededVO(message string, todayCost, monthCost, dailyBudget, monthlyBudget float64) BudgetStatusVO {
	return BudgetStatusVO{
		CanProceed:    false,
		Status:        BudgetStatusExceeded,
		Message:       message,
		TodayCost:     todayCost,
		MonthCost:     monthCost,
		DailyBudget:   dailyBudget,
		MonthlyBudget: monthlyBudget,
	}
}
