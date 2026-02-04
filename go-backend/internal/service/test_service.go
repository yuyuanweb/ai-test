// Package service 测试服务层
// @author <a href="https://codefather.cn">编程导航学习圈</a>
package service

import (
	"ai-test-go/internal/config"
	"ai-test-go/internal/model/vo"
	"ai-test-go/pkg/llm"
	"context"
	"log"
)

type TestService struct {
	langchainAdapter *llm.LangChainAdapter
}

func NewTestService() *TestService {
	adapter, err := llm.NewLangChainAdapter(
		config.AppConfig.OpenRouter.APIKey,
		config.AppConfig.OpenRouter.BaseURL,
	)
	if err != nil {
		log.Fatalf("初始化LangChain适配器失败: %v", err)
	}

	return &TestService{
		langchainAdapter: adapter,
	}
}

func (s *TestService) TestAISimple(prompt, model string) (string, error) {
	if model == "" {
		model = "qwen/qwen-plus"
	}

	ctx := context.Background()
	response, err := s.langchainAdapter.Call(ctx, prompt, model)
	if err != nil {
		return "", err
	}

	return response, nil
}

func (s *TestService) TestAIStream(prompt, model string, onChunk func(chunk vo.StreamChunkVO) error) error {
	if model == "" {
		model = "qwen/qwen-plus"
	}

	ctx := context.Background()
	return s.langchainAdapter.CallStream(ctx, prompt, model, func(content string) error {
		if content == "" {
			return nil
		}

		chunkVO := vo.StreamChunkVO{
			ModelName: model,
			Content:   content,
			Done:      false,
			HasError:  false,
		}

		return onChunk(chunkVO)
	})
}
