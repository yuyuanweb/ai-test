// Package vo 成本统计数据视图对象
// @author <a href="https://codefather.cn">编程导航学习圈</a>
package vo

type CostStatisticsVO struct {
	TotalCost   float64         `json:"totalCost"`
	TodayCost   float64         `json:"todayCost"`
	WeekCost    float64         `json:"weekCost"`
	MonthCost   float64         `json:"monthCost"`
	CostByModel []ModelCostVO   `json:"costByModel"`
	CostTrend   []DailyCostVO   `json:"costTrend"`
}

type ModelCostVO struct {
	ModelName   string  `json:"modelName"`
	Cost        float64 `json:"cost"`
	Percentage  float64 `json:"percentage"`
}

type DailyCostVO struct {
	Date  string  `json:"date"`
	Cost  float64 `json:"cost"`
}
