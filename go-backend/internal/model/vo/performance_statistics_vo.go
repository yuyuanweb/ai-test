// Package vo 性能统计数据视图对象
// @author <a href="https://codefather.cn">编程导航学习圈</a>
package vo

type PerformanceStatisticsVO struct {
	AvgResponseTime    float64                 `json:"avgResponseTime"`
	MinResponseTime     int                     `json:"minResponseTime"`
	MaxResponseTime     int                     `json:"maxResponseTime"`
	PerformanceByModel []ModelPerformanceVO    `json:"performanceByModel"`
}

type ModelPerformanceVO struct {
	ModelName        string  `json:"modelName"`
	CallCount        int64   `json:"callCount"`
	AvgResponseTime  float64 `json:"avgResponseTime"`
	MinResponseTime  int     `json:"minResponseTime"`
	MaxResponseTime  int     `json:"maxResponseTime"`
	AvgInputTokens   float64 `json:"avgInputTokens"`
	AvgOutputTokens  float64 `json:"avgOutputTokens"`
}
