// Package service 批量测试服务层
// @author <a href="https://codefather.cn">编程导航学习圈</a>
package service

import (
	"ai-test-go/internal/model"
	"ai-test-go/internal/model/dto"
	"ai-test-go/internal/repository"
	"ai-test-go/pkg/common"
	"ai-test-go/pkg/logger"
	"ai-test-go/pkg/rabbitmq"
	"ai-test-go/pkg/utils"
	"context"
	"encoding/json"
	"errors"
	"fmt"

	"gorm.io/gorm"
)

type BatchTestService struct {
	testTaskRepo    *repository.TestTaskRepository
	testResultRepo  *repository.TestResultRepository
	sceneRepo       *repository.SceneRepository
	scenePromptRepo *repository.ScenePromptRepository
	rabbitMQClient  *rabbitmq.RabbitMQClient
	db              *gorm.DB
}

func NewBatchTestService(
	testTaskRepo *repository.TestTaskRepository,
	testResultRepo *repository.TestResultRepository,
	sceneRepo *repository.SceneRepository,
	scenePromptRepo *repository.ScenePromptRepository,
	rabbitMQClient *rabbitmq.RabbitMQClient,
	db *gorm.DB,
) *BatchTestService {
	return &BatchTestService{
		testTaskRepo:    testTaskRepo,
		testResultRepo:  testResultRepo,
		sceneRepo:       sceneRepo,
		scenePromptRepo: scenePromptRepo,
		rabbitMQClient:  rabbitMQClient,
		db:              db,
	}
}

func (s *BatchTestService) CreateBatchTestTask(req *dto.CreateBatchTestRequest, userID int64) (string, error) {
	if req.SceneID == "" {
		return "", common.NewBusinessException(common.PARAMS_ERROR, "场景ID不能为空")
	}

	if len(req.Models) == 0 {
		return "", common.NewBusinessException(common.PARAMS_ERROR, "模型列表不能为空")
	}

	scene, err := s.sceneRepo.FindByID(req.SceneID)
	if err != nil {
		if errors.Is(err, gorm.ErrRecordNotFound) {
			return "", common.NewBusinessException(common.NOT_FOUND_ERROR, "场景不存在")
		}
		return "", common.NewBusinessException(common.SYSTEM_ERROR, "查询场景失败")
	}

	if scene.IsPreset == 0 && (scene.UserID == nil || *scene.UserID != userID) {
		return "", common.NewBusinessException(common.NO_AUTH_ERROR, "无权限使用该场景")
	}

	prompts, err := s.scenePromptRepo.ListBySceneID(req.SceneID)
	if err != nil {
		return "", common.NewBusinessException(common.SYSTEM_ERROR, "查询提示词失败")
	}

	if len(prompts) == 0 {
		return "", common.NewBusinessException(common.PARAMS_ERROR, "场景中没有提示词")
	}

	taskID := utils.GenerateUUID()
	totalSubtasks := len(req.Models) * len(prompts)

	configMap := make(map[string]interface{})
	if req.Temperature != nil {
		configMap["temperature"] = *req.Temperature
	}
	if req.TopP != nil {
		configMap["topP"] = *req.TopP
	}
	if req.MaxTokens != nil {
		configMap["maxTokens"] = *req.MaxTokens
	}
	if req.TopK != nil {
		configMap["topK"] = *req.TopK
	}
	if req.FrequencyPenalty != nil {
		configMap["frequencyPenalty"] = *req.FrequencyPenalty
	}
	if req.PresencePenalty != nil {
		configMap["presencePenalty"] = *req.PresencePenalty
	}
	if req.EnableAiScoring != nil {
		configMap["enableAiScoring"] = *req.EnableAiScoring
	}

	var configJSON string
	if len(configMap) > 0 {
		configBytes, _ := json.Marshal(configMap)
		configJSON = string(configBytes)
	} else {
		configJSON = "{}"
	}

	modelsJSON, _ := json.Marshal(req.Models)

	testTask := &model.TestTask{
		ID:                taskID,
		UserID:            userID,
		Name:              req.Name,
		SceneID:           req.SceneID,
		Models:            string(modelsJSON),
		Config:            configJSON,
		Status:            "pending",
		TotalSubtasks:     totalSubtasks,
		CompletedSubtasks: 0,
		IsDelete:          0,
	}

	tx := s.db.Begin()
	defer func() {
		if r := recover(); r != nil {
			tx.Rollback()
		}
	}()

	if err := s.testTaskRepo.Create(testTask); err != nil {
		tx.Rollback()
		return "", common.NewBusinessException(common.OPERATION_ERROR, "创建测试任务失败")
	}

	if err := tx.Commit().Error; err != nil {
		return "", common.NewBusinessException(common.OPERATION_ERROR, "提交事务失败")
	}

	if s.rabbitMQClient != nil {
		priority := rabbitmq.CalculatePriority(totalSubtasks)

		for _, modelName := range req.Models {
			for _, prompt := range prompts {
				subTask := dto.SubTaskMessage{
					TaskID:        taskID,
					SceneID:       req.SceneID,
					PromptID:      prompt.ID,
					PromptTitle:   prompt.Title,
					PromptContent: prompt.Content,
					ModelName:     modelName,
					UserID:        userID,
					Config:        configJSON,
				}

				if err := s.rabbitMQClient.PublishMessage(context.Background(), subTask, priority); err != nil {
					fmt.Printf("发送子任务到队列失败: taskId=%s, error=%v\n", taskID, err)
				}
			}
		}
	}

	return taskID, nil
}

func (s *BatchTestService) GetTask(taskID string, userID int64) (*model.TestTask, error) {
	task, err := s.testTaskRepo.FindByID(taskID)
	if err != nil {
		if errors.Is(err, gorm.ErrRecordNotFound) {
			return nil, common.NewBusinessException(common.NOT_FOUND_ERROR, "任务不存在")
		}
		return nil, common.NewBusinessException(common.SYSTEM_ERROR, "查询任务失败")
	}

	if task.UserID != userID {
		return nil, common.NewBusinessException(common.NO_AUTH_ERROR, "无权限查看")
	}

	return task, nil
}

func (s *BatchTestService) ListTasks(userID int64, req *dto.TaskQueryRequest) ([]model.TestTask, int64, error) {
	if req.PageNum <= 0 {
		req.PageNum = 1
	}
	if req.PageSize <= 0 {
		req.PageSize = 10
	}

	tasks, total, err := s.testTaskRepo.ListByUserID(userID, req.PageNum, req.PageSize)
	if err != nil {
		return nil, 0, common.NewBusinessException(common.SYSTEM_ERROR, "查询任务列表失败")
	}

	return tasks, total, nil
}

func (s *BatchTestService) DeleteTask(taskID string, userID int64) error {
	task, err := s.testTaskRepo.FindByID(taskID)
	if err != nil {
		if errors.Is(err, gorm.ErrRecordNotFound) {
			return common.NewBusinessException(common.NOT_FOUND_ERROR, "任务不存在")
		}
		return common.NewBusinessException(common.SYSTEM_ERROR, "查询任务失败")
	}

	if task.UserID != userID {
		return common.NewBusinessException(common.NO_AUTH_ERROR, "无权限操作")
	}

	if err := s.testTaskRepo.Delete(taskID); err != nil {
		return common.NewBusinessException(common.OPERATION_ERROR, "删除任务失败")
	}

	return nil
}

func (s *BatchTestService) GetTaskResults(taskID string, userID int64) ([]model.TestResult, error) {
	task, err := s.testTaskRepo.FindByID(taskID)
	if err != nil {
		if errors.Is(err, gorm.ErrRecordNotFound) {
			return nil, common.NewBusinessException(common.NOT_FOUND_ERROR, "任务不存在")
		}
		return nil, common.NewBusinessException(common.SYSTEM_ERROR, "查询任务失败")
	}

	if task.UserID != userID {
		return nil, common.NewBusinessException(common.NO_AUTH_ERROR, "无权限查看")
	}

	results, err := s.testResultRepo.ListByTaskID(taskID)
	if err != nil {
		return nil, common.NewBusinessException(common.SYSTEM_ERROR, "查询测试结果失败")
	}

	return results, nil
}

func (s *BatchTestService) UpdateTaskProgress(taskID string) {
	if err := s.testTaskRepo.IncrementCompletedSubtasks(taskID); err != nil {
		logger.Log.Errorf("更新任务进度失败: taskId=%s, error=%v", taskID, err)
		return
	}

	task, err := s.testTaskRepo.FindByID(taskID)
	if err != nil {
		logger.Log.Errorf("查询任务失败: taskId=%s, error=%v", taskID, err)
		return
	}

	if task.CompletedSubtasks >= task.TotalSubtasks {
		s.testTaskRepo.UpdateStatus(taskID, "completed")
		logger.Log.Infof("批量测试任务完成: taskId=%s", taskID)
	} else if task.Status == "pending" {
		s.testTaskRepo.UpdateStatus(taskID, "running")
	}
}

func (s *BatchTestService) MarkTaskFailed(taskID string) error {
	return s.testTaskRepo.UpdateStatus(taskID, "failed")
}

func (s *BatchTestService) UpdateTestResultRating(resultID string, userRating int, userID int64) error {
	result, err := s.testResultRepo.FindByID(resultID)
	if err != nil {
		if errors.Is(err, gorm.ErrRecordNotFound) {
			return common.NewBusinessException(common.NOT_FOUND_ERROR, "测试结果不存在")
		}
		return common.NewBusinessException(common.SYSTEM_ERROR, "查询测试结果失败")
	}

	if result.UserID != userID {
		return common.NewBusinessException(common.NO_AUTH_ERROR, "无权限操作")
	}

	updates := map[string]interface{}{
		"userRating": userRating,
	}

	if err := s.testResultRepo.UpdateByID(resultID, updates); err != nil {
		return common.NewBusinessException(common.OPERATION_ERROR, "更新评分失败")
	}

	return nil
}
