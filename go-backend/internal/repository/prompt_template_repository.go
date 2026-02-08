// Package repository 提示词模板数据访问层
// @author <a href="https://codefather.cn">编程导航学习圈</a>
package repository

import (
	"ai-test-go/internal/model"

	"gorm.io/gorm"
)

type PromptTemplateRepository struct {
	db *gorm.DB
}

func NewPromptTemplateRepository(db *gorm.DB) *PromptTemplateRepository {
	return &PromptTemplateRepository{db: db}
}

func (r *PromptTemplateRepository) Create(template *model.PromptTemplate) error {
	return r.db.Create(template).Error
}

func (r *PromptTemplateRepository) FindByID(id string) (*model.PromptTemplate, error) {
	var t model.PromptTemplate
	err := r.db.Where("id = ? AND isDelete = 0", id).First(&t).Error
	if err != nil {
		return nil, err
	}
	return &t, nil
}

func (r *PromptTemplateRepository) Update(template *model.PromptTemplate) error {
	return r.db.Save(template).Error
}

func (r *PromptTemplateRepository) Delete(id string) error {
	return r.db.Model(&model.PromptTemplate{}).Where("id = ?", id).Update("isDelete", 1).Error
}

func (r *PromptTemplateRepository) ListByUserIDAndStrategy(userID int64, strategy string) ([]model.PromptTemplate, error) {
	var list []model.PromptTemplate
	db := r.db.Where("isDelete = 0 AND isActive = 1")
	if strategy != "" {
		db = db.Where("strategy = ?", strategy)
	}
	db = db.Where("(isPreset = 1 OR userId = ?)", userID)
	err := db.Order("isPreset DESC, usageCount DESC, createTime DESC").Find(&list).Error
	return list, err
}

func (r *PromptTemplateRepository) IncrementUsageCount(id string) error {
	return r.db.Model(&model.PromptTemplate{}).
		Where("id = ? AND isDelete = 0", id).
		UpdateColumn("usageCount", gorm.Expr("usageCount + 1")).
		Error
}
