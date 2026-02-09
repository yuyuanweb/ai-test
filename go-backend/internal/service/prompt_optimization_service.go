// Package service 提示词优化服务层
// @author <a href="https://codefather.cn">编程导航学习圈</a>
package service

import (
	"ai-test-go/internal/guardrail"
	"ai-test-go/internal/model/dto"
	"ai-test-go/internal/model/vo"
	"ai-test-go/pkg/common"
	"ai-test-go/pkg/logger"
	"ai-test-go/pkg/openrouter"
	"ai-test-go/pkg/utils"
	"encoding/json"
	"fmt"
	"strings"
)

const (
	defaultOptimizationModel  = "qwen/qwen-plus"
	optimizationTemperature   = 0.3
)

const optimizationPromptTemplate = `你是一位专业的提示词工程专家。请分析以下提示词，并提供优化建议。

## 原始提示词
{originalPrompt}

{aiResponseSection}

## 分析维度
请从以下5个维度分析提示词：
1. **角色设定**：是否明确指定了AI的角色和身份？
2. **任务描述**：任务目标是否清晰、具体？
3. **输出格式**：是否明确指定了期望的输出格式？
4. **思维链**：是否引导AI进行逐步思考？
5. **Few-shot示例**：是否提供了示例来帮助AI理解需求？

## 输出要求
请以JSON格式输出分析结果：
{
  "issues": ["问题1", "问题2", ...],
  "optimized_prompt": "优化后的完整提示词",
  "improvements": ["改进点1", "改进点2", ...]
}

要求：
- issues: 列出当前提示词存在的问题（至少3个维度的问题）
- optimized_prompt: 提供优化后的完整提示词，保持原意但更加清晰、具体
- improvements: 说明每个优化带来的具体提升（至少3个改进点）
`

type PromptOptimizationService struct {
	openRouterClient      *openrouter.Client
	userModelUsageService *UserModelUsageService
	budgetService         *BudgetService
}

func NewPromptOptimizationService(
	openRouterClient *openrouter.Client,
	userModelUsageService *UserModelUsageService,
	budgetService *BudgetService,
) *PromptOptimizationService {
	return &PromptOptimizationService{
		openRouterClient:      openRouterClient,
		userModelUsageService: userModelUsageService,
		budgetService:         budgetService,
	}
}

func (s *PromptOptimizationService) OptimizePrompt(originalPrompt, aiResponse, evaluationModel string, userID int64) (*vo.PromptOptimizationVO, error) {
	if strings.TrimSpace(originalPrompt) == "" {
		return nil, common.NewBusinessException(common.PARAMS_ERROR, "原始提示词不能为空")
	}
	if err := guardrail.Validate(originalPrompt); err != nil {
		return nil, err
	}

	model := defaultOptimizationModel
	if strings.TrimSpace(evaluationModel) != "" {
		model = evaluationModel
	}

	aiResponseSection := ""
	if strings.TrimSpace(aiResponse) != "" {
		aiResponseSection = "\n## AI回答\n" + aiResponse + "\n"
	}

	analysisPrompt := strings.ReplaceAll(optimizationPromptTemplate, "{originalPrompt}", originalPrompt)
	analysisPrompt = strings.ReplaceAll(analysisPrompt, "{aiResponseSection}", aiResponseSection)

	logger.Log.Infof("开始提示词优化分析: promptLen=%d, hasResponse=%v, model=%s, userId=%d",
		len(originalPrompt), strings.TrimSpace(aiResponse) != "", model, userID)

	req := &openrouter.ChatRequest{
		Model:       model,
		Temperature: optimizationTemperature,
		Messages:    []openrouter.Message{{Role: "user", Content: analysisPrompt}},
	}

	resp, err := utils.RunWithRetry(func() (*openrouter.ChatResponse, error) {
		return s.openRouterClient.Chat(req)
	})
	if err != nil {
		logger.Log.Errorf("提示词优化AI调用失败: %v", err)
		return nil, common.NewBusinessException(common.SYSTEM_ERROR, "提示词优化分析失败: "+err.Error())
	}

	content := ""
	if len(resp.Choices) > 0 {
		content = resp.Choices[0].Message.Content
	}

	var suggestion dto.OptimizationSuggestion
	if err := parseOptimizationJSON(content, &suggestion); err != nil {
		logger.Log.Warnf("解析优化结果失败: %v, content=%s", err, content)
		suggestion = dto.OptimizationSuggestion{
			Issues:         []string{},
			OptimizedPrompt: "",
			Improvements:   []string{},
		}
	}

	if userID > 0 && resp.Usage.TotalTokens > 0 {
		cost := s.estimateOptimizationCost(model, resp.Usage.PromptTokens, resp.Usage.CompletionTokens)
		s.userModelUsageService.UpdateUserModelUsage(userID, model, int64(resp.Usage.TotalTokens), cost)
		if cost > 0 && s.budgetService != nil {
			s.budgetService.AddCost(userID, cost)
		}
		logger.Log.Infof("提示词优化统计: userId=%d, model=%s, tokens=%d, cost=%.6f",
			userID, model, resp.Usage.TotalTokens, cost)
	}

	logger.Log.Infof("提示词优化分析完成: issuesCount=%d, improvementsCount=%d, tokens=%d",
		len(suggestion.Issues), len(suggestion.Improvements), resp.Usage.TotalTokens)

	result := &vo.PromptOptimizationVO{
		Issues:         suggestion.Issues,
		OptimizedPrompt: suggestion.OptimizedPrompt,
		Improvements:   suggestion.Improvements,
	}
	if result.Issues == nil {
		result.Issues = []string{}
	}
	if result.Improvements == nil {
		result.Improvements = []string{}
	}

	return result, nil
}

func parseOptimizationJSON(content string, result *dto.OptimizationSuggestion) error {
	content = strings.TrimSpace(content)
	if content == "" {
		return fmt.Errorf("响应为空")
	}
	start := strings.Index(content, "{")
	end := strings.LastIndex(content, "}")
	if start < 0 || end <= start {
		return fmt.Errorf("无效的JSON格式")
	}
	if err := json.Unmarshal([]byte(content[start:end+1]), result); err != nil {
		return fmt.Errorf("解析JSON失败: %w", err)
	}
	return nil
}

func (s *PromptOptimizationService) estimateOptimizationCost(model string, inputTokens, outputTokens int) float64 {
	inputPrice := 0.001
	outputPrice := 0.002
	if strings.Contains(model, "qwen") {
		inputPrice = 0.0005
		outputPrice = 0.001
	}
	return (float64(inputTokens)*inputPrice + float64(outputTokens)*outputPrice) / 1000.0
}
