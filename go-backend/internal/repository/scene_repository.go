// Package repository 场景数据访问层
// @author <a href="https://codefather.cn">编程导航学习圈</a>
package repository

import (
	"ai-test-go/internal/model"

	"gorm.io/gorm"
)

type SceneRepository struct {
	db *gorm.DB
}

func NewSceneRepository(db *gorm.DB) *SceneRepository {
	return &SceneRepository{db: db}
}

func (r *SceneRepository) Create(scene *model.Scene) error {
	return r.db.Create(scene).Error
}

func (r *SceneRepository) FindByID(id string) (*model.Scene, error) {
	var scene model.Scene
	err := r.db.Where("id = ? AND isDelete = 0", id).First(&scene).Error
	if err != nil {
		return nil, err
	}
	return &scene, nil
}

func (r *SceneRepository) Update(scene *model.Scene) error {
	return r.db.Save(scene).Error
}

func (r *SceneRepository) UpdateByID(id string, updates map[string]interface{}) error {
	return r.db.Model(&model.Scene{}).Where("id = ?", id).Updates(updates).Error
}

func (r *SceneRepository) Delete(id string) error {
	return r.db.Model(&model.Scene{}).Where("id = ?", id).Update("isDelete", 1).Error
}

func (r *SceneRepository) ListByUserID(userID int64, includePreset bool) ([]model.Scene, error) {
	var scenes []model.Scene
	db := r.db.Where("isDelete = 0")

	if includePreset {
		db = db.Where("userId = ? OR isPreset = 1", userID)
	} else {
		db = db.Where("userId = ?", userID)
	}

	err := db.Order("createTime DESC").Find(&scenes).Error
	return scenes, err
}

func (r *SceneRepository) ListByCategory(category string) ([]model.Scene, error) {
	var scenes []model.Scene
	err := r.db.Where("category = ? AND isDelete = 0 AND isActive = 1", category).
		Order("createTime DESC").
		Find(&scenes).Error
	return scenes, err
}

func (r *SceneRepository) ListAll(includeInactive bool) ([]model.Scene, error) {
	var scenes []model.Scene
	db := r.db.Where("isDelete = 0")

	if !includeInactive {
		db = db.Where("isActive = 1")
	}

	err := db.Order("isPreset DESC, createTime DESC").Find(&scenes).Error
	return scenes, err
}

func (r *SceneRepository) ListByUserIDWithPage(userID int64, pageNum, pageSize int, category string, isPreset *int) ([]model.Scene, int64, error) {
	var scenes []model.Scene
	var total int64

	db := r.db.Model(&model.Scene{}).Where("isDelete = 0")
	db = db.Where("userId = ? OR isPreset = 1", userID)

	if category != "" {
		db = db.Where("category = ?", category)
	}

	if isPreset != nil {
		db = db.Where("isPreset = ?", *isPreset)
	}

	if err := db.Count(&total).Error; err != nil {
		return nil, 0, err
	}

	offset := (pageNum - 1) * pageSize
	err := db.Order("createTime DESC").
		Offset(offset).
		Limit(pageSize).
		Find(&scenes).Error

	return scenes, total, err
}
