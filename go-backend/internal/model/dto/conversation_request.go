// Package dto 对话相关请求DTO
// @author <a href="https://codefather.cn">编程导航学习圈</a>
package dto

type CreateConversationRequest struct {
	Title              string   `json:"title"`
	ConversationType   string   `json:"conversationType" binding:"required"`
	CodePreviewEnabled bool     `json:"codePreviewEnabled"`
	Models             []string `json:"models"`
}

type ChatRequest struct {
	ConversationID   string   `json:"conversationId"`
	Model            string   `json:"model" binding:"required"`
	Message          string   `json:"message" binding:"required"`
	ImageUrls        []string `json:"imageUrls"`
	WebSearchEnabled bool     `json:"webSearchEnabled"`
}

type SideBySideRequest struct {
	Models           []string `json:"models" binding:"required"`
	Prompt           string   `json:"prompt" binding:"required"`
	ImageUrls        []string `json:"imageUrls"`
	ConversationID   string   `json:"conversationId"`
	Stream           bool     `json:"stream"`
	WebSearchEnabled bool     `json:"webSearchEnabled"`
}

type PromptLabRequest struct {
	Model            string     `json:"model" binding:"required"`
	PromptVariants   []string   `json:"promptVariants" binding:"required,min=2,max=5"`
	VariantImageUrls [][]string `json:"variantImageUrls"`
	ConversationID   string     `json:"conversationId"`
	Stream           *bool      `json:"stream"`
	WebSearchEnabled bool       `json:"webSearchEnabled"`
}

type BattleRequest struct {
	Models           []string `json:"models" binding:"required"`
	Prompt           string   `json:"prompt" binding:"required"`
	ConversationID   string   `json:"conversationId"`
	ImageUrls        []string `json:"imageUrls"`
	WebSearchEnabled bool     `json:"webSearchEnabled"`
}

type CodeModeRequest struct {
	Models           []string `json:"models" binding:"required"`
	Prompt           string   `json:"prompt" binding:"required"`
	ConversationID   string   `json:"conversationId"`
	ImageUrls        []string `json:"imageUrls"`
	WebSearchEnabled bool     `json:"webSearchEnabled"`
}

type CodeModePromptLabRequest struct {
	Model            string     `json:"model" binding:"required"`
	PromptVariants   []string   `json:"promptVariants" binding:"required,min=2,max=5"`
	VariantImageUrls [][]string `json:"variantImageUrls"`
	ConversationID   string     `json:"conversationId"`
	Stream           *bool      `json:"stream"`
	WebSearchEnabled bool       `json:"webSearchEnabled"`
}

type DeleteConversationRequest struct {
	ID string `json:"id" binding:"required"`
}
