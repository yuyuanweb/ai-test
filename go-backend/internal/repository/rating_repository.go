// Package repository 评分数据访问层
// @author <a href="https://codefather.cn">编程导航学习圈</a>
package repository

import (
	"ai-test-go/internal/model"

	"gorm.io/gorm"
)

type RatingRepository struct {
	db *gorm.DB
}

func NewRatingRepository(db *gorm.DB) *RatingRepository {
	return &RatingRepository{db: db}
}

func (r *RatingRepository) SaveOrUpdate(rating *model.Rating) error {
	var existing model.Rating
	result := r.db.Where("conversationId = ? AND messageIndex = ? AND userId = ? AND isDelete = 0",
		rating.ConversationID, rating.MessageIndex, rating.UserID).First(&existing)

	if result.Error == gorm.ErrRecordNotFound {
		return r.db.Create(rating).Error
	}

	return r.db.Model(&existing).Updates(map[string]interface{}{
		"ratingType":         rating.RatingType,
		"winnerModel":        rating.WinnerModel,
		"loserModel":         rating.LoserModel,
		"winnerVariantIndex": rating.WinnerVariantIndex,
		"loserVariantIndex":  rating.LoserVariantIndex,
		"updateTime":         rating.UpdateTime,
	}).Error
}

func (r *RatingRepository) FindByConversationAndMessage(conversationID string, messageIndex int, userID int64) (*model.Rating, error) {
	var rating model.Rating
	err := r.db.Where("conversationId = ? AND messageIndex = ? AND userId = ? AND isDelete = 0",
		conversationID, messageIndex, userID).First(&rating).Error
	return &rating, err
}

func (r *RatingRepository) FindByConversation(conversationID string, userID int64) ([]model.Rating, error) {
	var ratings []model.Rating
	err := r.db.Where("conversationId = ? AND userId = ? AND isDelete = 0", conversationID, userID).
		Order("messageIndex ASC").
		Find(&ratings).Error
	return ratings, err
}

func (r *RatingRepository) Delete(conversationID string, messageIndex int, userID int64) error {
	return r.db.Model(&model.Rating{}).
		Where("conversationId = ? AND messageIndex = ? AND userId = ?", conversationID, messageIndex, userID).
		Update("isDelete", 1).Error
}
