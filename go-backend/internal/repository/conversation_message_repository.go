// Package repository 对话消息数据访问层
// @author <a href="https://codefather.cn">编程导航学习圈</a>
package repository

import (
	"ai-test-go/internal/model"
	"time"

	"gorm.io/gorm"
)

type ConversationMessageRepository struct {
	db *gorm.DB
}

func NewConversationMessageRepository(db *gorm.DB) *ConversationMessageRepository {
	return &ConversationMessageRepository{db: db}
}

func (r *ConversationMessageRepository) Create(message *model.ConversationMessage) error {
	return r.db.Create(message).Error
}

func (r *ConversationMessageRepository) FindByID(id string) (*model.ConversationMessage, error) {
	var message model.ConversationMessage
	err := r.db.Where("id = ? AND isDelete = 0", id).First(&message).Error
	if err != nil {
		return nil, err
	}
	return &message, nil
}

func (r *ConversationMessageRepository) ListByConversationID(conversationID string) ([]model.ConversationMessage, error) {
	var messages []model.ConversationMessage
	err := r.db.Where("conversationId = ? AND isDelete = 0", conversationID).
		Order("messageIndex ASC").
		Find(&messages).Error
	return messages, err
}

func (r *ConversationMessageRepository) ListByConversation(conversationID string) ([]model.ConversationMessage, error) {
	return r.ListByConversationID(conversationID)
}

func (r *ConversationMessageRepository) GetNextMessageIndex(conversationID string) (int, error) {
	var maxIndex int
	err := r.db.Model(&model.ConversationMessage{}).
		Where("conversationId = ? AND isDelete = 0", conversationID).
		Select("COALESCE(MAX(messageIndex), -1)").
		Scan(&maxIndex).Error
	if err != nil {
		return 0, err
	}
	return maxIndex + 1, nil
}

func (r *ConversationMessageRepository) DeleteByConversationID(conversationID string) error {
	return r.db.Model(&model.ConversationMessage{}).
		Where("conversationId = ?", conversationID).
		Update("isDelete", 1).Error
}

func (r *ConversationMessageRepository) GetTodayCostByUserID(userID int64) (float64, error) {
	var cost float64
	today := time.Now().Format("2006-01-02")
	err := r.db.Model(&model.ConversationMessage{}).
		Where("userId = ? AND isDelete = 0 AND DATE(createTime) = ?", userID, today).
		Select("COALESCE(SUM(cost), 0)").
		Scan(&cost).Error
	return cost, err
}

func (r *ConversationMessageRepository) GetMonthCostByUserID(userID int64) (float64, error) {
	var cost float64
	year, month, _ := time.Now().Date()
	firstDay := time.Date(year, month, 1, 0, 0, 0, 0, time.Local)
	err := r.db.Model(&model.ConversationMessage{}).
		Where("userId = ? AND isDelete = 0 AND createTime >= ?", userID, firstDay).
		Select("COALESCE(SUM(cost), 0)").
		Scan(&cost).Error
	return cost, err
}
