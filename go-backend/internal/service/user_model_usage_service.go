// Package service 用户模型使用统计服务层
// @author <a href="https://codefather.cn">编程导航学习圈</a>
package service

import (
	"ai-test-go/internal/model"
	"ai-test-go/internal/repository"
	"ai-test-go/pkg/common"
)

type UserModelUsageService struct {
	userModelUsageRepo *repository.UserModelUsageRepository
}

func NewUserModelUsageService(userModelUsageRepo *repository.UserModelUsageRepository) *UserModelUsageService {
	return &UserModelUsageService{
		userModelUsageRepo: userModelUsageRepo,
	}
}

func (s *UserModelUsageService) UpdateUserModelUsage(userID int64, modelName string, tokens int64, cost float64) error {
	return s.userModelUsageRepo.IncrementUsage(userID, modelName, tokens, cost)
}

func (s *UserModelUsageService) ListByUserID(userID int64) ([]model.UserModelUsage, error) {
	usages, err := s.userModelUsageRepo.ListByUserID(userID)
	if err != nil {
		return nil, common.NewBusinessException(common.SYSTEM_ERROR, "查询用户模型使用统计失败")
	}
	return usages, nil
}
