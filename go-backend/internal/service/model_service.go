// Package service 模型服务层
// @author <a href="https://codefather.cn">编程导航学习圈</a>
package service

import (
	"ai-test-go/internal/model"
	"ai-test-go/internal/model/dto"
	"ai-test-go/internal/model/vo"
	"ai-test-go/internal/repository"
	"ai-test-go/pkg/common"
	"encoding/json"
)

type ModelService struct {
	modelRepo *repository.ModelRepository
}

func NewModelService(modelRepo *repository.ModelRepository) *ModelService {
	return &ModelService{
		modelRepo: modelRepo,
	}
}

func (s *ModelService) ListModels(req *dto.ModelQueryRequest, userID *int64) (*dto.PageResponse, error) {
	models, total, err := s.modelRepo.List(req)
	if err != nil {
		return nil, common.NewBusinessException(common.SYSTEM_ERROR, "查询模型列表失败")
	}

	records := make([]vo.ModelVO, 0, len(models))
	for _, m := range models {
		records = append(records, s.convertToVO(&m, userID))
	}

	return &dto.PageResponse{
		Total:    total,
		PageNum:  req.Current,
		PageSize: req.PageSize,
		Records:  records,
	}, nil
}

func (s *ModelService) GetAllModels(userID *int64) ([]vo.ModelVO, error) {
	models, err := s.modelRepo.GetAll()
	if err != nil {
		return nil, common.NewBusinessException(common.SYSTEM_ERROR, "查询模型列表失败")
	}

	result := make([]vo.ModelVO, 0, len(models))
	for _, m := range models {
		result = append(result, s.convertToVO(&m, userID))
	}

	return result, nil
}

func (s *ModelService) convertToVO(m *model.Model, userID *int64) vo.ModelVO {
	var tags []string
	if m.Tags != "" {
		json.Unmarshal([]byte(m.Tags), &tags)
	}

	return vo.ModelVO{
		ID:                  m.ID,
		Name:                m.Name,
		Description:         m.Description,
		Provider:            m.Provider,
		ContextLength:       m.ContextLength,
		InputPrice:          m.InputPrice,
		OutputPrice:         m.OutputPrice,
		Recommended:         m.Recommended == 1,
		IsChina:             m.IsChina == 1,
		SupportsMultimodal:  m.SupportsMultimodal == 1,
		SupportsImageGen:    m.SupportsImageGen == 1,
		SupportsToolCalling: m.SupportsToolCalling == 1,
		Tags:                tags,
		TotalTokens:         m.TotalTokens,
		TotalCost:           m.TotalCost,
		UserTotalTokens:     0,
		UserTotalCost:       0,
	}
}
