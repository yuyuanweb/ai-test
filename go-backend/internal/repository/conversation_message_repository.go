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

func (r *ConversationMessageRepository) GetWeekCostByUserID(userID int64) (float64, error) {
	var cost float64
	now := time.Now()
	daysSinceMonday := int(now.Weekday() + 6) % 7
	weekStart := time.Date(now.Year(), now.Month(), now.Day(), 0, 0, 0, 0, time.Local).AddDate(0, 0, -daysSinceMonday)
	err := r.db.Model(&model.ConversationMessage{}).
		Where("userId = ? AND isDelete = 0 AND createTime >= ?", userID, weekStart).
		Select("COALESCE(SUM(cost), 0)").
		Scan(&cost).Error
	return cost, err
}

func (r *ConversationMessageRepository) GetTodayApiCallsByUserID(userID int64) (int64, error) {
	var count int64
	today := time.Now().Format("2006-01-02")
	err := r.db.Model(&model.ConversationMessage{}).
		Where("userId = ? AND isDelete = 0 AND role = ? AND DATE(createTime) = ?", userID, "assistant", today).
		Count(&count).Error
	return count, err
}

func (r *ConversationMessageRepository) GetTodayTokensByUserID(userID int64) (int64, error) {
	var total int64
	today := time.Now().Format("2006-01-02")
	err := r.db.Model(&model.ConversationMessage{}).
		Where("userId = ? AND isDelete = 0 AND DATE(createTime) = ?", userID, today).
		Select("COALESCE(SUM(COALESCE(inputTokens, 0) + COALESCE(outputTokens, 0)), 0)").
		Scan(&total).Error
	return total, err
}

type DailyCostRow struct {
	Date  string  `gorm:"column:dt"`
	Cost  float64 `gorm:"column:cost"`
}

func (r *ConversationMessageRepository) GetDailyCostsByUserIDAndDateRange(userID int64, start, end time.Time) ([]DailyCostRow, error) {
	var rows []DailyCostRow
	err := r.db.Model(&model.ConversationMessage{}).
		Select("DATE(createTime) AS dt, COALESCE(SUM(cost), 0) AS cost").
		Where("userId = ? AND isDelete = 0 AND createTime >= ? AND createTime < ?", userID, start, end).
		Group("DATE(createTime)").
		Order("dt ASC").
		Scan(&rows).Error
	return rows, err
}

func (r *ConversationMessageRepository) GetTotalApiCallsByUserID(userID int64) (int64, error) {
	var count int64
	err := r.db.Model(&model.ConversationMessage{}).
		Where("userId = ? AND isDelete = 0 AND role = ?", userID, "assistant").
		Count(&count).Error
	return count, err
}

func (r *ConversationMessageRepository) GetTotalInputTokensByUserID(userID int64) (int64, error) {
	var total int64
	err := r.db.Model(&model.ConversationMessage{}).
		Where("userId = ? AND isDelete = 0", userID).
		Select("COALESCE(SUM(inputTokens), 0)").
		Scan(&total).Error
	return total, err
}

func (r *ConversationMessageRepository) GetTotalOutputTokensByUserID(userID int64) (int64, error) {
	var total int64
	err := r.db.Model(&model.ConversationMessage{}).
		Where("userId = ? AND isDelete = 0", userID).
		Select("COALESCE(SUM(outputTokens), 0)").
		Scan(&total).Error
	return total, err
}

type DailyUsageRow struct {
	Date     string `gorm:"column:dt"`
	ApiCalls int64  `gorm:"column:api_calls"`
	Tokens   int64  `gorm:"column:tokens"`
}

func (r *ConversationMessageRepository) GetDailyUsageByUserIDAndDateRange(userID int64, start, end time.Time) ([]DailyUsageRow, error) {
	var rows []DailyUsageRow
	err := r.db.Model(&model.ConversationMessage{}).
		Select("DATE(createTime) AS dt, COUNT(*) AS api_calls, COALESCE(SUM(COALESCE(inputTokens, 0) + COALESCE(outputTokens, 0)), 0) AS tokens").
		Where("userId = ? AND isDelete = 0 AND role = ? AND createTime >= ? AND createTime < ?", userID, "assistant", start, end).
		Group("DATE(createTime)").
		Order("dt ASC").
		Scan(&rows).Error
	return rows, err
}

func (r *ConversationMessageRepository) ListAssistantMessagesByUserID(userID int64) ([]model.ConversationMessage, error) {
	var messages []model.ConversationMessage
	err := r.db.Where("userId = ? AND isDelete = 0 AND role = ?", userID, "assistant").
		Find(&messages).Error
	return messages, err
}
