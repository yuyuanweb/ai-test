// Package service 场景服务层
// @author <a href="https://codefather.cn">编程导航学习圈</a>
package service

import (
	"ai-test-go/internal/model"
	"ai-test-go/internal/model/dto"
	"ai-test-go/internal/repository"
	"ai-test-go/pkg/common"
	"ai-test-go/pkg/utils"
	"errors"

	"gorm.io/gorm"
)

type SceneService struct {
	sceneRepo       *repository.SceneRepository
	scenePromptRepo *repository.ScenePromptRepository
	db              *gorm.DB
}

func NewSceneService(
	sceneRepo *repository.SceneRepository,
	scenePromptRepo *repository.ScenePromptRepository,
	db *gorm.DB,
) *SceneService {
	return &SceneService{
		sceneRepo:       sceneRepo,
		scenePromptRepo: scenePromptRepo,
		db:              db,
	}
}

func (s *SceneService) CreateScene(req *dto.CreateSceneRequest, userID int64) (string, error) {
	if req.Name == "" {
		return "", common.NewBusinessException(common.PARAMS_ERROR, "场景名称不能为空")
	}

	sceneID := utils.GenerateUUID()
	scene := &model.Scene{
		ID:          sceneID,
		UserID:      &userID,
		Name:        req.Name,
		Description: req.Description,
		Category:    req.Category,
		IsPreset:    0,
		IsActive:    1,
		IsDelete:    0,
	}

	if err := s.sceneRepo.Create(scene); err != nil {
		return "", common.NewBusinessException(common.OPERATION_ERROR, "创建场景失败")
	}

	return sceneID, nil
}

func (s *SceneService) UpdateScene(req *dto.UpdateSceneRequest, userID int64) error {
	if req.ID == "" {
		return common.NewBusinessException(common.PARAMS_ERROR, "场景ID不能为空")
	}

	existingScene, err := s.sceneRepo.FindByID(req.ID)
	if err != nil {
		if errors.Is(err, gorm.ErrRecordNotFound) {
			return common.NewBusinessException(common.NOT_FOUND_ERROR, "场景不存在")
		}
		return common.NewBusinessException(common.SYSTEM_ERROR, "查询场景失败")
	}

	if existingScene.UserID == nil || *existingScene.UserID != userID {
		return common.NewBusinessException(common.NO_AUTH_ERROR, "无权限操作")
	}

	updates := make(map[string]interface{})
	if req.Name != "" {
		updates["name"] = req.Name
	}
	if req.Description != "" {
		updates["description"] = req.Description
	}
	if req.Category != "" {
		updates["category"] = req.Category
	}
	if req.IsActive != nil {
		updates["isActive"] = *req.IsActive
	}

	if len(updates) == 0 {
		return common.NewBusinessException(common.PARAMS_ERROR, "没有需要更新的字段")
	}

	if err := s.sceneRepo.UpdateByID(req.ID, updates); err != nil {
		return common.NewBusinessException(common.OPERATION_ERROR, "更新场景失败")
	}

	return nil
}

func (s *SceneService) DeleteScene(sceneID string, userID int64) error {
	scene, err := s.sceneRepo.FindByID(sceneID)
	if err != nil {
		if errors.Is(err, gorm.ErrRecordNotFound) {
			return common.NewBusinessException(common.NOT_FOUND_ERROR, "场景不存在")
		}
		return common.NewBusinessException(common.SYSTEM_ERROR, "查询场景失败")
	}

	if scene.UserID == nil || *scene.UserID != userID {
		return common.NewBusinessException(common.NO_AUTH_ERROR, "无权限操作")
	}

	tx := s.db.Begin()
	defer func() {
		if r := recover(); r != nil {
			tx.Rollback()
		}
	}()

	if err := s.sceneRepo.Delete(sceneID); err != nil {
		tx.Rollback()
		return common.NewBusinessException(common.OPERATION_ERROR, "删除场景失败")
	}

	if err := s.scenePromptRepo.DeleteBySceneID(sceneID); err != nil {
		tx.Rollback()
		return common.NewBusinessException(common.OPERATION_ERROR, "删除场景提示词失败")
	}

	if err := tx.Commit().Error; err != nil {
		return common.NewBusinessException(common.OPERATION_ERROR, "提交事务失败")
	}

	return nil
}

func (s *SceneService) GetScene(sceneID string, userID int64) (*model.Scene, error) {
	scene, err := s.sceneRepo.FindByID(sceneID)
	if err != nil {
		if errors.Is(err, gorm.ErrRecordNotFound) {
			return nil, common.NewBusinessException(common.NOT_FOUND_ERROR, "场景不存在")
		}
		return nil, common.NewBusinessException(common.SYSTEM_ERROR, "查询场景失败")
	}

	if scene.IsPreset == 0 && (scene.UserID == nil || *scene.UserID != userID) {
		return nil, common.NewBusinessException(common.NO_AUTH_ERROR, "无权限查看")
	}

	return scene, nil
}

func (s *SceneService) ListScenes(userID int64, includePreset bool) ([]model.Scene, error) {
	scenes, err := s.sceneRepo.ListByUserID(userID, includePreset)
	if err != nil {
		return nil, common.NewBusinessException(common.SYSTEM_ERROR, "查询场景列表失败")
	}
	return scenes, nil
}

func (s *SceneService) ListScenesPage(userID int64, pageNum, pageSize int, category string, isPreset *bool) (*dto.PageResponse, error) {
	if pageNum <= 0 {
		pageNum = 1
	}
	if pageSize <= 0 {
		pageSize = 10
	}

	var isPresetInt *int
	if isPreset != nil {
		val := 0
		if *isPreset {
			val = 1
		}
		isPresetInt = &val
	}

	scenes, total, err := s.sceneRepo.ListByUserIDWithPage(userID, pageNum, pageSize, category, isPresetInt)
	if err != nil {
		return nil, common.NewBusinessException(common.SYSTEM_ERROR, "查询场景列表失败")
	}

	pageResp := dto.NewPageResponse(scenes, total, int64(pageNum), int64(pageSize))
	return pageResp, nil
}

func (s *SceneService) GetScenePrompts(sceneID string, userID int64) ([]model.ScenePrompt, error) {
	scene, err := s.sceneRepo.FindByID(sceneID)
	if err != nil {
		if errors.Is(err, gorm.ErrRecordNotFound) {
			return nil, common.NewBusinessException(common.NOT_FOUND_ERROR, "场景不存在")
		}
		return nil, common.NewBusinessException(common.SYSTEM_ERROR, "查询场景失败")
	}

	if scene.IsPreset == 0 && (scene.UserID == nil || *scene.UserID != userID) {
		return nil, common.NewBusinessException(common.NO_AUTH_ERROR, "无权限查看")
	}

	prompts, err := s.scenePromptRepo.ListBySceneID(sceneID)
	if err != nil {
		return nil, common.NewBusinessException(common.SYSTEM_ERROR, "查询提示词列表失败")
	}

	return prompts, nil
}

func (s *SceneService) AddScenePrompt(req *dto.AddScenePromptRequest, userID int64) (string, error) {
	if req.SceneID == "" {
		return "", common.NewBusinessException(common.PARAMS_ERROR, "场景ID不能为空")
	}

	scene, err := s.sceneRepo.FindByID(req.SceneID)
	if err != nil {
		if errors.Is(err, gorm.ErrRecordNotFound) {
			return "", common.NewBusinessException(common.NOT_FOUND_ERROR, "场景不存在")
		}
		return "", common.NewBusinessException(common.SYSTEM_ERROR, "查询场景失败")
	}

	if scene.UserID == nil || *scene.UserID != userID {
		return "", common.NewBusinessException(common.NO_AUTH_ERROR, "无权限操作")
	}

	promptIndex, err := s.scenePromptRepo.GetNextPromptIndex(req.SceneID)
	if err != nil {
		return "", common.NewBusinessException(common.SYSTEM_ERROR, "获取提示词索引失败")
	}

	promptID := utils.GenerateUUID()
	prompt := &model.ScenePrompt{
		ID:             promptID,
		SceneID:        req.SceneID,
		UserID:         userID,
		PromptIndex:    promptIndex,
		Title:          req.Title,
		Content:        req.Content,
		Difficulty:     req.Difficulty,
		ExpectedOutput: req.ExpectedOutput,
		IsDelete:       0,
	}

	if err := s.scenePromptRepo.Create(prompt); err != nil {
		return "", common.NewBusinessException(common.OPERATION_ERROR, "添加提示词失败")
	}

	return promptID, nil
}

func (s *SceneService) UpdateScenePrompt(req *dto.UpdateScenePromptRequest, userID int64) error {
	if req.ID == "" {
		return common.NewBusinessException(common.PARAMS_ERROR, "提示词ID不能为空")
	}

	existingPrompt, err := s.scenePromptRepo.FindByID(req.ID)
	if err != nil {
		if errors.Is(err, gorm.ErrRecordNotFound) {
			return common.NewBusinessException(common.NOT_FOUND_ERROR, "提示词不存在")
		}
		return common.NewBusinessException(common.SYSTEM_ERROR, "查询提示词失败")
	}

	if existingPrompt.UserID != userID {
		return common.NewBusinessException(common.NO_AUTH_ERROR, "无权限操作")
	}

	updates := make(map[string]interface{})
	if req.Title != "" {
		updates["title"] = req.Title
	}
	if req.Content != "" {
		updates["content"] = req.Content
	}
	if req.Difficulty != "" {
		updates["difficulty"] = req.Difficulty
	}
	if req.ExpectedOutput != "" {
		updates["expectedOutput"] = req.ExpectedOutput
	}

	if len(updates) == 0 {
		return common.NewBusinessException(common.PARAMS_ERROR, "没有需要更新的字段")
	}

	if err := s.scenePromptRepo.UpdateByID(req.ID, updates); err != nil {
		return common.NewBusinessException(common.OPERATION_ERROR, "更新提示词失败")
	}

	return nil
}

func (s *SceneService) DeleteScenePrompt(promptID string, userID int64) error {
	prompt, err := s.scenePromptRepo.FindByID(promptID)
	if err != nil {
		if errors.Is(err, gorm.ErrRecordNotFound) {
			return common.NewBusinessException(common.NOT_FOUND_ERROR, "提示词不存在")
		}
		return common.NewBusinessException(common.SYSTEM_ERROR, "查询提示词失败")
	}

	if prompt.UserID != userID {
		return common.NewBusinessException(common.NO_AUTH_ERROR, "无权限操作")
	}

	if err := s.scenePromptRepo.Delete(promptID); err != nil {
		return common.NewBusinessException(common.OPERATION_ERROR, "删除提示词失败")
	}

	return nil
}
