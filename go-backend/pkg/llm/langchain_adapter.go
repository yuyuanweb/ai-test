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

type StreamCallback func(content string) error

type StreamChunk struct {
	ID      string          `json:"id"`
	Choices []StreamChoice  `json:"choices"`
	Model   string          `json:"model"`
}

type StreamChoice struct {
	Delta        StreamDelta `json:"delta"`
	FinishReason *string     `json:"finish_reason"`
}

type StreamDelta struct {
	Content string `json:"content"`
	Role    string `json:"role"`
}

func (a *LangChainAdapter) CallStream(ctx context.Context, prompt, model string, callback StreamCallback) error {
	reqBody := map[string]interface{}{
		"model": model,
		"messages": []map[string]string{
			{"role": "user", "content": prompt},
		},
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

		if len(chunk.Choices) > 0 && chunk.Choices[0].Delta.Content != "" {
			if err := callback(chunk.Choices[0].Delta.Content); err != nil {
				return err
			}
		}
	}

	return nil
}
