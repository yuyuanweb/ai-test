// Package dto 图片生成请求 DTO
// @author <a href="https://codefather.cn">编程导航学习圈</a>
package dto

// GenerateImageRequest 图片生成请求（与 Java 对齐）
type GenerateImageRequest struct {
	Model               string   `json:"model"`
	IsAnonymous         *bool    `json:"isAnonymous"`
	Prompt              string   `json:"prompt"`
	ReferenceImageUrls  []string `json:"referenceImageUrls"`
	Count               *int     `json:"count"`
	ConversationId      string   `json:"conversationId"`
	Models              []string `json:"models"`
	ConversationType    string   `json:"conversationType"`
	VariantIndex        *int     `json:"variantIndex"`
	MessageIndex        *int     `json:"messageIndex"`
	Reasoning           string   `json:"reasoning"`
}
