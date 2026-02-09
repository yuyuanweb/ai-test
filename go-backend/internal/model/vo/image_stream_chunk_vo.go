// Package vo 图像生成流式响应块 VO
// @author <a href="https://codefather.cn">编程导航学习圈</a>
package vo

// ImageStreamChunkVO 图像生成流式响应块（type: thinking/image/done/error）
type ImageStreamChunkVO struct {
	Type          string            `json:"type"`
	Thinking      string            `json:"thinking,omitempty"`
	FullThinking  string            `json:"fullThinking,omitempty"`
	Image         *GeneratedImageVO `json:"image,omitempty"`
	ConversationId string           `json:"conversationId,omitempty"`
	MessageIndex  *int              `json:"messageIndex,omitempty"`
	VariantIndex  *int              `json:"variantIndex,omitempty"`
	ModelName     string            `json:"modelName,omitempty"`
	Error         string            `json:"error,omitempty"`
}
