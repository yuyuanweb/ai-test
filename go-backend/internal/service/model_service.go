// Package service 模型服务层
// @author <a href="https://codefather.cn">编程导航学习圈</a>
package service

import (
	"ai-test-go/internal/constant"
	"ai-test-go/internal/model"
	"ai-test-go/internal/model/dto"
	"ai-test-go/internal/model/vo"
	"ai-test-go/internal/repository"
	"ai-test-go/pkg/common"
	"ai-test-go/pkg/logger"
	"context"
	"encoding/json"
	"time"

	"github.com/redis/go-redis/v9"
)

type ModelService struct {
	modelRepo   *repository.ModelRepository
	redisClient *redis.Client
}

func NewModelService(modelRepo *repository.ModelRepository, redisClient *redis.Client) *ModelService {
	return &ModelService{
		modelRepo:   modelRepo,
		redisClient: redisClient,
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
		Total:   total,
		Current: req.Current,
		Size:    req.PageSize,
		Records: records,
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

func (s *ModelService) GetModelPricing(modelName string) *vo.ModelPricingVO {
	if modelName == "" {
		return nil
	}

	ctx := context.Background()

	if s.redisClient != nil {
		cacheKey := constant.ModelPricingKeyPrefix + modelName
		cached, err := s.redisClient.Get(ctx, cacheKey).Result()
		if err == nil {
			var cachedPricing vo.ModelPricingVO
			if json.Unmarshal([]byte(cached), &cachedPricing) == nil {
				return &cachedPricing
			}
		}
	}

	m, err := s.modelRepo.FindByID(modelName)
	if err != nil {
		return nil
	}

	inputPrice := m.InputPrice
	if inputPrice == 0 {
		inputPrice = constant.DEFAULT_INPUT_PRICE_PER_MILLION
	}
	outputPrice := m.OutputPrice
	if outputPrice == 0 {
		outputPrice = constant.DEFAULT_OUTPUT_PRICE_PER_MILLION
	}

	result := &vo.ModelPricingVO{
		InputPrice:  inputPrice,
		OutputPrice: outputPrice,
	}

	if s.redisClient != nil {
		cacheKey := constant.ModelPricingKeyPrefix + modelName
		if data, err := json.Marshal(result); err == nil {
			ttl := time.Duration(constant.ModelPricingTTLHours) * time.Hour
			if setErr := s.redisClient.Set(ctx, cacheKey, data, ttl).Err(); setErr != nil {
				logger.Log.Warnf("写入模型价格缓存失败: modelName=%s, err=%v", modelName, setErr)
			}
		}
	}

	return result
}

func (s *ModelService) EvictModelPricingCache(modelName string) {
	if modelName == "" || s.redisClient == nil {
		return
	}
	ctx := context.Background()
	cacheKey := constant.ModelPricingKeyPrefix + modelName
	if err := s.redisClient.Del(ctx, cacheKey).Err(); err != nil {
		logger.Log.Warnf("清除模型价格缓存失败: modelName=%s, err=%v", modelName, err)
	} else {
		logger.Log.Debugf("已清除模型价格缓存: modelName=%s", modelName)
	}
}

func (s *ModelService) UpdateModelUsage(modelName string, tokens int64, cost float64) error {
	if modelName == "" || tokens <= 0 {
		return nil
	}

	m, err := s.modelRepo.FindByID(modelName)
	if err != nil {
		return nil
	}

	updates := map[string]interface{}{
		"totalTokens": m.TotalTokens + tokens,
		"totalCost":   m.TotalCost + cost,
	}

	return s.modelRepo.UpdateByID(modelName, updates)
}
