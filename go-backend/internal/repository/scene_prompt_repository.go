// Package repository 场景提示词数据访问层
// @author <a href="https://codefather.cn">编程导航学习圈</a>
package repository

import (
	"ai-test-go/internal/model"

	"gorm.io/gorm"
)

type ScenePromptRepository struct {
	db *gorm.DB
}

func NewScenePromptRepository(db *gorm.DB) *ScenePromptRepository {
	return &ScenePromptRepository{db: db}
}

func (r *ScenePromptRepository) Create(prompt *model.ScenePrompt) error {
	return r.db.Create(prompt).Error
}

func (r *ScenePromptRepository) FindByID(id string) (*model.ScenePrompt, error) {
	var prompt model.ScenePrompt
	err := r.db.Where("id = ? AND isDelete = 0", id).First(&prompt).Error
	if err != nil {
		return nil, err
	}
	return &prompt, nil
}

func (r *ScenePromptRepository) Update(prompt *model.ScenePrompt) error {
	return r.db.Save(prompt).Error
}

func (r *ScenePromptRepository) UpdateByID(id string, updates map[string]interface{}) error {
	return r.db.Model(&model.ScenePrompt{}).Where("id = ?", id).Updates(updates).Error
}

func (r *ScenePromptRepository) Delete(id string) error {
	return r.db.Model(&model.ScenePrompt{}).Where("id = ?", id).Update("isDelete", 1).Error
}

func (r *ScenePromptRepository) ListBySceneID(sceneID string) ([]model.ScenePrompt, error) {
	var prompts []model.ScenePrompt
	err := r.db.Where("sceneId = ? AND isDelete = 0", sceneID).
		Order("promptIndex ASC").
		Find(&prompts).Error
	return prompts, err
}

func (r *ScenePromptRepository) CountBySceneID(sceneID string) (int64, error) {
	var count int64
	err := r.db.Model(&model.ScenePrompt{}).
		Where("sceneId = ? AND isDelete = 0", sceneID).
		Count(&count).Error
	return count, err
}

func (r *ScenePromptRepository) GetNextPromptIndex(sceneID string) (int, error) {
	var maxIndex int
	err := r.db.Model(&model.ScenePrompt{}).
		Where("sceneId = ? AND isDelete = 0", sceneID).
		Select("COALESCE(MAX(promptIndex), -1)").
		Scan(&maxIndex).Error
	if err != nil {
		return 0, err
	}
	return maxIndex + 1, nil
}

func (r *ScenePromptRepository) DeleteBySceneID(sceneID string) error {
	return r.db.Model(&model.ScenePrompt{}).
		Where("sceneId = ?", sceneID).
		Update("isDelete", 1).Error
}
