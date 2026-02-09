// Package vo 使用统计数据视图对象
// @author <a href="https://codefather.cn">编程导航学习圈</a>
package vo

type UsageStatisticsVO struct {
	TotalApiCalls     int64            `json:"totalApiCalls"`
	TodayApiCalls     int64            `json:"todayApiCalls"`
	TotalTokens       int64            `json:"totalTokens"`
	TotalInputTokens  int64            `json:"totalInputTokens"`
	TotalOutputTokens int64            `json:"totalOutputTokens"`
	TodayTokens       int64            `json:"todayTokens"`
	UsageByModel      []ModelUsageVO   `json:"usageByModel"`
	UsageTrend        []DailyUsageVO   `json:"usageTrend"`
}

type ModelUsageVO struct {
	ModelName  string  `json:"modelName"`
	CallCount  int64   `json:"callCount"`
	Tokens     int64   `json:"tokens"`
	Percentage float64 `json:"percentage"`
}

type DailyUsageVO struct {
	Date     string `json:"date"`
	ApiCalls int64  `json:"apiCalls"`
	Tokens   int64  `json:"tokens"`
}
