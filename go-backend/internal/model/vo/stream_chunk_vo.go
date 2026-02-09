// Package vo 流式响应数据块VO
// @author <a href="https://codefather.cn">编程导航学习圈</a>
package vo

import "ai-test-go/internal/model/dto"

type StreamChunkVO struct {
	ConversationID string           `json:"conversationId,omitempty"`
	ModelName      string           `json:"modelName,omitempty"`
	VariantIndex   *int             `json:"variantIndex,omitempty"`
	MessageIndex   *int             `json:"messageIndex,omitempty"`
	Content        string           `json:"content,omitempty"`
	FullContent    string           `json:"fullContent,omitempty"`
	InputTokens    int              `json:"inputTokens,omitempty"`
	OutputTokens   int              `json:"outputTokens,omitempty"`
	TotalTokens    int              `json:"totalTokens,omitempty"`
	ElapsedMs      int64            `json:"elapsedMs,omitempty"`
	ResponseTimeMs int              `json:"responseTimeMs,omitempty"`
	Cost           float64          `json:"cost,omitempty"`
	Done           bool             `json:"done"`
	Error          string           `json:"error,omitempty"`
	HasError       bool             `json:"hasError"`
	Reasoning      string           `json:"reasoning,omitempty"`
	HasReasoning   bool             `json:"hasReasoning,omitempty"`
	ThinkingTime   int              `json:"thinkingTime,omitempty"`
	CodeBlocks     []dto.CodeBlock  `json:"codeBlocks,omitempty"`
	HasCodeBlocks  bool             `json:"hasCodeBlocks,omitempty"`
	ToolsUsed      string           `json:"toolsUsed,omitempty"`
}
