// Package vo 提示词优化结果视图对象
// @author <a href="https://codefather.cn">编程导航学习圈</a>
package vo

type PromptOptimizationVO struct {
	Issues         []string `json:"issues"`
	OptimizedPrompt string  `json:"optimizedPrompt"`
	Improvements   []string `json:"improvements"`
}
