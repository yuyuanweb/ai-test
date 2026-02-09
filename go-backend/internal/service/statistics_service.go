// Package service 数据统计服务层：成本统计与实时成本监控
// @author <a href="https://codefather.cn">编程导航学习圈</a>
package service

import (
	"ai-test-go/internal/constant"
	"ai-test-go/internal/model/vo"
	"ai-test-go/internal/repository"
	"context"
	"encoding/json"
	"strconv"
	"time"

	"github.com/redis/go-redis/v9"
	"ai-test-go/pkg/logger"
)

type StatisticsService struct {
	budgetService           *BudgetService
	conversationMessageRepo *repository.ConversationMessageRepository
	userModelUsageRepo      *repository.UserModelUsageRepository
	userRepo                *repository.UserRepository
	redisClient             *redis.Client
}

func NewStatisticsService(
	budgetService *BudgetService,
	conversationMessageRepo *repository.ConversationMessageRepository,
	userModelUsageRepo *repository.UserModelUsageRepository,
	userRepo *repository.UserRepository,
	redisClient *redis.Client,
) *StatisticsService {
	return &StatisticsService{
		budgetService:           budgetService,
		conversationMessageRepo: conversationMessageRepo,
		userModelUsageRepo:      userModelUsageRepo,
		userRepo:                userRepo,
		redisClient:             redisClient,
	}
}

func (s *StatisticsService) GetCostStatistics(userID int64, days int) (*vo.CostStatisticsVO, error) {
	if days <= 0 {
		days = 30
	}

	if s.redisClient != nil {
		cacheKey := constant.StatisticsCostKeyPrefix + strconv.FormatInt(userID, 10) + ":" + strconv.Itoa(days)
		cached, err := s.redisClient.Get(context.Background(), cacheKey).Result()
		if err == nil {
			var result vo.CostStatisticsVO
			if json.Unmarshal([]byte(cached), &result) == nil {
				logger.Log.Debugf("从缓存获取成本统计数据: userId=%d, days=%d", userID, days)
				return &result, nil
			}
		}
	}

	result := &vo.CostStatisticsVO{}

	usages, err := s.userModelUsageRepo.ListByUserID(userID)
	if err == nil {
		var totalCost float64
		for _, u := range usages {
			totalCost += u.TotalCost
		}
		result.TotalCost = totalCost
		result.CostByModel = make([]vo.ModelCostVO, 0, len(usages))
		for _, u := range usages {
			if u.ModelName == "" {
				continue
			}
			pc := 0.0
			if result.TotalCost > 0 {
				pc = (u.TotalCost / result.TotalCost) * 100
			}
			result.CostByModel = append(result.CostByModel, vo.ModelCostVO{
				ModelName:  u.ModelName,
				Cost:       u.TotalCost,
				Percentage: pc,
			})
		}
		for i := 0; i < len(result.CostByModel); i++ {
			for j := i + 1; j < len(result.CostByModel); j++ {
				if result.CostByModel[j].Cost > result.CostByModel[i].Cost {
					result.CostByModel[i], result.CostByModel[j] = result.CostByModel[j], result.CostByModel[i]
				}
			}
		}
	}

	result.TodayCost = s.budgetService.GetTodayCost(userID)
	result.MonthCost = s.budgetService.GetMonthCost(userID)
	weekCost, _ := s.conversationMessageRepo.GetWeekCostByUserID(userID)
	result.WeekCost = weekCost

	endDate := time.Now()
	startDate := endDate.AddDate(0, 0, -(days - 1))
	startDate = time.Date(startDate.Year(), startDate.Month(), startDate.Day(), 0, 0, 0, 0, time.Local)
	endDate = time.Date(endDate.Year(), endDate.Month(), endDate.Day(), 23, 59, 59, 999999999, time.Local).Add(24 * time.Hour)

	rows, err := s.conversationMessageRepo.GetDailyCostsByUserIDAndDateRange(userID, startDate, endDate)
	if err != nil {
		result.CostTrend = []vo.DailyCostVO{}
	} else {
		dayMap := make(map[string]float64)
		for _, r := range rows {
			dayMap[r.Date] = r.Cost
		}
		result.CostTrend = make([]vo.DailyCostVO, 0, days)
		for d := 0; d < days; d++ {
			dt := startDate.AddDate(0, 0, d)
			dateStr := dt.Format("2006-01-02")
			cost := dayMap[dateStr]
			result.CostTrend = append(result.CostTrend, vo.DailyCostVO{Date: dateStr, Cost: cost})
		}
	}

	if s.redisClient != nil {
		cacheKey := constant.StatisticsCostKeyPrefix + strconv.FormatInt(userID, 10) + ":" + strconv.Itoa(days)
		body, _ := json.Marshal(result)
		s.redisClient.Set(context.Background(), cacheKey, string(body), constant.StatisticsTTLMinutes*time.Minute)
	}

	return result, nil
}

func (s *StatisticsService) GetRealtimeCost(userID int64) (*vo.RealtimeCostVO, error) {
	result := &vo.RealtimeCostVO{
		BudgetStatus:  vo.BudgetStatusNormal,
		BudgetMessage: "预算充足",
	}

	user, err := s.userRepo.FindByID(userID)
	if err != nil || user == nil {
		result.TodayCost = 0
		result.MonthCost = 0
		return result, nil
	}

	alertThreshold := user.BudgetAlertThreshold
	if alertThreshold <= 0 {
		alertThreshold = 80
	}
	result.AlertThreshold = alertThreshold
	result.DailyBudget = user.DailyBudget
	result.MonthlyBudget = user.MonthlyBudget

	result.TodayCost = s.budgetService.GetTodayCost(userID)
	result.MonthCost = s.budgetService.GetMonthCost(userID)

	todayCalls, _ := s.conversationMessageRepo.GetTodayApiCallsByUserID(userID)
	todayTokens, _ := s.conversationMessageRepo.GetTodayTokensByUserID(userID)
	result.TodayApiCalls = todayCalls
	result.TodayTokens = todayTokens

	if todayCalls > 0 && result.TodayCost > 0 {
		result.AvgCostPerCall = result.TodayCost / float64(todayCalls)
	}

	if user.DailyBudget > 0 {
		result.DailyUsagePercent = (result.TodayCost / user.DailyBudget) * 100
	}
	if user.MonthlyBudget > 0 {
		result.MonthlyUsagePercent = (result.MonthCost / user.MonthlyBudget) * 100
	}

	budgetStatus := s.budgetService.CheckBudget(userID)
	result.BudgetStatus = budgetStatus.Status
	result.BudgetMessage = budgetStatus.Message

	return result, nil
}

func (s *StatisticsService) GetUsageStatistics(userID int64, days int) (*vo.UsageStatisticsVO, error) {
	if days <= 0 {
		days = 30
	}

	if s.redisClient != nil {
		cacheKey := constant.StatisticsUsageKeyPrefix + strconv.FormatInt(userID, 10) + ":" + strconv.Itoa(days)
		cached, err := s.redisClient.Get(context.Background(), cacheKey).Result()
		if err == nil {
			var result vo.UsageStatisticsVO
			if json.Unmarshal([]byte(cached), &result) == nil {
				logger.Log.Debugf("从缓存获取使用量统计数据: userId=%d, days=%d", userID, days)
				return &result, nil
			}
		}
	}

	result := &vo.UsageStatisticsVO{}

	usages, err := s.userModelUsageRepo.ListByUserID(userID)
	if err == nil {
		var totalTokens int64
		for _, u := range usages {
			totalTokens += u.TotalTokens
		}
		result.TotalTokens = totalTokens
	}

	totalApiCalls, _ := s.conversationMessageRepo.GetTotalApiCallsByUserID(userID)
	todayApiCalls, _ := s.conversationMessageRepo.GetTodayApiCallsByUserID(userID)
	totalInput, _ := s.conversationMessageRepo.GetTotalInputTokensByUserID(userID)
	totalOutput, _ := s.conversationMessageRepo.GetTotalOutputTokensByUserID(userID)
	todayTokens, _ := s.conversationMessageRepo.GetTodayTokensByUserID(userID)
	result.TotalApiCalls = totalApiCalls
	result.TodayApiCalls = todayApiCalls
	result.TotalInputTokens = totalInput
	result.TotalOutputTokens = totalOutput
	result.TodayTokens = todayTokens

	messages, err := s.conversationMessageRepo.ListAssistantMessagesByUserID(userID)
	callCountByModel := make(map[string]int64)
	for _, m := range messages {
		if m.ModelName != "" {
			callCountByModel[m.ModelName]++
		}
	}

	result.UsageByModel = make([]vo.ModelUsageVO, 0, len(usages))
	for _, u := range usages {
		if u.ModelName == "" {
			continue
		}
		calls := callCountByModel[u.ModelName]
		pc := 0.0
		if totalApiCalls > 0 {
			pc = float64(calls) * 100 / float64(totalApiCalls)
		}
		result.UsageByModel = append(result.UsageByModel, vo.ModelUsageVO{
			ModelName:  u.ModelName,
			CallCount:  calls,
			Tokens:     u.TotalTokens,
			Percentage: pc,
		})
	}
	for i := 0; i < len(result.UsageByModel); i++ {
		for j := i + 1; j < len(result.UsageByModel); j++ {
			if result.UsageByModel[j].CallCount > result.UsageByModel[i].CallCount {
				result.UsageByModel[i], result.UsageByModel[j] = result.UsageByModel[j], result.UsageByModel[i]
			}
		}
	}

	endDate := time.Now()
	startDate := endDate.AddDate(0, 0, -(days - 1))
	startDate = time.Date(startDate.Year(), startDate.Month(), startDate.Day(), 0, 0, 0, 0, time.Local)
	endDate = time.Date(endDate.Year(), endDate.Month(), endDate.Day(), 23, 59, 59, 999999999, time.Local).Add(24 * time.Hour)

	rows, err := s.conversationMessageRepo.GetDailyUsageByUserIDAndDateRange(userID, startDate, endDate)
	if err != nil {
		result.UsageTrend = []vo.DailyUsageVO{}
	} else {
		dayMap := make(map[string]struct{ ApiCalls, Tokens int64 })
		for _, r := range rows {
			dayMap[r.Date] = struct{ ApiCalls, Tokens int64 }{r.ApiCalls, r.Tokens}
		}
		result.UsageTrend = make([]vo.DailyUsageVO, 0, days)
		for d := 0; d < days; d++ {
			dt := startDate.AddDate(0, 0, d)
			dateStr := dt.Format("2006-01-02")
			v := dayMap[dateStr]
			result.UsageTrend = append(result.UsageTrend, vo.DailyUsageVO{
				Date: dateStr, ApiCalls: v.ApiCalls, Tokens: v.Tokens,
			})
		}
	}

	if s.redisClient != nil {
		cacheKey := constant.StatisticsUsageKeyPrefix + strconv.FormatInt(userID, 10) + ":" + strconv.Itoa(days)
		body, _ := json.Marshal(result)
		s.redisClient.Set(context.Background(), cacheKey, string(body), constant.StatisticsTTLMinutes*time.Minute)
	}

	return result, nil
}

func (s *StatisticsService) GetPerformanceStatistics(userID int64) (*vo.PerformanceStatisticsVO, error) {
	if s.redisClient != nil {
		cacheKey := constant.StatisticsPerformanceKeyPrefix + strconv.FormatInt(userID, 10)
		cached, err := s.redisClient.Get(context.Background(), cacheKey).Result()
		if err == nil {
			var result vo.PerformanceStatisticsVO
			if json.Unmarshal([]byte(cached), &result) == nil {
				logger.Log.Debugf("从缓存获取性能统计数据: userId=%d", userID)
				return &result, nil
			}
		}
	}

	result := &vo.PerformanceStatisticsVO{}

	messages, err := s.conversationMessageRepo.ListAssistantMessagesByUserID(userID)
	if err != nil || len(messages) == 0 {
		result.AvgResponseTime = 0
		result.MinResponseTime = 0
		result.MaxResponseTime = 0
		result.PerformanceByModel = []vo.ModelPerformanceVO{}
		return result, nil
	}

	var sumRt int64
	minRt := 1<<31 - 1
	maxRt := 0
	for _, m := range messages {
		rt := m.ResponseTimeMs
		if rt > 0 {
			sumRt += int64(rt)
			if rt < minRt {
				minRt = rt
			}
			if rt > maxRt {
				maxRt = rt
			}
		}
	}
	if minRt == 1<<31-1 {
		minRt = 0
	}
	countWithRt := 0
	for _, m := range messages {
		if m.ResponseTimeMs > 0 {
			countWithRt++
		}
	}
	result.AvgResponseTime = 0
	if countWithRt > 0 {
		result.AvgResponseTime = float64(sumRt) / float64(countWithRt)
		result.AvgResponseTime = float64(int64(result.AvgResponseTime*100+0.5)) / 100
	}
	result.MinResponseTime = minRt
	result.MaxResponseTime = maxRt

	byModel := make(map[string][]int)
	byModelInput := make(map[string][]int)
	byModelOutput := make(map[string][]int)
	for _, m := range messages {
		if m.ModelName == "" || m.ResponseTimeMs <= 0 {
			continue
		}
		byModel[m.ModelName] = append(byModel[m.ModelName], m.ResponseTimeMs)
		byModelInput[m.ModelName] = append(byModelInput[m.ModelName], m.InputTokens)
		byModelOutput[m.ModelName] = append(byModelOutput[m.ModelName], m.OutputTokens)
	}

	result.PerformanceByModel = make([]vo.ModelPerformanceVO, 0, len(byModel))
	for modelName, rts := range byModel {
		if len(rts) == 0 {
			continue
		}
		var sum int64
		minR, maxR := 1<<31-1, 0
		for _, rt := range rts {
			sum += int64(rt)
			if rt < minR {
				minR = rt
			}
			if rt > maxR {
				maxR = rt
			}
		}
		avgRt := float64(sum) / float64(len(rts))
		avgRt = float64(int64(avgRt*100+0.5)) / 100

		inputs := byModelInput[modelName]
		outputs := byModelOutput[modelName]
		var sumIn, sumOut int64
		for _, v := range inputs {
			sumIn += int64(v)
		}
		for _, v := range outputs {
			sumOut += int64(v)
		}
		avgIn := 0.0
		avgOut := 0.0
		if len(inputs) > 0 {
			avgIn = float64(sumIn) / float64(len(inputs))
			avgIn = float64(int64(avgIn*100+0.5)) / 100
		}
		if len(outputs) > 0 {
			avgOut = float64(sumOut) / float64(len(outputs))
			avgOut = float64(int64(avgOut*100+0.5)) / 100
		}

		result.PerformanceByModel = append(result.PerformanceByModel, vo.ModelPerformanceVO{
			ModelName:       modelName,
			CallCount:       int64(len(rts)),
			AvgResponseTime: avgRt,
			MinResponseTime: minR,
			MaxResponseTime: maxR,
			AvgInputTokens:  avgIn,
			AvgOutputTokens: avgOut,
		})
	}
	for i := 0; i < len(result.PerformanceByModel); i++ {
		for j := i + 1; j < len(result.PerformanceByModel); j++ {
			if result.PerformanceByModel[j].AvgResponseTime < result.PerformanceByModel[i].AvgResponseTime {
				result.PerformanceByModel[i], result.PerformanceByModel[j] = result.PerformanceByModel[j], result.PerformanceByModel[i]
			}
		}
	}

	if s.redisClient != nil {
		cacheKey := constant.StatisticsPerformanceKeyPrefix + strconv.FormatInt(userID, 10)
		body, _ := json.Marshal(result)
		s.redisClient.Set(context.Background(), cacheKey, string(body), constant.StatisticsTTLMinutes*time.Minute)
	}

	return result, nil
}