// Package service 对话服务层
// @author <a href="https://codefather.cn">编程导航学习圈</a>
package service

import (
	"ai-test-go/internal/config"
	"ai-test-go/internal/constant"
	"ai-test-go/internal/model"
	"ai-test-go/internal/model/dto"
	"ai-test-go/internal/model/vo"
	"ai-test-go/internal/repository"
	"ai-test-go/pkg/common"
	"ai-test-go/pkg/llm"
	"ai-test-go/pkg/utils"
	"context"
	"encoding/json"
	"log"
	"sync"
	"time"
)

type ConversationService struct {
	conversationRepo        *repository.ConversationRepository
	conversationMessageRepo *repository.ConversationMessageRepository
	langchainAdapter        *llm.LangChainAdapter
	modelRepo               *repository.ModelRepository
}

func (s *ConversationService) createAssistantMessage(conversationID string, userID int64, messageIndex int, modelName, content string, responseTimeMs, inputTokens, outputTokens int) *model.ConversationMessage {
	cost := s.calculateCostByModel(modelName, inputTokens, outputTokens)

	return &model.ConversationMessage{
		ID:             utils.GenerateUUID(),
		ConversationID: conversationID,
		UserID:         userID,
		MessageIndex:   messageIndex,
		Role:           "assistant",
		ModelName:      modelName,
		Content:        content,
		ResponseTimeMs: responseTimeMs,
		InputTokens:    inputTokens,
		OutputTokens:   outputTokens,
		Cost:           cost,
		CreateTime:     time.Now(),
		UpdateTime:     time.Now(),
		IsDelete:       0,
	}
}

func max(a, b int) int {
	if a > b {
		return a
	}
	return b
}

func min(a, b int) int {
	if a < b {
		return a
	}
	return b
}

func NewConversationService(
	conversationRepo *repository.ConversationRepository,
	conversationMessageRepo *repository.ConversationMessageRepository,
	modelRepo *repository.ModelRepository,
) *ConversationService {
	adapter, err := llm.NewLangChainAdapter(
		config.AppConfig.OpenRouter.APIKey,
		config.AppConfig.OpenRouter.BaseURL,
	)
	if err != nil {
		log.Fatalf("初始化LangChain适配器失败: %v", err)
	}

	return &ConversationService{
		conversationRepo:        conversationRepo,
		conversationMessageRepo: conversationMessageRepo,
		langchainAdapter:        adapter,
		modelRepo:               modelRepo,
	}
}

func (s *ConversationService) CreateConversation(req *dto.CreateConversationRequest, userID int64) (string, error) {
	if req.ConversationType == "" {
		return "", common.NewBusinessException(common.PARAMS_ERROR, "对话类型不能为空")
	}
	if len(req.Models) == 0 {
		return "", common.NewBusinessException(common.PARAMS_ERROR, "模型列表不能为空")
	}

	conversationID := utils.GenerateUUID()
	title := req.Title
	if title == "" {
		title = "新对话"
	}

	modelsJSON, _ := json.Marshal(req.Models)

	conversation := &model.Conversation{
		ID:                 conversationID,
		UserID:             userID,
		Title:              title,
		ConversationType:   req.ConversationType,
		CodePreviewEnabled: req.CodePreviewEnabled,
		Models:             string(modelsJSON),
		TotalTokens:        0,
		TotalCost:          0,
		CreateTime:         time.Now(),
		UpdateTime:         time.Now(),
		IsDelete:           0,
	}

	if err := s.conversationRepo.Create(conversation); err != nil {
		return "", common.NewBusinessException(common.OPERATION_ERROR, "创建对话失败")
	}

	return conversationID, nil
}

func (s *ConversationService) GetConversation(conversationID string, userID int64) (*model.Conversation, error) {
	if conversationID == "" {
		return nil, common.NewBusinessException(common.PARAMS_ERROR, "对话ID不能为空")
	}

	conversation, err := s.conversationRepo.FindByID(conversationID, userID)
	if err != nil {
		return nil, common.NewBusinessException(common.NOT_FOUND_ERROR, "对话不存在")
	}

	return conversation, nil
}

func (s *ConversationService) ListConversations(userID int64, pageNum, pageSize int64, codePreviewEnabled *bool) ([]model.Conversation, int64, error) {
	conversations, total, err := s.conversationRepo.ListByUser(userID, pageNum, pageSize, codePreviewEnabled)
	if err != nil {
		return nil, 0, common.NewBusinessException(common.SYSTEM_ERROR, "查询对话列表失败")
	}

	return conversations, total, nil
}

func (s *ConversationService) GetConversationMessages(conversationID string, userID int64) ([]model.ConversationMessage, error) {
	_, err := s.GetConversation(conversationID, userID)
	if err != nil {
		return nil, err
	}

	messages, err := s.conversationMessageRepo.ListByConversation(conversationID)
	if err != nil {
		return nil, common.NewBusinessException(common.SYSTEM_ERROR, "查询消息列表失败")
	}

	return messages, nil
}

func (s *ConversationService) DeleteConversation(conversationID string, userID int64) error {
	if conversationID == "" {
		return common.NewBusinessException(common.PARAMS_ERROR, "对话ID不能为空")
	}

	if err := s.conversationRepo.Delete(conversationID, userID); err != nil {
		return common.NewBusinessException(common.OPERATION_ERROR, "删除对话失败")
	}

	return nil
}

func (s *ConversationService) SideBySideStream(req *dto.SideBySideRequest, userID int64, onChunk func(chunk vo.StreamChunkVO) error) error {
	if len(req.Models) == 0 || len(req.Models) > 8 {
		return common.NewBusinessException(common.PARAMS_ERROR, "模型数量必须在1-8之间")
	}
	if req.Prompt == "" {
		return common.NewBusinessException(common.PARAMS_ERROR, "提示词不能为空")
	}

	conversationID := req.ConversationID
	if conversationID == "" {
		createReq := &dto.CreateConversationRequest{
			Title:            s.generateTitle(req.Prompt),
			ConversationType: "side_by_side",
			Models:           req.Models,
		}
		var err error
		conversationID, err = s.CreateConversation(createReq, userID)
		if err != nil {
			return err
		}
	}

	messageIndex, err := s.conversationMessageRepo.GetNextMessageIndex(conversationID)
	if err != nil {
		return common.NewBusinessException(common.SYSTEM_ERROR, "获取消息索引失败")
	}

	userMessage := &model.ConversationMessage{
		ID:             utils.GenerateUUID(),
		ConversationID: conversationID,
		UserID:         userID,
		MessageIndex:   messageIndex,
		Role:           "user",
		Content:        req.Prompt,
		CreateTime:     time.Now(),
		UpdateTime:     time.Now(),
		IsDelete:       0,
	}
	if err := s.conversationMessageRepo.Create(userMessage); err != nil {
		log.Printf("保存user消息失败: %v", err)
		return common.NewBusinessException(common.SYSTEM_ERROR, "保存消息失败")
	}

	var wg sync.WaitGroup
	ctx := context.Background()

	for _, modelName := range req.Models {
		wg.Add(1)
		go func(model string) {
			defer wg.Done()

			historyMessages, err := s.getHistoryMessagesForContext(conversationID, messageIndex, nil)
			if err != nil {
				log.Printf("加载历史消息失败: %v", err)
			}

			langchainMessages := make([]llm.Message, 0)
			for _, msg := range historyMessages {
				if msg.Role == "user" {
					langchainMessages = append(langchainMessages, llm.Message{
						Role:    "user",
						Content: msg.Content,
					})
				} else if msg.Role == "assistant" && msg.ModelName == model {
					langchainMessages = append(langchainMessages, llm.Message{
						Role:    "assistant",
						Content: msg.Content,
					})
				}
			}

			startTime := time.Now()
			fullContent := ""
			fullReasoning := ""
			outputTokens := 0

			err = s.langchainAdapter.CallStreamWithHistory(ctx, langchainMessages, req.Prompt, model, func(data llm.StreamData) error {
				fullContent += data.Content
				fullReasoning += data.Reasoning
				outputTokens += (len(data.Content) + len(data.Reasoning)) / 4

				chunk := vo.StreamChunkVO{
					ConversationID: conversationID,
					ModelName:      model,
					Content:        data.Content,
					FullContent:    fullContent,
					Reasoning:      fullReasoning,
					HasReasoning:   fullReasoning != "",
					OutputTokens:   outputTokens,
					ElapsedMs:      time.Since(startTime).Milliseconds(),
					Done:           false,
					HasError:       false,
				}

				return onChunk(chunk)
			})

			responseTimeMs := int(time.Since(startTime).Milliseconds())

			if err != nil {
				log.Printf("模型 %s 调用失败: %v", model, err)
				errorChunk := vo.StreamChunkVO{
					ConversationID: conversationID,
					ModelName:      model,
					Error:          err.Error(),
					HasError:       true,
					Done:           true,
				}
				onChunk(errorChunk)
				return
			}

			thinkingTime := 0
			if fullReasoning != "" {
				thinkingTime = max(1, min(len(fullReasoning)/200, 60))
			}

			inputTokens := len(req.Prompt) / 4
			totalTokens := inputTokens + outputTokens
			cost := s.calculateCostByModel(model, inputTokens, outputTokens)

			doneChunk := vo.StreamChunkVO{
				ConversationID: conversationID,
				ModelName:      model,
				FullContent:    fullContent,
				Reasoning:      fullReasoning,
				HasReasoning:   fullReasoning != "",
				ThinkingTime:   thinkingTime,
				InputTokens:    inputTokens,
				OutputTokens:   outputTokens,
				TotalTokens:    totalTokens,
				Cost:           cost,
				ResponseTimeMs: responseTimeMs,
				Done:           true,
				HasError:       false,
			}
			onChunk(doneChunk)

			assistantMessageIndex := messageIndex + 1
			assistantMessage := s.createAssistantMessage(conversationID, userID, assistantMessageIndex, model, fullContent, responseTimeMs, inputTokens, outputTokens)
			if fullReasoning != "" {
				assistantMessage.Reasoning = fullReasoning
			}
			if err := s.conversationMessageRepo.Create(assistantMessage); err != nil {
				log.Printf("保存assistant消息失败: %v", err)
			}
		}(modelName)
	}

	wg.Wait()
	return nil
}

func (s *ConversationService) PromptLabStream(req *dto.PromptLabRequest, userID int64, onChunk func(chunk vo.StreamChunkVO) error) error {
	if req.Model == "" {
		return common.NewBusinessException(common.PARAMS_ERROR, "模型不能为空")
	}
	if len(req.PromptVariants) < constant.MIN_PROMPT_VARIANTS_COUNT || len(req.PromptVariants) > constant.MAX_PROMPT_VARIANTS_COUNT {
		return common.NewBusinessException(common.PARAMS_ERROR, "提示词变体数量必须在2-5之间")
	}

	conversationID := req.ConversationID
	if conversationID == "" {
		createReq := &dto.CreateConversationRequest{
			Title:            s.generateTitle(req.PromptVariants[0]),
			ConversationType: "prompt_lab",
			Models:           []string{req.Model},
		}
		var err error
		conversationID, err = s.CreateConversation(createReq, userID)
		if err != nil {
			return err
		}
	}

	userMessageIndex, err := s.conversationMessageRepo.GetNextMessageIndex(conversationID)
	if err != nil {
		return common.NewBusinessException(common.SYSTEM_ERROR, "获取消息索引失败")
	}
	assistantMessageIndex := userMessageIndex + 1

	for i, promptVariant := range req.PromptVariants {
		variantIndex := i
		userMessage := &model.ConversationMessage{
			ID:             utils.GenerateUUID(),
			ConversationID: conversationID,
			UserID:         userID,
			MessageIndex:   userMessageIndex,
			Role:           "user",
			VariantIndex:   &variantIndex,
			Content:        promptVariant,
			CreateTime:     time.Now(),
			UpdateTime:     time.Now(),
			IsDelete:       0,
		}
		if err := s.conversationMessageRepo.Create(userMessage); err != nil {
			log.Printf("保存user消息失败: %v", err)
			return common.NewBusinessException(common.SYSTEM_ERROR, "保存消息失败")
		}
	}

	var wg sync.WaitGroup
	ctx := context.Background()

	for i, promptVariant := range req.PromptVariants {
		wg.Add(1)
		variantIndex := i
		go func(variant string, vIndex int) {
			defer wg.Done()

			historyMessages, err := s.getHistoryMessagesForContext(conversationID, userMessageIndex, &vIndex)
			if err != nil {
				log.Printf("加载历史消息失败: %v", err)
			}

			langchainMessages := make([]llm.Message, 0)
			for _, msg := range historyMessages {
				if msg.Role == "user" {
					langchainMessages = append(langchainMessages, llm.Message{
						Role:    "user",
						Content: msg.Content,
					})
				} else if msg.Role == "assistant" && msg.ModelName == req.Model {
					if msg.VariantIndex != nil && *msg.VariantIndex == vIndex {
						langchainMessages = append(langchainMessages, llm.Message{
							Role:    "assistant",
							Content: msg.Content,
						})
					}
				}
			}

			startTime := time.Now()
			fullContent := ""
			fullReasoning := ""
			outputTokens := 0

			err = s.langchainAdapter.CallStreamWithHistory(ctx, langchainMessages, variant, req.Model, func(data llm.StreamData) error {
				fullContent += data.Content
				fullReasoning += data.Reasoning
				outputTokens += (len(data.Content) + len(data.Reasoning)) / 4

				chunk := vo.StreamChunkVO{
					ConversationID: conversationID,
					ModelName:      req.Model,
					VariantIndex:   &vIndex,
					MessageIndex:   &assistantMessageIndex,
					Content:        data.Content,
					FullContent:    fullContent,
					Reasoning:      fullReasoning,
					HasReasoning:   fullReasoning != "",
					OutputTokens:   outputTokens,
					ElapsedMs:      time.Since(startTime).Milliseconds(),
					Done:           false,
					HasError:       false,
				}

				return onChunk(chunk)
			})

			responseTimeMs := int(time.Since(startTime).Milliseconds())

			if err != nil {
				log.Printf("模型 %s 变体 %d 调用失败: %v", req.Model, vIndex, err)
				errorChunk := vo.StreamChunkVO{
					ConversationID: conversationID,
					ModelName:      req.Model,
					VariantIndex:   &vIndex,
					MessageIndex:   &assistantMessageIndex,
					Error:          err.Error(),
					HasError:       true,
					Done:           true,
				}
				onChunk(errorChunk)
				return
			}

			thinkingTime := 0
			if fullReasoning != "" {
				thinkingTime = max(1, min(len(fullReasoning)/200, 60))
			}

			inputTokens := len(variant) / 4
			totalTokens := inputTokens + outputTokens
			cost := s.calculateCostByModel(req.Model, inputTokens, outputTokens)

			doneChunk := vo.StreamChunkVO{
				ConversationID: conversationID,
				ModelName:      req.Model,
				VariantIndex:   &vIndex,
				MessageIndex:   &assistantMessageIndex,
				FullContent:    fullContent,
				Reasoning:      fullReasoning,
				HasReasoning:   fullReasoning != "",
				ThinkingTime:   thinkingTime,
				InputTokens:    inputTokens,
				OutputTokens:   outputTokens,
				TotalTokens:    totalTokens,
				Cost:           cost,
				ResponseTimeMs: responseTimeMs,
				Done:           true,
				HasError:       false,
			}
			onChunk(doneChunk)

			assistantMessage := s.createAssistantMessage(conversationID, userID, assistantMessageIndex, req.Model, fullContent, responseTimeMs, inputTokens, outputTokens)
			assistantMessage.VariantIndex = &vIndex
			if fullReasoning != "" {
				assistantMessage.Reasoning = fullReasoning
			}
			if err := s.conversationMessageRepo.Create(assistantMessage); err != nil {
				log.Printf("保存assistant消息失败: %v", err)
			}
		}(promptVariant, variantIndex)
	}

	wg.Wait()
	return nil
}

func (s *ConversationService) ChatStream(req *dto.ChatRequest, userID int64, onChunk func(chunk vo.StreamChunkVO) error) error {
	if req.Model == "" {
		return common.NewBusinessException(common.PARAMS_ERROR, "模型名称不能为空")
	}
	if req.Message == "" {
		return common.NewBusinessException(common.PARAMS_ERROR, "消息内容不能为空")
	}

	conversationID := req.ConversationID
	if conversationID == "" {
		createReq := &dto.CreateConversationRequest{
			Title:            s.generateTitle(req.Message),
			ConversationType: "chat",
			Models:           []string{req.Model},
		}
		var err error
		conversationID, err = s.CreateConversation(createReq, userID)
		if err != nil {
			return err
		}
	}

	messageIndex, err := s.conversationMessageRepo.GetNextMessageIndex(conversationID)
	if err != nil {
		return common.NewBusinessException(common.SYSTEM_ERROR, "获取消息索引失败")
	}

	userMessage := &model.ConversationMessage{
		ID:             utils.GenerateUUID(),
		ConversationID: conversationID,
		UserID:         userID,
		MessageIndex:   messageIndex,
		Role:           "user",
		Content:        req.Message,
		CreateTime:     time.Now(),
		UpdateTime:     time.Now(),
		IsDelete:       0,
	}
	if err := s.conversationMessageRepo.Create(userMessage); err != nil {
		log.Printf("保存user消息失败: %v", err)
		return common.NewBusinessException(common.SYSTEM_ERROR, "保存消息失败")
	}

	historyMessages, err := s.getHistoryMessagesForContext(conversationID, messageIndex, nil)
	if err != nil {
		log.Printf("加载历史消息失败: %v", err)
	}

	langchainMessages := make([]llm.Message, 0)
	for _, msg := range historyMessages {
		if msg.Role == "user" {
			langchainMessages = append(langchainMessages, llm.Message{
				Role:    "user",
				Content: msg.Content,
			})
		} else if msg.Role == "assistant" && msg.ModelName == req.Model {
			langchainMessages = append(langchainMessages, llm.Message{
				Role:    "assistant",
				Content: msg.Content,
			})
		}
	}

	ctx := context.Background()
	startTime := time.Now()
	fullContent := ""
	fullReasoning := ""
	outputTokens := 0

	err = s.langchainAdapter.CallStreamWithHistory(ctx, langchainMessages, req.Message, req.Model, func(data llm.StreamData) error {
		fullContent += data.Content
		fullReasoning += data.Reasoning
		outputTokens += (len(data.Content) + len(data.Reasoning)) / 4

		chunk := vo.StreamChunkVO{
			ConversationID: conversationID,
			ModelName:      req.Model,
			Content:        data.Content,
			FullContent:    fullContent,
			Reasoning:      fullReasoning,
			HasReasoning:   fullReasoning != "",
			OutputTokens:   outputTokens,
			ElapsedMs:      time.Since(startTime).Milliseconds(),
			Done:           false,
			HasError:       false,
		}

		return onChunk(chunk)
	})

	responseTimeMs := int(time.Since(startTime).Milliseconds())

	if err != nil {
		log.Printf("AI调用失败: %v", err)
		errorChunk := vo.StreamChunkVO{
			ConversationID: conversationID,
			ModelName:      req.Model,
			Error:          err.Error(),
			HasError:       true,
			Done:           true,
		}
		return onChunk(errorChunk)
	}

	thinkingTime := 0
	if fullReasoning != "" {
		thinkingTime = max(1, min(len(fullReasoning)/200, 60))
	}

	inputTokens := len(req.Message) / 4
	totalTokens := inputTokens + outputTokens
	cost := s.calculateCostByModel(req.Model, inputTokens, outputTokens)

	doneChunk := vo.StreamChunkVO{
		ConversationID: conversationID,
		ModelName:      req.Model,
		FullContent:    fullContent,
		Reasoning:      fullReasoning,
		HasReasoning:   fullReasoning != "",
		ThinkingTime:   thinkingTime,
		InputTokens:    inputTokens,
		OutputTokens:   outputTokens,
		TotalTokens:    totalTokens,
		Cost:           cost,
		ResponseTimeMs: responseTimeMs,
		Done:           true,
		HasError:       false,
	}
	onChunk(doneChunk)

	assistantMessageIndex := messageIndex + 1
	assistantMessage := s.createAssistantMessage(conversationID, userID, assistantMessageIndex, req.Model, fullContent, responseTimeMs, inputTokens, outputTokens)
	if fullReasoning != "" {
		assistantMessage.Reasoning = fullReasoning
	}
	if err := s.conversationMessageRepo.Create(assistantMessage); err != nil {
		log.Printf("保存assistant消息失败: %v", err)
		return err
	}

	return nil
}

func (s *ConversationService) calculateCostByModel(modelID string, inputTokens, outputTokens int) float64 {
	model, err := s.modelRepo.FindByID(modelID)
	if err != nil || model == nil {
		log.Printf("查询模型%s价格失败，使用默认价格: %v", modelID, err)
		return s.calculateCostWithDefaultPrice(inputTokens, outputTokens)
	}

	if model.InputPrice > 0 && model.OutputPrice > 0 {
		inputCost := (float64(inputTokens) / constant.TOKENS_PER_MILLION) * model.InputPrice
		outputCost := (float64(outputTokens) / constant.TOKENS_PER_MILLION) * model.OutputPrice
		return inputCost + outputCost
	}

	return s.calculateCostWithDefaultPrice(inputTokens, outputTokens)
}

func (s *ConversationService) calculateCostWithDefaultPrice(inputTokens, outputTokens int) float64 {
	inputCost := (float64(inputTokens) / constant.TOKENS_PER_MILLION) * constant.DEFAULT_INPUT_PRICE_PER_MILLION
	outputCost := (float64(outputTokens) / constant.TOKENS_PER_MILLION) * constant.DEFAULT_OUTPUT_PRICE_PER_MILLION
	return inputCost + outputCost
}

func (s *ConversationService) getHistoryMessagesForContext(conversationID string, excludeMessageIndex int, variantIndex *int) ([]model.ConversationMessage, error) {
	messages, err := s.conversationMessageRepo.ListByConversation(conversationID)
	if err != nil {
		return nil, err
	}

	var filteredMessages []model.ConversationMessage
	for _, msg := range messages {
		if msg.MessageIndex >= excludeMessageIndex {
			continue
		}

		if variantIndex != nil {
			if msg.VariantIndex != nil && *msg.VariantIndex == *variantIndex {
				filteredMessages = append(filteredMessages, msg)
			} else if msg.VariantIndex == nil {
				filteredMessages = append(filteredMessages, msg)
			}
		} else {
			filteredMessages = append(filteredMessages, msg)
		}
	}

	log.Printf("📚 加载历史消息: 会话ID=%s, variantIndex=%v, 数量=%d", conversationID, variantIndex, len(filteredMessages))
	return filteredMessages, nil
}

func (s *ConversationService) generateTitle(prompt string) string {
	runes := []rune(prompt)
	if len(runes) > constant.MAX_TITLE_LENGTH {
		return string(runes[:constant.MAX_TITLE_LENGTH]) + "..."
	}
	return prompt
}
