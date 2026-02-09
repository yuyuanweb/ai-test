// Package llm LangChain-Go适配器
// @author <a href="https://codefather.cn">编程导航学习圈</a>
package llm

import (
	"bufio"
	"bytes"
	"context"
	"encoding/json"
	"fmt"
	"io"
	"net/http"
	"strings"
	"time"

	"github.com/tmc/langchaingo/llms"
	"github.com/tmc/langchaingo/llms/openai"
)

type LangChainAdapter struct {
	client  *openai.LLM
	apiKey  string
	baseURL string
}

func NewLangChainAdapter(apiKey, baseURL string) (*LangChainAdapter, error) {
	llm, err := openai.New(
		openai.WithToken(apiKey),
		openai.WithBaseURL(baseURL),
	)
	if err != nil {
		return nil, fmt.Errorf("创建LangChain客户端失败: %w", err)
	}

	return &LangChainAdapter{
		client:  llm,
		apiKey:  apiKey,
		baseURL: baseURL,
	}, nil
}

func (a *LangChainAdapter) Call(ctx context.Context, prompt, model string) (string, error) {
	content, err := a.client.Call(ctx, prompt,
		llms.WithModel(model),
		llms.WithTemperature(0.7),
		llms.WithMaxTokens(4096),
	)
	if err != nil {
		return "", fmt.Errorf("LangChain调用失败: %w", err)
	}

	return content, nil
}

type StreamData struct {
	Content   string
	Reasoning string
}

type StreamCallback func(data StreamData) error

const onlineSuffix = ":online"

type Message struct {
	Role      string   `json:"role"`
	Content   string   `json:"content"`
	ImageUrls []string `json:"-"`
}

type StreamChunk struct{
	ID      string         `json:"id"`
	Choices []StreamChoice `json:"choices"`
	Model   string         `json:"model"`
}

type StreamChoice struct {
	Delta        StreamDelta `json:"delta"`
	FinishReason *string     `json:"finish_reason"`
}

type StreamDelta struct {
	Content   string `json:"content"`
	Reasoning string `json:"reasoning"`
	Role      string `json:"role"`
}

func (a *LangChainAdapter) CallStream(ctx context.Context, prompt, model string, callback StreamCallback) error {
	return a.CallStreamWithHistory(ctx, nil, prompt, nil, model, false, callback)
}

func (a *LangChainAdapter) CallStreamWithHistory(ctx context.Context, historyMessages []Message, prompt string, imageUrls []string, model string, webSearchEnabled bool, callback StreamCallback) error {
	return a.CallStreamWithSystemPrompt(ctx, historyMessages, prompt, imageUrls, "", model, webSearchEnabled, callback)
}

func (a *LangChainAdapter) CallStreamWithSystemPrompt(ctx context.Context, historyMessages []Message, prompt string, imageUrls []string, systemPrompt, model string, webSearchEnabled bool, callback StreamCallback) error {
	effectiveModel := applyOnlineSuffix(model, webSearchEnabled)

	messagesForAPI := make([]map[string]interface{}, 0, len(historyMessages)+3)
	if systemPrompt != "" {
		messagesForAPI = append(messagesForAPI, map[string]interface{}{
			"role":    "system",
			"content": systemPrompt,
		})
	}
	for _, msg := range historyMessages {
		messagesForAPI = append(messagesForAPI, buildMessageContent(msg.Role, msg.Content, msg.ImageUrls))
	}
	messagesForAPI = append(messagesForAPI, buildMessageContent("user", prompt, imageUrls))

	reqBody := map[string]interface{}{
		"model":       effectiveModel,
		"messages":    messagesForAPI,
		"temperature": 0.7,
		"max_tokens":  4096,
		"stream":      true,
	}

	reqData, err := json.Marshal(reqBody)
	if err != nil {
		return fmt.Errorf("序列化请求失败: %w", err)
	}

	httpClient := &http.Client{Timeout: 60 * time.Second}
	httpReq, err := http.NewRequestWithContext(ctx, "POST", a.baseURL+"/chat/completions", bytes.NewBuffer(reqData))
	if err != nil {
		return fmt.Errorf("创建请求失败: %w", err)
	}

	httpReq.Header.Set("Content-Type", "application/json")
	httpReq.Header.Set("Authorization", "Bearer "+a.apiKey)

	resp, err := httpClient.Do(httpReq)
	if err != nil {
		return fmt.Errorf("执行请求失败: %w", err)
	}
	defer resp.Body.Close()

	if resp.StatusCode != http.StatusOK {
		body, _ := io.ReadAll(resp.Body)
		return fmt.Errorf("API错误: status=%d, body=%s", resp.StatusCode, string(body))
	}

	reader := bufio.NewReader(resp.Body)
	for {
		line, err := reader.ReadString('\n')
		if err != nil {
			if err == io.EOF {
				break
			}
			return fmt.Errorf("读取响应失败: %w", err)
		}

		line = strings.TrimSpace(line)
		if line == "" || line == "data: [DONE]" {
			continue
		}

		if !strings.HasPrefix(line, "data: ") {
			continue
		}

		data := strings.TrimPrefix(line, "data: ")
		var chunk StreamChunk
		if err := json.Unmarshal([]byte(data), &chunk); err != nil {
			continue
		}

		if len(chunk.Choices) > 0 {
			delta := chunk.Choices[0].Delta
			if delta.Content != "" || delta.Reasoning != "" {
				streamData := StreamData{
					Content:   delta.Content,
					Reasoning: delta.Reasoning,
				}
				if err := callback(streamData); err != nil {
					return err
				}
			}
		}
	}

	return nil
}

func applyOnlineSuffix(model string, webSearchEnabled bool) string {
	if model == "" || !webSearchEnabled {
		return model
	}
	if strings.Contains(model, onlineSuffix) {
		return model
	}
	return model + onlineSuffix
}

func buildMessageContent(role, text string, imageUrls []string) map[string]interface{} {
	content := text
	hasImages := len(imageUrls) > 0
	for _, u := range imageUrls {
		if u != "" {
			hasImages = true
			break
		}
	}
	if role != "user" || !hasImages {
		return map[string]interface{}{
			"role":    role,
			"content": content,
		}
	}
	parts := []map[string]interface{}{
		{"type": "text", "text": content},
	}
	for _, u := range imageUrls {
		u = strings.TrimSpace(u)
		if u == "" {
			continue
		}
		parts = append(parts, map[string]interface{}{
			"type": "image_url",
			"image_url": map[string]string{"url": u},
		})
	}
	return map[string]interface{}{
		"role":    role,
		"content": parts,
	}
}
