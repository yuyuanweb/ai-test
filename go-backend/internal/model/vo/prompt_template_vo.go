// Package vo 提示词模板视图对象
// @author <a href="https://codefather.cn">编程导航学习圈</a>
package vo

type PromptTemplateVO struct {
	ID           string   `json:"id"`
	Name         string   `json:"name"`
	Description  string   `json:"description"`
	Strategy     string   `json:"strategy"`
	StrategyName string   `json:"strategyName"`
	Content      string   `json:"content"`
	Variables    []string `json:"variables"`
	Category     string   `json:"category"`
	IsPreset     bool     `json:"isPreset"`
	UsageCount   int      `json:"usageCount"`
	IsActive     bool     `json:"isActive"`
	CreateTime   string   `json:"createTime"`
}
