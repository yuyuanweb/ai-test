// Package vo 生成图片结果 VO
// @author <a href="https://codefather.cn">编程导航学习圈</a>
package vo

// GeneratedImageVO 生成图片结果（与前端约定一致）
type GeneratedImageVO struct {
	Url             string   `json:"url"`
	ModelName       string   `json:"modelName"`
	Index           int      `json:"index"`
	InputTokens     *int     `json:"inputTokens"`
	OutputTokens    *int     `json:"outputTokens"`
	TotalTokens     *int     `json:"totalTokens"`
	Cost            *float64 `json:"cost"`
	ConversationId  string   `json:"conversationId"`
	MessageIndex    *int     `json:"messageIndex"`
}
