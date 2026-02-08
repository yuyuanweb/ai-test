// Package service AI评分服务
// @author <a href="https://codefather.cn">编程导航学习圈</a>
package service

import (
	"ai-test-go/internal/model/dto"
	"ai-test-go/internal/repository"
	"ai-test-go/pkg/common"
	"ai-test-go/pkg/logger"
	"ai-test-go/pkg/openrouter"
	"encoding/json"
	"fmt"
	"math"
	"strings"
	"sync"
)

const (
	defaultJudgeModel   = "qwen/qwen-plus"
	maxJudges           = 3
	minJudges           = 2
	scoringTemperature  = 0.3
)

const scoringPromptTemplate = `你是一位专业的AI评测专家。请对以下AI模型的回答进行评分。

## 问题
{question}

## 模型回答
{model_response}

## 评分标准
1. 准确性（30分）：答案是否正确，事实是否准确
2. 相关性（20分）：是否切题，是否回答了问题
3. 完整性（20分）：是否全面，是否遗漏重要信息
4. 清晰度（15分）：表达是否清楚，逻辑是否连贯
5. 创意性（15分）：是否有独特见解或创新点

请以JSON格式输出评分结果：
{
  "scores": {
    "accuracy": 分数,
    "relevance": 分数,
    "completeness": 分数,
    "clarity": 分数,
    "creativity": 分数
  },
  "total_score": 总分（100分制）,
  "rating": 评级（1-10分）,
  "comment": "简短评价（50字以内）"
}
`

type AIScoringService struct {
	openRouterClient      *openrouter.Client
	modelRepo             *repository.ModelRepository
	userModelUsageService *UserModelUsageService
}

func NewAIScoringService(
	openRouterClient *openrouter.Client,
	modelRepo *repository.ModelRepository,
	userModelUsageService *UserModelUsageService,
) *AIScoringService {
	return &AIScoringService{
		openRouterClient:      openRouterClient,
		modelRepo:             modelRepo,
		userModelUsageService: userModelUsageService,
	}
}

// Score 对模型回答进行单评委AI评分
func (s *AIScoringService) Score(question, modelResponse string, userID int64) (*dto.EvaluationResult, error) {
	if strings.TrimSpace(question) == "" {
		return nil, common.NewBusinessException(common.PARAMS_ERROR, "问题不能为空")
	}
	if strings.TrimSpace(modelResponse) == "" {
		return nil, common.NewBusinessException(common.PARAMS_ERROR, "模型回答不能为空")
	}

	prompt := buildScoringPrompt(question, modelResponse)

	logger.Log.Infof("开始AI评分: questionLen=%d, responseLen=%d, userId=%d",
		len(question), len(modelResponse), userID)

	req := &openrouter.ChatRequest{
		Model:       defaultJudgeModel,
		Temperature: scoringTemperature,
		Messages:    []openrouter.Message{{Role: "user", Content: prompt}},
	}

	resp, err := s.openRouterClient.Chat(req)
	if err != nil {
		logger.Log.Errorf("AI评分调用失败: %v", err)
		return nil, common.NewBusinessException(common.SYSTEM_ERROR, "AI评分失败: "+err.Error())
	}

	content := ""
	if len(resp.Choices) > 0 {
		content = resp.Choices[0].Message.Content
	}

	var result dto.EvaluationResult
	if err := parseEvaluationJSON(content, &result); err != nil {
		logger.Log.Errorf("解析AI评分结果失败: %v", err)
		return nil, common.NewBusinessException(common.SYSTEM_ERROR, "AI评分结果解析失败")
	}

	s.updateUsageStatistics(defaultJudgeModel, resp, userID)

	logger.Log.Infof("AI评分完成: totalScore=%d, rating=%d", result.TotalScore, result.Rating)
	return &result, nil
}

// ScoreWithMultipleJudges 对模型回答进行多评委交叉验证评分
func (s *AIScoringService) ScoreWithMultipleJudges(question, modelResponse, testedModelName string, userID int64) (*dto.AIScoreResult, error) {
	if strings.TrimSpace(question) == "" {
		return nil, common.NewBusinessException(common.PARAMS_ERROR, "问题不能为空")
	}
	if strings.TrimSpace(modelResponse) == "" {
		return nil, common.NewBusinessException(common.PARAMS_ERROR, "模型回答不能为空")
	}

	judgeModels := s.selectJudgeModels(testedModelName)
	if len(judgeModels) == 0 {
		logger.Log.Warnf("未找到可用评委模型，使用单评委模式: testedModel=%s", testedModelName)
		single, err := s.Score(question, modelResponse, userID)
		if err != nil {
			return nil, err
		}
		js := dto.JudgeScore{
			Model:      defaultJudgeModel,
			Scores:     single.Scores,
			TotalScore: single.TotalScore,
			Rating:     single.Rating,
			Comment:    single.Comment,
		}
		avg := 0.0
		if single.Rating > 0 {
			avg = float64(single.Rating)
		}
		return &dto.AIScoreResult{
			Judges:        []dto.JudgeScore{js},
			AverageRating: avg,
			Consistency:   0,
		}, nil
	}

	prompt := buildScoringPrompt(question, modelResponse)

	logger.Log.Infof("开始多评委交叉验证评分: testedModel=%s, judges=%v", testedModelName, judgeModels)

	var wg sync.WaitGroup
	mu := sync.Mutex{}
	var judgeScores []dto.JudgeScore

	for _, modelID := range judgeModels {
		wg.Add(1)
		go func(judgeModel string) {
			defer wg.Done()
			req := &openrouter.ChatRequest{
				Model:       judgeModel,
				Temperature: scoringTemperature,
				Messages:    []openrouter.Message{{Role: "user", Content: prompt}},
			}
			resp, err := s.openRouterClient.Chat(req)
			if err != nil {
				logger.Log.Errorf("评委 %s 评分失败: %v", judgeModel, err)
				return
			}
			content := ""
			if len(resp.Choices) > 0 {
				content = resp.Choices[0].Message.Content
			}
			var evalResult dto.EvaluationResult
			if parseEvaluationJSON(content, &evalResult) != nil {
				logger.Log.Warnf("评委 %s 评分结果解析失败", judgeModel)
				return
			}
			s.updateUsageStatistics(judgeModel, resp, userID)
			js := dto.JudgeScore{
				Model:      judgeModel,
				Scores:     evalResult.Scores,
				TotalScore: evalResult.TotalScore,
				Rating:     evalResult.Rating,
				Comment:    evalResult.Comment,
			}
			mu.Lock()
			judgeScores = append(judgeScores, js)
			mu.Unlock()
			logger.Log.Infof("评委 %s 评分完成: totalScore=%d, rating=%d", judgeModel, evalResult.TotalScore, evalResult.Rating)
		}(modelID)
	}

	wg.Wait()

	if len(judgeScores) == 0 {
		return nil, common.NewBusinessException(common.SYSTEM_ERROR, "所有评委评分都失败了")
	}

	avgRating := calculateAverageRating(judgeScores)
	consistency := calculateConsistency(judgeScores)

	logger.Log.Infof("多评委评分完成: testedModel=%s, judges=%d, averageRating=%.2f, consistency=%.2f",
		testedModelName, len(judgeScores), avgRating, consistency)

	return &dto.AIScoreResult{
		Judges:        judgeScores,
		AverageRating: avgRating,
		Consistency:    consistency,
	}, nil
}

func buildScoringPrompt(question, modelResponse string) string {
	return strings.ReplaceAll(strings.ReplaceAll(scoringPromptTemplate, "{question}", question), "{model_response}", modelResponse)
}

func parseEvaluationJSON(content string, result *dto.EvaluationResult) error {
	content = strings.TrimSpace(content)
	if content == "" {
		return fmt.Errorf("评分为空")
	}
	start := strings.Index(content, "{")
	end := strings.LastIndex(content, "}")
	if start < 0 || end <= start {
		return fmt.Errorf("无效的评分JSON")
	}
	if err := json.Unmarshal([]byte(content[start:end+1]), result); err != nil {
		return fmt.Errorf("解析评分JSON失败: %w", err)
	}
	return nil
}

func (s *AIScoringService) selectJudgeModels(testedModelName string) []string {
	models, err := s.modelRepo.ListDomesticForJudge()
	if err != nil {
		logger.Log.Warnf("查询评委模型失败: %v", err)
		return nil
	}

	var candidates []string
	for _, m := range models {
		if testedModelName != "" && (m.ID == testedModelName || isSameProvider(m.ID, testedModelName)) {
			continue
		}
		candidates = append(candidates, m.ID)
	}

	count := maxJudges
	if len(candidates) < minJudges {
		count = len(candidates)
	} else if len(candidates) < maxJudges {
		count = len(candidates)
	}
	if count < minJudges {
		return nil
	}

	return candidates[:count]
}

func isSameProvider(model1, model2 string) bool {
	if model1 == "" || model2 == "" {
		return false
	}
	parts1 := strings.SplitN(model1, "/", 2)
	parts2 := strings.SplitN(model2, "/", 2)
	p1 := ""
	p2 := ""
	if len(parts1) > 0 {
		p1 = parts1[0]
	}
	if len(parts2) > 0 {
		p2 = parts2[0]
	}
	return p1 != "" && p1 == p2
}

func calculateAverageRating(judgeScores []dto.JudgeScore) float64 {
	if len(judgeScores) == 0 {
		return 0
	}
	var sum float64
	for _, js := range judgeScores {
		sum += float64(js.Rating)
	}
	return sum / float64(len(judgeScores))
}

func calculateConsistency(judgeScores []dto.JudgeScore) float64 {
	if len(judgeScores) < 2 {
		return 0
	}
	avg := calculateAverageRating(judgeScores)
	var variance float64
	for _, js := range judgeScores {
		diff := float64(js.Rating) - avg
		variance += diff * diff
	}
	variance /= float64(len(judgeScores))
	return math.Sqrt(variance)
}

func (s *AIScoringService) updateUsageStatistics(model string, resp *openrouter.ChatResponse, userID int64) {
	if userID <= 0 || resp == nil {
		return
	}
	inputTokens := resp.Usage.PromptTokens
	outputTokens := resp.Usage.CompletionTokens
	totalTokens := int64(inputTokens + outputTokens)
	if totalTokens <= 0 {
		return
	}
	cost := s.estimateJudgeCost(model, inputTokens, outputTokens)
	s.userModelUsageService.UpdateUserModelUsage(userID, model, totalTokens, cost)
	logger.Log.Debugf("AI评分统计: userId=%d, model=%s, tokens=%d, cost=%.6f", userID, model, totalTokens, cost)
}

func (s *AIScoringService) estimateJudgeCost(model string, inputTokens, outputTokens int) float64 {
	inputPrice := 0.001
	outputPrice := 0.002
	if strings.Contains(model, "qwen") {
		inputPrice = 0.0005
		outputPrice = 0.001
	}
	return (float64(inputTokens)*inputPrice + float64(outputTokens)*outputPrice) / 1000.0
}
