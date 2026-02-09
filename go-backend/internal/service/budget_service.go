// Package service 预算服务层：实时成本统计与预算预警
// @author <a href="https://codefather.cn">编程导航学习圈</a>
package service

import (
	"ai-test-go/internal/constant"
	"ai-test-go/internal/model/vo"
	"ai-test-go/internal/repository"
	"context"
	"fmt"
	"strconv"
	"time"

	"github.com/redis/go-redis/v9"
	"ai-test-go/pkg/logger"
)

const defaultAlertThreshold = 80

type BudgetService struct {
	redisClient           *redis.Client
	userRepo              *repository.UserRepository
	conversationMessageRepo *repository.ConversationMessageRepository
}

func NewBudgetService(
	redisClient *redis.Client,
	userRepo *repository.UserRepository,
	conversationMessageRepo *repository.ConversationMessageRepository,
) *BudgetService {
	return &BudgetService{
		redisClient:             redisClient,
		userRepo:                userRepo,
		conversationMessageRepo: conversationMessageRepo,
	}
}

func (s *BudgetService) CheckBudget(userID int64) vo.BudgetStatusVO {
	user, err := s.userRepo.FindByID(userID)
	if err != nil || user == nil {
		return vo.BudgetStatusNormalVO(0, 0, 0, 0)
	}

	dailyBudget := user.DailyBudget
	monthlyBudget := user.MonthlyBudget
	alertThreshold := user.BudgetAlertThreshold
	if alertThreshold <= 0 {
		alertThreshold = defaultAlertThreshold
	}

	todayCost := s.GetTodayCost(userID)
	monthCost := s.GetMonthCost(userID)

	if dailyBudget > 0 {
		dailyUsagePercent := (todayCost / dailyBudget) * 100
		if todayCost >= dailyBudget {
			status := vo.BudgetStatusExceededVO(
				fmt.Sprintf("今日预算已用完（%.2f / %.2f USD）", todayCost, dailyBudget),
				todayCost, monthCost, dailyBudget, monthlyBudget)
			status.DailyUsagePercent = dailyUsagePercent
			return status
		}
		if dailyUsagePercent >= float64(alertThreshold) {
			status := vo.BudgetStatusWarningVO(
				fmt.Sprintf("今日预算已使用 %.0f%%（%.2f / %.2f USD）", dailyUsagePercent, todayCost, dailyBudget),
				todayCost, monthCost, dailyBudget, monthlyBudget)
			status.DailyUsagePercent = dailyUsagePercent
			s.setMonthlyUsagePercent(&status, monthCost, monthlyBudget)
			return status
		}
	}

	if monthlyBudget > 0 {
		monthlyUsagePercent := (monthCost / monthlyBudget) * 100
		if monthCost >= monthlyBudget {
			status := vo.BudgetStatusExceededVO(
				fmt.Sprintf("本月预算已用完（%.2f / %.2f USD）", monthCost, monthlyBudget),
				todayCost, monthCost, dailyBudget, monthlyBudget)
			status.MonthlyUsagePercent = monthlyUsagePercent
			return status
		}
		if monthlyUsagePercent >= float64(alertThreshold) {
			status := vo.BudgetStatusWarningVO(
				fmt.Sprintf("本月预算已使用 %.0f%%（%.2f / %.2f USD）", monthlyUsagePercent, monthCost, monthlyBudget),
				todayCost, monthCost, dailyBudget, monthlyBudget)
			status.MonthlyUsagePercent = monthlyUsagePercent
			return status
		}
	}

	status := vo.BudgetStatusNormalVO(todayCost, monthCost, dailyBudget, monthlyBudget)
	s.calculateUsagePercent(&status, todayCost, monthCost, dailyBudget, monthlyBudget)
	return status
}

func (s *BudgetService) AddCost(userID int64, cost float64) {
	if cost <= 0 {
		return
	}

	ctx := context.Background()
	dailyKey := s.getDailyKey(userID)
	monthlyKey := s.getMonthlyKey(userID)

	if s.redisClient == nil {
		return
	}

	pipe := s.redisClient.Pipeline()
	pipe.IncrByFloat(ctx, dailyKey, cost)
	pipe.IncrByFloat(ctx, monthlyKey, cost)
	pipe.Expire(ctx, dailyKey, constant.UserDailyCostTTLHours*time.Hour)
	pipe.Expire(ctx, monthlyKey, constant.UserMonthlyCostTTLDays*24*time.Hour)
	_, err := pipe.Exec(ctx)
	if err != nil {
		logger.Log.Warnf("用户 %d 消耗累加失败: %v", userID, err)
		return
	}

	logger.Log.Debugf("用户 %d 消耗累加成功，本次: %f", userID, cost)
}

func (s *BudgetService) GetTodayCost(userID int64) float64 {
	if s.redisClient == nil {
		todayCost, _ := s.conversationMessageRepo.GetTodayCostByUserID(userID)
		return todayCost
	}

	ctx := context.Background()
	key := s.getDailyKey(userID)
	val, err := s.redisClient.Get(ctx, key).Result()
	if err == redis.Nil {
		s.SyncCostFromDB(userID)
		val, err = s.redisClient.Get(ctx, key).Result()
	}
	if err != nil {
		todayCost, _ := s.conversationMessageRepo.GetTodayCostByUserID(userID)
		return todayCost
	}

	cost, _ := strconv.ParseFloat(val, 64)
	return cost
}

func (s *BudgetService) GetMonthCost(userID int64) float64 {
	if s.redisClient == nil {
		monthCost, _ := s.conversationMessageRepo.GetMonthCostByUserID(userID)
		return monthCost
	}

	ctx := context.Background()
	key := s.getMonthlyKey(userID)
	val, err := s.redisClient.Get(ctx, key).Result()
	if err == redis.Nil {
		s.SyncCostFromDB(userID)
		val, err = s.redisClient.Get(ctx, key).Result()
	}
	if err != nil {
		monthCost, _ := s.conversationMessageRepo.GetMonthCostByUserID(userID)
		return monthCost
	}

	cost, _ := strconv.ParseFloat(val, 64)
	return cost
}

func (s *BudgetService) SyncCostFromDB(userID int64) {
	if s.redisClient == nil {
		return
	}

	todayCost, errToday := s.conversationMessageRepo.GetTodayCostByUserID(userID)
	monthCost, errMonth := s.conversationMessageRepo.GetMonthCostByUserID(userID)
	if errToday != nil && errMonth != nil {
		logger.Log.Warnf("用户 %d 消耗数据同步失败", userID)
		return
	}

	ctx := context.Background()
	dailyKey := s.getDailyKey(userID)
	monthlyKey := s.getMonthlyKey(userID)

	if todayCost > 0 {
		s.redisClient.Set(ctx, dailyKey, fmt.Sprintf("%f", todayCost), constant.UserDailyCostTTLHours*time.Hour)
	}
	if monthCost > 0 {
		s.redisClient.Set(ctx, monthlyKey, fmt.Sprintf("%f", monthCost), constant.UserMonthlyCostTTLDays*24*time.Hour)
	}

	logger.Log.Debugf("用户 %d 消耗数据同步成功，今日: %f, 本月: %f", userID, todayCost, monthCost)
}

func (s *BudgetService) getDailyKey(userID int64) string {
	return constant.UserDailyCostKeyPrefix + strconv.FormatInt(userID, 10)
}

func (s *BudgetService) getMonthlyKey(userID int64) string {
	yearMonth := time.Now().Format("200601")
	return constant.UserMonthlyCostKeyPrefix + strconv.FormatInt(userID, 10) + ":" + yearMonth
}

func (s *BudgetService) setMonthlyUsagePercent(status *vo.BudgetStatusVO, monthCost, monthlyBudget float64) {
	if monthlyBudget > 0 {
		status.MonthlyUsagePercent = (monthCost / monthlyBudget) * 100
	}
}

func (s *BudgetService) calculateUsagePercent(status *vo.BudgetStatusVO, todayCost, monthCost, dailyBudget, monthlyBudget float64) {
	if dailyBudget > 0 {
		status.DailyUsagePercent = (todayCost / dailyBudget) * 100
	}
	if monthlyBudget > 0 {
		status.MonthlyUsagePercent = (monthCost / monthlyBudget) * 100
	}
}

