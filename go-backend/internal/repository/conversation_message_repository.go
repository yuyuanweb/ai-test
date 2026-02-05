// Package repository 对话消息数据访问层
// @author <a href="https://codefather.cn">编程导航学习圈</a>
package repository

import (
	"ai-test-go/internal/model"

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

func (r *ConversationMessageRepository) ListByConversation(conversationID string) ([]model.ConversationMessage, error) {
	var messages []model.ConversationMessage
	err := r.db.Where("conversationId = ? AND isDelete = 0", conversationID).
		Order("messageIndex ASC").
		Find(&messages).Error
	return messages, err
}

func (r *ConversationMessageRepository) GetNextMessageIndex(conversationID string) (int, error) {
	var maxIndex int
	err := r.db.Model(&model.ConversationMessage{}).
		Where("conversationId = ?", conversationID).
		Select("COALESCE(MAX(messageIndex), -1)").
		Scan(&maxIndex).Error
	return maxIndex + 1, err
}

func (r *ConversationMessageRepository) BatchCreate(messages []*model.ConversationMessage) error {
	return r.db.Create(&messages).Error
}
