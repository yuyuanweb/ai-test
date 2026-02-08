// Package vo 测试报告相关视图对象
// @author <a href="https://codefather.cn">编程导航学习圈</a>
package vo

import "time"

// ReportVO 测试报告视图对象
type ReportVO struct {
	TaskID          string              `json:"taskId"`
	TaskName        string              `json:"taskName"`
	Summary         *ReportSummaryVO    `json:"summary"`
	ModelStatistics []ModelStatisticsVO `json:"modelStatistics"`
	RadarChart      *RadarChartDataVO   `json:"radarChart"`
	BarChart        *BarChartDataVO     `json:"barChart"`
	TestResults     []TestResultVO      `json:"testResults"`
}

// ReportSummaryVO 报告摘要视图对象
type ReportSummaryVO struct {
	TotalCost         float64  `json:"totalCost"`
	AvgResponseTimeMs *float64 `json:"avgResponseTimeMs"`
	TotalTokens       int64    `json:"totalTokens"`
	TotalResults      int      `json:"totalResults"`
	ModelCount        int      `json:"modelCount"`
}

// ModelStatisticsVO 模型统计信息视图对象
type ModelStatisticsVO struct {
	ModelName       string   `json:"modelName"`
	TestCount       int      `json:"testCount"`
	AvgResponseTimeMs *float64 `json:"avgResponseTimeMs"`
	AvgInputTokens  *float64 `json:"avgInputTokens"`
	AvgOutputTokens *float64 `json:"avgOutputTokens"`
	TotalTokens     int64    `json:"totalTokens"`
	TotalCost       float64  `json:"totalCost"`
	AvgCost         *float64 `json:"avgCost"`
	AvgUserRating   *float64 `json:"avgUserRating"`
	AvgAiScore      *float64 `json:"avgAiScore"`
}

// RadarChartDataVO 雷达图数据视图对象
type RadarChartDataVO struct {
	Dimensions []string        `json:"dimensions"`
	Series     []RadarSeriesVO `json:"series"`
}

// RadarSeriesVO 雷达图系列数据视图对象
type RadarSeriesVO struct {
	ModelName string    `json:"modelName"`
	Values    []float64 `json:"values"`
}

// BarChartDataVO 柱状图数据视图对象
type BarChartDataVO struct {
	Categories []string      `json:"categories"`
	Series     []BarSeriesVO `json:"series"`
}

// BarSeriesVO 柱状图系列数据视图对象
type BarSeriesVO struct {
	Name string    `json:"name"`
	Data []float64 `json:"data"`
	Unit string    `json:"unit"`
}

// TestResultVO 测试结果视图对象
type TestResultVO struct {
	ID             string    `json:"id"`
	TaskID         string    `json:"taskId"`
	SceneID        string    `json:"sceneId"`
	PromptID       string    `json:"promptId"`
	ModelName      string    `json:"modelName"`
	InputPrompt    string    `json:"inputPrompt"`
	OutputText     string    `json:"outputText"`
	Reasoning      string    `json:"reasoning"`
	ResponseTimeMs int       `json:"responseTimeMs"`
	InputTokens    int       `json:"inputTokens"`
	OutputTokens   int       `json:"outputTokens"`
	Cost           float64   `json:"cost"`
	UserRating     *int      `json:"userRating"`
	AIScore        string    `json:"aiScore"`
	CreateTime     time.Time `json:"createTime"`
}
