// Package service AI 评分服务实现（阶段7 步骤1 仅接口与桩实现，具体逻辑在后续步骤完成）
// @author <a href="https://codefather.cn">编程导航学习圈</a>
package service

import (
	"ai-test-go/internal/model/dto"
	"ai-test-go/internal/repository"
	"ai-test-go/pkg/openrouter"
	"errors"
)

var ErrAIScoringNotImplemented = errors.New("AI评分功能尚未实现，请完成阶段7后续步骤")

type aiScoringServiceImpl struct {
	openRouterClient     *openrouter.Client
	modelRepo            *repository.ModelRepository
	userModelUsageService *UserModelUsageService
}

// NewAIScoringService 创建 AI 评分服务（当前为桩实现，后续步骤补全评分提示词与多评委逻辑）
func NewAIScoringService(
	openRouterClient *openrouter.Client,
	modelRepo *repository.ModelRepository,
	userModelUsageService *UserModelUsageService,
) AIScoringService {
	return &aiScoringServiceImpl{
		openRouterClient:      openRouterClient,
		modelRepo:             modelRepo,
		userModelUsageService: userModelUsageService,
	}
}

func (s *aiScoringServiceImpl) Score(question, modelResponse string, userID int64) (*dto.EvaluationResult, error) {
	return nil, ErrAIScoringNotImplemented
}

// ScoreWithMultipleJudges 实现时应对 openRouterClient.Chat 调用使用 utils.RunWithRetry 包装（非流式幂等调用）
func (s *aiScoringServiceImpl) ScoreWithMultipleJudges(question, modelResponse, testedModelName string, userID int64) (*dto.AIScoreResult, error) {
	return nil, ErrAIScoringNotImplemented
}
