// Package service 提示词模板服务层
// @author <a href="https://codefather.cn">编程导航学习圈</a>
package service

import (
	"ai-test-go/internal/model"
	"ai-test-go/internal/model/dto"
	"ai-test-go/internal/model/vo"
	"ai-test-go/internal/repository"
	"ai-test-go/pkg/common"
	"ai-test-go/pkg/utils"
	"strings"
)

const (
	StrategyDirect   = "direct"
	StrategyCot      = "cot"
	StrategyRolePlay = "role_play"
	StrategyFewShot  = "few_shot"
)

var strategyNameMap = map[string]string{
	StrategyDirect:   "直接提问",
	StrategyCot:      "CoT (思维链)",
	StrategyRolePlay: "角色扮演",
	StrategyFewShot:  "Few-shot (示例学习)",
}

type PromptTemplateService struct {
	repo *repository.PromptTemplateRepository
}

func NewPromptTemplateService(repo *repository.PromptTemplateRepository) *PromptTemplateService {
	return &PromptTemplateService{repo: repo}
}

func (s *PromptTemplateService) ListTemplates(userID int64, strategy string) ([]vo.PromptTemplateVO, error) {
	list, err := s.repo.ListByUserIDAndStrategy(userID, strategy)
	if err != nil {
		return nil, common.NewBusinessException(common.SYSTEM_ERROR, "查询模板列表失败")
	}
	result := make([]vo.PromptTemplateVO, 0, len(list))
	for _, t := range list {
		result = append(result, s.toVO(&t))
	}
	return result, nil
}

func (s *PromptTemplateService) GetTemplateByID(templateID string, userID int64) (*vo.PromptTemplateVO, error) {
	if strings.TrimSpace(templateID) == "" {
		return nil, common.NewBusinessException(common.PARAMS_ERROR, "模板ID不能为空")
	}
	t, err := s.repo.FindByID(templateID)
	if err != nil || t == nil {
		return nil, common.NewBusinessException(common.NOT_FOUND_ERROR, "模板不存在")
	}
	if t.IsPreset == 0 && (t.UserID == nil || *t.UserID != userID) {
		return nil, common.NewBusinessException(common.NO_AUTH_ERROR, "无权限访问")
	}
	vo := s.toVO(t)
	return &vo, nil
}

func (s *PromptTemplateService) CreateTemplate(req *dto.CreatePromptTemplateRequest, userID int64) (string, error) {
	if strings.TrimSpace(req.Name) == "" {
		return "", common.NewBusinessException(common.PARAMS_ERROR, "模板名称不能为空")
	}
	if strings.TrimSpace(req.Strategy) == "" {
		return "", common.NewBusinessException(common.PARAMS_ERROR, "策略类型不能为空")
	}
	if strings.TrimSpace(req.Content) == "" {
		return "", common.NewBusinessException(common.PARAMS_ERROR, "模板内容不能为空")
	}

	templateID := utils.GenerateUUID()
	t := &model.PromptTemplate{
		ID:          templateID,
		UserID:      &userID,
		Name:        req.Name,
		Description: req.Description,
		Strategy:    req.Strategy,
		Content:     req.Content,
		Variables:   req.Variables,
		Category:    req.Category,
		IsPreset:    0,
		UsageCount:  0,
		IsActive:    1,
	}

	if err := s.repo.Create(t); err != nil {
		return "", common.NewBusinessException(common.OPERATION_ERROR, "创建模板失败")
	}
	return templateID, nil
}

func (s *PromptTemplateService) UpdateTemplate(req *dto.UpdatePromptTemplateRequest, userID int64) (bool, error) {
	if strings.TrimSpace(req.ID) == "" {
		return false, common.NewBusinessException(common.PARAMS_ERROR, "模板ID不能为空")
	}

	existing, err := s.repo.FindByID(req.ID)
	if err != nil || existing == nil {
		return false, common.NewBusinessException(common.NOT_FOUND_ERROR, "模板不存在")
	}
	if existing.IsPreset == 1 {
		return false, common.NewBusinessException(common.NO_AUTH_ERROR, "预设模板不能修改")
	}
	if existing.UserID == nil || *existing.UserID != userID {
		return false, common.NewBusinessException(common.NO_AUTH_ERROR, "无权限操作")
	}

	existing.Name = req.Name
	existing.Description = req.Description
	existing.Strategy = req.Strategy
	existing.Content = req.Content
	existing.Category = req.Category
	if len(req.Variables) > 0 {
		existing.Variables = req.Variables
	}
	if req.IsActive != nil {
		if *req.IsActive {
			existing.IsActive = 1
		} else {
			existing.IsActive = 0
		}
	}

	if err := s.repo.Update(existing); err != nil {
		return false, common.NewBusinessException(common.OPERATION_ERROR, "更新模板失败")
	}
	return true, nil
}

func (s *PromptTemplateService) DeleteTemplate(templateID string, userID int64) (bool, error) {
	if strings.TrimSpace(templateID) == "" {
		return false, common.NewBusinessException(common.PARAMS_ERROR, "模板ID不能为空")
	}

	t, err := s.repo.FindByID(templateID)
	if err != nil || t == nil {
		return false, common.NewBusinessException(common.NOT_FOUND_ERROR, "模板不存在")
	}
	if t.IsPreset == 1 {
		return false, common.NewBusinessException(common.NO_AUTH_ERROR, "预设模板不能删除")
	}
	if t.UserID == nil || *t.UserID != userID {
		return false, common.NewBusinessException(common.NO_AUTH_ERROR, "无权限操作")
	}

	if err := s.repo.Delete(templateID); err != nil {
		return false, common.NewBusinessException(common.OPERATION_ERROR, "删除模板失败")
	}
	return true, nil
}

func (s *PromptTemplateService) IncrementUsageCount(templateID string) (bool, error) {
	if strings.TrimSpace(templateID) == "" {
		return false, common.NewBusinessException(common.PARAMS_ERROR, "模板ID不能为空")
	}

	_, err := s.repo.FindByID(templateID)
	if err != nil {
		return false, nil
	}

	if err := s.repo.IncrementUsageCount(templateID); err != nil {
		return false, nil
	}
	return true, nil
}

func (s *PromptTemplateService) toVO(t *model.PromptTemplate) vo.PromptTemplateVO {
	strategyName := t.Strategy
	if n, ok := strategyNameMap[t.Strategy]; ok {
		strategyName = n
	}
	vars := t.Variables
	if vars == nil {
		vars = []string{}
	}
	return vo.PromptTemplateVO{
		ID:           t.ID,
		Name:         t.Name,
		Description:  t.Description,
		Strategy:     t.Strategy,
		StrategyName: strategyName,
		Content:      t.Content,
		Variables:    vars,
		Category:     t.Category,
		IsPreset:     t.IsPreset == 1,
		UsageCount:   t.UsageCount,
		IsActive:     t.IsActive == 1,
		CreateTime:   t.CreateTime.Format("2006-01-02 15:04:05"),
	}
}
