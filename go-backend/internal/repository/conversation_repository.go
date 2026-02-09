// Package repository 对话数据访问层
// @author <a href="https://codefather.cn">编程导航学习圈</a>
package repository

import (
	"ai-test-go/internal/model"
	"time"

	"gorm.io/gorm"
)

type ConversationRepository struct {
	db *gorm.DB
}

func NewConversationRepository(db *gorm.DB) *ConversationRepository {
	return &ConversationRepository{db: db}
}

func (r *ConversationRepository) Create(conversation *model.Conversation) error {
	return r.db.Create(conversation).Error
}

func (r *ConversationRepository) FindByID(id string, userID int64) (*model.Conversation, error) {
	var conversation model.Conversation
	err := r.db.Where("id = ? AND userId = ? AND isDelete = 0", id, userID).First(&conversation).Error
	return &conversation, err
}

func (r *ConversationRepository) ListByUser(userID int64, pageNum, pageSize int64, codePreviewEnabled *bool) ([]model.Conversation, int64, error) {
	var conversations []model.Conversation
	var total int64

	db := r.db.Model(&model.Conversation{}).Where("userId = ? AND isDelete = 0", userID)

	if codePreviewEnabled != nil {
		db = db.Where("codePreviewEnabled = ?", *codePreviewEnabled)
	}

	if err := db.Count(&total).Error; err != nil {
		return nil, 0, err
	}

	offset := (pageNum - 1) * pageSize
	if err := db.Order("createTime DESC").Offset(int(offset)).Limit(int(pageSize)).Find(&conversations).Error; err != nil {
		return nil, 0, err
	}

	return conversations, total, nil
}

func (r *ConversationRepository) Delete(id string, userID int64) error {
	return r.db.Model(&model.Conversation{}).
		Where("id = ? AND userId = ?", id, userID).
		Update("isDelete", 1).Error
}

func (r *ConversationRepository) UpdateStats(id string, totalTokens int, totalCost float64) error {
	return r.db.Model(&model.Conversation{}).
		Where("id = ?", id).
		Updates(map[string]interface{}{
			"totalTokens": totalTokens,
			"totalCost":   totalCost,
		}).Error
}

// IncrementStats 累加会话的 token 和费用（用于图片生成等）
func (r *ConversationRepository) IncrementStats(id string, addTokens int, addCost float64) error {
	return r.db.Model(&model.Conversation{}).
		Where("id = ?", id).
		Updates(map[string]interface{}{
			"totalTokens": gorm.Expr("totalTokens + ?", addTokens),
			"totalCost":   gorm.Expr("totalCost + ?", addCost),
			"updateTime":  time.Now(),
		}).Error
}
