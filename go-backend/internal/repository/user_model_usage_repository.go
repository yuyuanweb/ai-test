// Package repository 用户模型使用统计数据访问层
// @author <a href="https://codefather.cn">编程导航学习圈</a>
package repository

import (
	"ai-test-go/internal/model"

	"gorm.io/gorm"
)

type UserModelUsageRepository struct {
	db *gorm.DB
}

func NewUserModelUsageRepository(db *gorm.DB) *UserModelUsageRepository {
	return &UserModelUsageRepository{db: db}
}

func (r *UserModelUsageRepository) Create(usage *model.UserModelUsage) error {
	return r.db.Create(usage).Error
}

func (r *UserModelUsageRepository) FindByUserIDAndModel(userID int64, modelName string) (*model.UserModelUsage, error) {
	var usage model.UserModelUsage
	err := r.db.Where("userId = ? AND modelName = ? AND isDelete = 0", userID, modelName).First(&usage).Error
	if err != nil {
		return nil, err
	}
	return &usage, nil
}

func (r *UserModelUsageRepository) IncrementUsage(userID int64, modelName string, tokens int64, cost float64) error {
	return r.db.Exec(`
		INSERT INTO user_model_usage (id, userId, modelName, totalTokens, totalCost, createTime, updateTime, isDelete)
		VALUES (UUID(), ?, ?, ?, ?, NOW(), NOW(), 0)
		ON DUPLICATE KEY UPDATE
			totalTokens = totalTokens + VALUES(totalTokens),
			totalCost = totalCost + VALUES(totalCost),
			updateTime = NOW()
	`, userID, modelName, tokens, cost).Error
}

func (r *UserModelUsageRepository) ListByUserID(userID int64) ([]model.UserModelUsage, error) {
	var usages []model.UserModelUsage
	err := r.db.Where("userId = ? AND isDelete = 0", userID).
		Order("totalCost DESC").
		Find(&usages).Error
	return usages, err
}
