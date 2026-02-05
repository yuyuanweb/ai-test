// Package repository 模型数据访问层
// @author <a href="https://codefather.cn">编程导航学习圈</a>
package repository

import (
	"ai-test-go/internal/model"
	"ai-test-go/internal/model/dto"

	"gorm.io/gorm"
)

type ModelRepository struct {
	db *gorm.DB
}

func NewModelRepository(db *gorm.DB) *ModelRepository {
	return &ModelRepository{db: db}
}

func (r *ModelRepository) SaveOrUpdate(m *model.Model) error {
	var existing model.Model
	result := r.db.Where("id = ?", m.ID).First(&existing)

	if result.Error == gorm.ErrRecordNotFound {
		return r.db.Create(m).Error
	}

	m.CreateTime = existing.CreateTime
	return r.db.Model(&existing).Updates(m).Error
}

func (r *ModelRepository) FindByID(id string) (*model.Model, error) {
	var m model.Model
	err := r.db.Where("id = ? AND isDelete = 0", id).First(&m).Error
	return &m, err
}

func (r *ModelRepository) List(req *dto.ModelQueryRequest) ([]model.Model, int64, error) {
	var models []model.Model
	var total int64

	db := r.db.Model(&model.Model{}).Where("isDelete = 0")

	if req.SearchText != "" {
		db = db.Where("(id LIKE ? OR name LIKE ? OR description LIKE ?)",
			"%"+req.SearchText+"%", "%"+req.SearchText+"%", "%"+req.SearchText+"%")
	}

	if req.Provider != "" {
		db = db.Where("provider = ?", req.Provider)
	}

	if req.OnlyRecommended != nil && *req.OnlyRecommended {
		db = db.Where("recommended = 1")
	}

	if req.OnlyChina != nil && *req.OnlyChina {
		db = db.Where("isChina = 1")
	}

	if req.OnlySupportsImageGen != nil && *req.OnlySupportsImageGen {
		db = db.Where("supportsImageGen = 1")
	}

	if req.OnlySupportsMultimodal != nil && *req.OnlySupportsMultimodal {
		db = db.Where("supportsMultimodal = 1")
	}

	if err := db.Count(&total).Error; err != nil {
		return nil, 0, err
	}

	current := req.Current
	if current < 1 {
		current = 1
	}
	pageSize := req.PageSize
	if pageSize < 1 {
		pageSize = 10
	}

	offset := (current - 1) * pageSize

	db = db.Order("isChina DESC, recommended DESC, createTime DESC")

	if err := db.Offset(int(offset)).Limit(int(pageSize)).Find(&models).Error; err != nil {
		return nil, 0, err
	}

	return models, total, nil
}

func (r *ModelRepository) GetAll() ([]model.Model, error) {
	var models []model.Model
	err := r.db.Where("isDelete = 0").
		Order("isChina DESC, recommended DESC, createTime DESC").
		Find(&models).Error
	return models, err
}
