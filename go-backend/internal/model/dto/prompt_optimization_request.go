// Package dto 提示词优化请求与AI响应解析
// @author <a href="https://codefather.cn">编程导航学习圈</a>
package dto

type PromptOptimizationRequest struct {
	OriginalPrompt   string  `json:"originalPrompt"`
	AiResponse       string  `json:"aiResponse"`
	EvaluationModel  string  `json:"evaluationModel"`
}

type OptimizationSuggestion struct {
	Issues         []string `json:"issues"`
	OptimizedPrompt string  `json:"optimized_prompt"`
	Improvements   []string `json:"improvements"`
}
