// Package repository 用户数据访问层
// @author <a href="https://codefather.cn">编程导航学习圈</a>
package repository

import (
	"ai-test-go/internal/model"

	"gorm.io/gorm"
)

type UserRepository struct {
	db *gorm.DB
}

func NewUserRepository(db *gorm.DB) *UserRepository {
	return &UserRepository{db: db}
}

func (r *UserRepository) Create(user *model.User) error {
	return r.db.Omit("EditTime").Create(user).Error
}

func (r *UserRepository) FindByID(id int64) (*model.User, error) {
	var user model.User
	err := r.db.Where("id = ? AND isDelete = 0", id).First(&user).Error
	if err != nil {
		return nil, err
	}
	return &user, nil
}

func (r *UserRepository) FindByAccount(userAccount string) (*model.User, error) {
	var user model.User
	err := r.db.Where("userAccount = ? AND isDelete = 0", userAccount).First(&user).Error
	if err != nil {
		return nil, err
	}
	return &user, nil
}

func (r *UserRepository) FindByAccountAndPassword(userAccount, userPassword string) (*model.User, error) {
	var user model.User
	err := r.db.Where("userAccount = ? AND userPassword = ? AND isDelete = 0", userAccount, userPassword).First(&user).Error
	if err != nil {
		return nil, err
	}
	return &user, nil
}

func (r *UserRepository) CountByAccount(userAccount string) (int64, error) {
	var count int64
	err := r.db.Model(&model.User{}).Where("userAccount = ? AND isDelete = 0", userAccount).Count(&count).Error
	return count, err
}

func (r *UserRepository) Update(user *model.User) error {
	return r.db.Save(user).Error
}

func (r *UserRepository) Delete(id int64) error {
	return r.db.Model(&model.User{}).Where("id = ?", id).Update("isDelete", 1).Error
}

func (r *UserRepository) UpdateByID(id int64, updates map[string]interface{}) error {
	return r.db.Model(&model.User{}).Where("id = ?", id).Updates(updates).Error
}

func (r *UserRepository) ListByPage(pageNum, pageSize int64, query map[string]interface{}) ([]model.User, int64, error) {
	var users []model.User
	var total int64

	db := r.db.Model(&model.User{}).Where("isDelete = 0")

	if id, ok := query["id"]; ok && id != nil {
		db = db.Where("id = ?", id)
	}
	if userAccount, ok := query["userAccount"]; ok && userAccount != "" {
		db = db.Where("userAccount LIKE ?", "%"+userAccount.(string)+"%")
	}
	if userName, ok := query["userName"]; ok && userName != "" {
		db = db.Where("userName LIKE ?", "%"+userName.(string)+"%")
	}
	if userProfile, ok := query["userProfile"]; ok && userProfile != "" {
		db = db.Where("userProfile LIKE ?", "%"+userProfile.(string)+"%")
	}
	if userRole, ok := query["userRole"]; ok && userRole != "" {
		db = db.Where("userRole = ?", userRole)
	}

	if err := db.Count(&total).Error; err != nil {
		return nil, 0, err
	}

	sortField, _ := query["sortField"].(string)
	sortOrder, _ := query["sortOrder"].(string)
	if sortField != "" {
		if sortOrder == "ascend" {
			db = db.Order(sortField + " ASC")
		} else {
			db = db.Order(sortField + " DESC")
		}
	} else {
		db = db.Order("createTime DESC")
	}

	offset := (pageNum - 1) * pageSize
	if err := db.Offset(int(offset)).Limit(int(pageSize)).Find(&users).Error; err != nil {
		return nil, 0, err
	}

	return users, total, nil
}
