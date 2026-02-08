// Package service 报告服务层
// @author <a href="https://codefather.cn">编程导航学习圈</a>
package service

import (
	"ai-test-go/internal/model"
	"ai-test-go/internal/model/vo"
	"ai-test-go/internal/repository"
	"ai-test-go/pkg/common"
	"ai-test-go/pkg/logger"
	"encoding/json"
	"errors"
	"math"

	"gorm.io/gorm"
)

// 雷达图维度名称
var radarDimensions = []string{"准确性", "完整性", "速度", "成本效率", "用户满意度"}

// 速度标准化基准值（毫秒）
const speedNormalizeBase = 10000.0

// 成本标准化系数
const costNormalizeFactor = 100.0

// 完整性计算中输出长度的除数
const completenessLengthDivisor = 1000.0

// 完整性计算中输出Token的除数
const completenessTokenDivisor = 100.0

// 标准化分数的最大值
const normalizeMaxScore = 100.0

// 成本标准化偏移量（避免除零）
const costNormalizeOffset = 0.01

// 平均成本保留小数位数
const avgCostScale = 6

type ReportService struct {
	testTaskRepo   *repository.TestTaskRepository
	testResultRepo *repository.TestResultRepository
}

func NewReportService(
	testTaskRepo *repository.TestTaskRepository,
	testResultRepo *repository.TestResultRepository,
) *ReportService {
	return &ReportService{
		testTaskRepo:   testTaskRepo,
		testResultRepo: testResultRepo,
	}
}

// GenerateReport 生成测试报告
func (s *ReportService) GenerateReport(taskID string, userID int64) (*vo.ReportVO, error) {
	task, err := s.testTaskRepo.FindByID(taskID)
	if err != nil {
		if errors.Is(err, gorm.ErrRecordNotFound) {
			return nil, common.NewBusinessException(common.NOT_FOUND_ERROR, "任务不存在")
		}
		logger.Log.Errorf("查询任务失败: taskId=%s, error=%v", taskID, err)
		return nil, common.NewBusinessException(common.SYSTEM_ERROR, "查询任务失败")
	}

	if task.UserID != userID {
		return nil, common.NewBusinessException(common.NO_AUTH_ERROR, "无权限查看该任务报告")
	}

	testResults, err := s.testResultRepo.ListByTaskID(taskID)
	if err != nil {
		logger.Log.Errorf("查询测试结果失败: taskId=%s, error=%v", taskID, err)
		return nil, common.NewBusinessException(common.SYSTEM_ERROR, "查询测试结果失败")
	}

	if len(testResults) == 0 {
		return nil, common.NewBusinessException(common.NOT_FOUND_ERROR, "该任务暂无测试结果")
	}

	report := &vo.ReportVO{
		TaskID:   taskID,
		TaskName: task.Name,
	}

	summary := s.calculateSummary(testResults)
	report.Summary = summary

	modelStatistics := s.calculateModelStatistics(testResults)
	report.ModelStatistics = modelStatistics

	radarChart := s.generateRadarChartData(testResults, modelStatistics)
	report.RadarChart = radarChart

	barChart := s.generateBarChartData(modelStatistics)
	report.BarChart = barChart

	testResultVOs := s.convertToTestResultVOs(testResults)
	report.TestResults = testResultVOs

	return report, nil
}

// calculateSummary 计算报告摘要
func (s *ReportService) calculateSummary(testResults []model.TestResult) *vo.ReportSummaryVO {
	summary := &vo.ReportSummaryVO{}

	var totalCost float64
	var totalResponseTime int64
	var responseTimeCount int
	var totalTokens int64
	modelNames := make(map[string]struct{})

	for _, result := range testResults {
		totalCost += result.Cost

		if result.ResponseTimeMs > 0 {
			totalResponseTime += int64(result.ResponseTimeMs)
			responseTimeCount++
		}

		totalTokens += int64(result.InputTokens) + int64(result.OutputTokens)

		modelNames[result.ModelName] = struct{}{}
	}

	summary.TotalCost = totalCost
	summary.TotalTokens = totalTokens
	summary.TotalResults = len(testResults)
	summary.ModelCount = len(modelNames)

	if responseTimeCount > 0 {
		avg := float64(totalResponseTime) / float64(responseTimeCount)
		summary.AvgResponseTimeMs = &avg
	}

	return summary
}

// calculateModelStatistics 计算各模型统计信息
func (s *ReportService) calculateModelStatistics(testResults []model.TestResult) []vo.ModelStatisticsVO {
	groupedByModel := make(map[string][]model.TestResult)
	for _, result := range testResults {
		groupedByModel[result.ModelName] = append(groupedByModel[result.ModelName], result)
	}

	var statisticsList []vo.ModelStatisticsVO

	for modelName, modelResults := range groupedByModel {
		statistics := vo.ModelStatisticsVO{
			ModelName: modelName,
			TestCount: len(modelResults),
		}

		var totalResponseTime int64
		var responseTimeCount int
		var totalInputTokens int64
		var inputTokensCount int
		var totalOutputTokens int64
		var outputTokensCount int
		var totalTokens int64
		var totalCost float64
		var totalUserRating int64
		var userRatingCount int
		var totalAiScore float64
		var aiScoreCount int

		for _, result := range modelResults {
			if result.ResponseTimeMs > 0 {
				totalResponseTime += int64(result.ResponseTimeMs)
				responseTimeCount++
			}

			if result.InputTokens > 0 {
				totalInputTokens += int64(result.InputTokens)
				inputTokensCount++
			}

			if result.OutputTokens > 0 {
				totalOutputTokens += int64(result.OutputTokens)
				outputTokensCount++
			}

			totalTokens += int64(result.InputTokens) + int64(result.OutputTokens)
			totalCost += result.Cost

			if result.UserRating != nil && *result.UserRating > 0 {
				totalUserRating += int64(*result.UserRating)
				userRatingCount++
			}

			if result.AIScore != "" {
				score := s.parseAiScoreTotal(result.AIScore)
				if score > 0 {
					totalAiScore += score
					aiScoreCount++
				}
			}
		}

		if responseTimeCount > 0 {
			avg := float64(totalResponseTime) / float64(responseTimeCount)
			statistics.AvgResponseTimeMs = &avg
		}

		if inputTokensCount > 0 {
			avg := float64(totalInputTokens) / float64(inputTokensCount)
			statistics.AvgInputTokens = &avg
		}

		if outputTokensCount > 0 {
			avg := float64(totalOutputTokens) / float64(outputTokensCount)
			statistics.AvgOutputTokens = &avg
		}

		statistics.TotalTokens = totalTokens
		statistics.TotalCost = totalCost

		if statistics.TestCount > 0 {
			avgCost := totalCost / float64(statistics.TestCount)
			rounded := math.Round(avgCost*math.Pow10(avgCostScale)) / math.Pow10(avgCostScale)
			statistics.AvgCost = &rounded
		}

		if userRatingCount > 0 {
			avg := float64(totalUserRating) / float64(userRatingCount)
			statistics.AvgUserRating = &avg
		}

		if aiScoreCount > 0 {
			avg := totalAiScore / float64(aiScoreCount)
			statistics.AvgAiScore = &avg
		}

		statisticsList = append(statisticsList, statistics)
	}

	return statisticsList
}

// generateRadarChartData 生成雷达图数据
func (s *ReportService) generateRadarChartData(testResults []model.TestResult, modelStatistics []vo.ModelStatisticsVO) *vo.RadarChartDataVO {
	radarChart := &vo.RadarChartDataVO{
		Dimensions: radarDimensions,
	}

	var seriesList []vo.RadarSeriesVO

	for _, statistics := range modelStatistics {
		series := vo.RadarSeriesVO{
			ModelName: statistics.ModelName,
		}

		var values []float64

		// 准确性（基于AI评分）
		accuracy := 0.0
		if statistics.AvgAiScore != nil {
			accuracy = *statistics.AvgAiScore
		}
		values = append(values, normalizeScore(accuracy, 0.0, normalizeMaxScore))

		// 完整性（基于输出文本长度和Token数）
		completeness := s.calculateCompleteness(testResults, statistics.ModelName)
		values = append(values, normalizeScore(completeness, 0.0, normalizeMaxScore))

		// 速度（响应时间越短分数越高）
		speed := 0.0
		if statistics.AvgResponseTimeMs != nil {
			speed = normalizeSpeed(*statistics.AvgResponseTimeMs)
		}
		values = append(values, speed)

		// 成本效率（成本越低分数越高）
		costEfficiency := 0.0
		if statistics.AvgCost != nil {
			costEfficiency = normalizeCostEfficiency(*statistics.AvgCost)
		}
		values = append(values, costEfficiency)

		// 用户满意度（1-5分标准化到0-100）
		userSatisfaction := 0.0
		if statistics.AvgUserRating != nil {
			userSatisfaction = normalizeScore(*statistics.AvgUserRating, 1.0, 5.0)
		}
		values = append(values, userSatisfaction)

		series.Values = values
		seriesList = append(seriesList, series)
	}

	radarChart.Series = seriesList
	return radarChart
}

// calculateCompleteness 计算完整性（基于输出文本长度和Token数）
func (s *ReportService) calculateCompleteness(testResults []model.TestResult, modelName string) float64 {
	var modelResults []model.TestResult
	for _, result := range testResults {
		if result.ModelName == modelName {
			modelResults = append(modelResults, result)
		}
	}

	if len(modelResults) == 0 {
		return 0.0
	}

	var totalOutputLength int
	var outputLengthCount int
	var totalOutputTokens int64
	var outputTokensCount int

	for _, result := range modelResults {
		if result.OutputText != "" {
			totalOutputLength += len(result.OutputText)
			outputLengthCount++
		}
		if result.OutputTokens > 0 {
			totalOutputTokens += int64(result.OutputTokens)
			outputTokensCount++
		}
	}

	avgOutputLength := 0.0
	if outputLengthCount > 0 {
		avgOutputLength = float64(totalOutputLength) / float64(outputLengthCount)
	}

	avgOutputTokens := 0.0
	if outputTokensCount > 0 {
		avgOutputTokens = float64(totalOutputTokens) / float64(outputTokensCount)
	}

	completeness := (avgOutputLength/completenessLengthDivisor + avgOutputTokens/completenessTokenDivisor) / 2.0
	return math.Min(completeness, normalizeMaxScore)
}

// normalizeScore 标准化分数到0-100范围
func normalizeScore(value, min, max float64) float64 {
	if max == min {
		return 0.0
	}
	normalized := ((value - min) / (max - min)) * normalizeMaxScore
	return math.Max(0.0, math.Min(normalizeMaxScore, normalized))
}

// normalizeSpeed 标准化速度（响应时间越短，分数越高）
func normalizeSpeed(responseTimeMs float64) float64 {
	if responseTimeMs <= 0 {
		return 0.0
	}
	normalized := speedNormalizeBase / responseTimeMs
	return math.Min(normalizeMaxScore, normalized)
}

// normalizeCostEfficiency 标准化成本效率（成本越低，分数越高）
func normalizeCostEfficiency(avgCost float64) float64 {
	if avgCost <= 0 {
		return 0.0
	}
	normalized := 1.0 / (avgCost*costNormalizeFactor + costNormalizeOffset)
	return math.Min(normalizeMaxScore, normalized*normalizeMaxScore)
}

// generateBarChartData 生成柱状图数据
func (s *ReportService) generateBarChartData(modelStatistics []vo.ModelStatisticsVO) *vo.BarChartDataVO {
	barChart := &vo.BarChartDataVO{}

	var categories []string
	for _, statistics := range modelStatistics {
		categories = append(categories, statistics.ModelName)
	}
	barChart.Categories = categories

	var seriesList []vo.BarSeriesVO

	// 平均响应时间
	responseTimeSeries := vo.BarSeriesVO{
		Name: "平均响应时间",
		Unit: "ms",
	}
	var responseTimeData []float64
	for _, statistics := range modelStatistics {
		if statistics.AvgResponseTimeMs != nil {
			responseTimeData = append(responseTimeData, *statistics.AvgResponseTimeMs)
		} else {
			responseTimeData = append(responseTimeData, 0.0)
		}
	}
	responseTimeSeries.Data = responseTimeData
	seriesList = append(seriesList, responseTimeSeries)

	// 总Token消耗
	totalTokensSeries := vo.BarSeriesVO{
		Name: "总Token消耗",
		Unit: "tokens",
	}
	var totalTokensData []float64
	for _, statistics := range modelStatistics {
		totalTokensData = append(totalTokensData, float64(statistics.TotalTokens))
	}
	totalTokensSeries.Data = totalTokensData
	seriesList = append(seriesList, totalTokensSeries)

	// 总成本
	totalCostSeries := vo.BarSeriesVO{
		Name: "总成本",
		Unit: "USD",
	}
	var totalCostData []float64
	for _, statistics := range modelStatistics {
		totalCostData = append(totalCostData, statistics.TotalCost)
	}
	totalCostSeries.Data = totalCostData
	seriesList = append(seriesList, totalCostSeries)

	barChart.Series = seriesList
	return barChart
}

// convertToTestResultVOs 批量转换测试结果为VO
func (s *ReportService) convertToTestResultVOs(testResults []model.TestResult) []vo.TestResultVO {
	var vos []vo.TestResultVO
	for _, result := range testResults {
		resultVO := vo.TestResultVO{
			ID:             result.ID,
			TaskID:         result.TaskID,
			SceneID:        result.SceneID,
			PromptID:       result.PromptID,
			ModelName:      result.ModelName,
			InputPrompt:    result.InputPrompt,
			OutputText:     result.OutputText,
			Reasoning:      result.Reasoning,
			ResponseTimeMs: result.ResponseTimeMs,
			InputTokens:    result.InputTokens,
			OutputTokens:   result.OutputTokens,
			Cost:           result.Cost,
			UserRating:     result.UserRating,
			AIScore:        result.AIScore,
			CreateTime:     result.CreateTime,
		}
		vos = append(vos, resultVO)
	}
	return vos
}

// parseAiScoreTotal 解析AI评分JSON，优先使用 averageRating（多评委），兼容 total 字段
func (s *ReportService) parseAiScoreTotal(aiScoreJSON string) float64 {
	if aiScoreJSON == "" {
		return 0.0
	}

	var aiScoreMap map[string]interface{}
	if err := json.Unmarshal([]byte(aiScoreJSON), &aiScoreMap); err != nil {
		logger.Log.Warnf("解析AI评分失败: %s, error=%v", aiScoreJSON, err)
		return 0.0
	}

	if v := parseNumberFromMap(aiScoreMap, "averageRating"); v > 0 {
		return v
	}
	return parseNumberFromMap(aiScoreMap, "total")
}

func parseNumberFromMap(m map[string]interface{}, key string) float64 {
	obj, ok := m[key]
	if !ok {
		return 0.0
	}
	switch v := obj.(type) {
	case float64:
		return v
	case int:
		return float64(v)
	case int64:
		return float64(v)
	default:
		return 0.0
	}
}
