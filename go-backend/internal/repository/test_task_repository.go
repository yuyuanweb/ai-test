// Package repository 测试任务数据访问层
// @author <a href="https://codefather.cn">编程导航学习圈</a>
package repository

import (
	"ai-test-go/internal/model"

	"gorm.io/gorm"
)

type TestTaskRepository struct {
	db *gorm.DB
}

func NewTestTaskRepository(db *gorm.DB) *TestTaskRepository {
	return &TestTaskRepository{db: db}
}

func (r *TestTaskRepository) Create(task *model.TestTask) error {
	return r.db.Create(task).Error
}

func (r *TestTaskRepository) FindByID(id string) (*model.TestTask, error) {
	var task model.TestTask
	err := r.db.Where("id = ? AND isDelete = 0", id).First(&task).Error
	if err != nil {
		return nil, err
	}
	return &task, nil
}

func (r *TestTaskRepository) Update(task *model.TestTask) error {
	return r.db.Save(task).Error
}

func (r *TestTaskRepository) UpdateByID(id string, updates map[string]interface{}) error {
	return r.db.Model(&model.TestTask{}).Where("id = ?", id).Updates(updates).Error
}

func (r *TestTaskRepository) Delete(id string) error {
	return r.db.Model(&model.TestTask{}).Where("id = ?", id).Update("isDelete", 1).Error
}

func (r *TestTaskRepository) ListByUserID(userID int64, pageNum, pageSize int64) ([]model.TestTask, int64, error) {
	var tasks []model.TestTask
	var total int64

	db := r.db.Model(&model.TestTask{}).Where("userId = ? AND isDelete = 0", userID)

	if err := db.Count(&total).Error; err != nil {
		return nil, 0, err
	}

	offset := (pageNum - 1) * pageSize
	err := db.Order("createTime DESC").Offset(int(offset)).Limit(int(pageSize)).Find(&tasks).Error
	if err != nil {
		return nil, 0, err
	}

	return tasks, total, nil
}

func (r *TestTaskRepository) IncrementCompletedSubtasks(taskID string) error {
	return r.db.Model(&model.TestTask{}).
		Where("id = ?", taskID).
		UpdateColumn("completedSubtasks", gorm.Expr("completedSubtasks + ?", 1)).Error
}

func (r *TestTaskRepository) UpdateStatus(taskID string, status string) error {
	updates := map[string]interface{}{
		"status": status,
	}
	if status == "running" {
		updates["startedAt"] = gorm.Expr("NOW()")
	} else if status == "completed" || status == "failed" {
		updates["completedAt"] = gorm.Expr("NOW()")
	}
	return r.db.Model(&model.TestTask{}).Where("id = ?", taskID).Updates(updates).Error
}
