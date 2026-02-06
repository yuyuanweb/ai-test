// Package repository 测试结果数据访问层
// @author <a href="https://codefather.cn">编程导航学习圈</a>
package repository

import (
	"ai-test-go/internal/model"

	"gorm.io/gorm"
)

type TestResultRepository struct {
	db *gorm.DB
}

func NewTestResultRepository(db *gorm.DB) *TestResultRepository {
	return &TestResultRepository{db: db}
}

func (r *TestResultRepository) Create(result *model.TestResult) error {
	return r.db.Create(result).Error
}

func (r *TestResultRepository) FindByID(id string) (*model.TestResult, error) {
	var result model.TestResult
	err := r.db.Where("id = ? AND isDelete = 0", id).First(&result).Error
	if err != nil {
		return nil, err
	}
	return &result, nil
}

func (r *TestResultRepository) Update(result *model.TestResult) error {
	return r.db.Save(result).Error
}

func (r *TestResultRepository) UpdateByID(id string, updates map[string]interface{}) error {
	return r.db.Model(&model.TestResult{}).Where("id = ?", id).Updates(updates).Error
}

func (r *TestResultRepository) Delete(id string) error {
	return r.db.Model(&model.TestResult{}).Where("id = ?", id).Update("isDelete", 1).Error
}

func (r *TestResultRepository) ListByTaskID(taskID string) ([]model.TestResult, error) {
	var results []model.TestResult
	err := r.db.Where("taskId = ? AND isDelete = 0", taskID).
		Order("createTime ASC").
		Find(&results).Error
	return results, err
}

func (r *TestResultRepository) CountByTaskID(taskID string) (int64, error) {
	var count int64
	err := r.db.Model(&model.TestResult{}).
		Where("taskId = ? AND isDelete = 0", taskID).
		Count(&count).Error
	return count, err
}
