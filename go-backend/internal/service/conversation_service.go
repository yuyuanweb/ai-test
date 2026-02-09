// Package service 对话服务层
// @author <a href="https://codefather.cn">编程导航学习圈</a>
package service

import (
	"ai-test-go/internal/config"
	"ai-test-go/internal/constant"
	"ai-test-go/internal/guardrail"
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
	"math/rand"
	"regexp"
	"strings"
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

func parseImagesJSON(s *string) []string {
	if s == nil || strings.TrimSpace(*s) == "" {
		return nil
	}
	var urls []string
	if err := json.Unmarshal([]byte(*s), &urls); err != nil {
		return nil
	}
	return urls
}

const (
	webSearchEmptyQueryPlaceholder = "用户请求"
	maxWebSources                  = 5
)

var urlInTextRegex = regexp.MustCompile(`https?://[^\s)\]}>"']+`)

func extractURLsFromContent(content string) []string {
	if strings.TrimSpace(content) == "" {
		return nil
	}
	matches := urlInTextRegex.FindAllString(content, -1)
	seen := make(map[string]bool)
	var urls []string
	for _, u := range matches {
		u = strings.TrimRight(u, ".,;:!?)")
		if u != "" && !seen[u] {
			seen[u] = true
			urls = append(urls, u)
			if len(urls) >= maxWebSources {
				break
			}
		}
	}
	return urls
}

func buildToolsUsedJSON(webSearchEnabled bool, query string, fullContent string) *string {
	if !webSearchEnabled {
		return nil
	}
	if strings.TrimSpace(query) == "" {
		query = webSearchEmptyQueryPlaceholder
	}
	sourceUrls := extractURLsFromContent(fullContent)
	sources := make([]map[string]string, 0, len(sourceUrls))
	for _, u := range sourceUrls {
		sources = append(sources, map[string]string{"url": u})
	}
	m := map[string]interface{}{
		"webSearch": map[string]interface{}{
			"enabled": true,
			"query":   query,
			"engine":  "auto",
			"sources": sources,
		},
	}
	data, err := json.Marshal(m)
	if err != nil {
		return nil
	}
	str := string(data)
	return &str
}

func effectiveModelForDB(model string, webSearchEnabled bool) string {
	if !webSearchEnabled || model == "" {
		return model
	}
	if strings.Contains(model, ":online") {
		return model
	}
	return model + ":online"
}

func historyToLLMMessages(history []model.ConversationMessage, modelFilter string, variantIndex *int) []llm.Message {
	out := make([]llm.Message, 0)
	for _, msg := range history {
		if msg.Role == "user" {
			out = append(out, llm.Message{
				Role:      "user",
				Content:   msg.Content,
				ImageUrls: parseImagesJSON(msg.Images),
			})
		} else if msg.Role == "assistant" {
			modelMatch := modelFilter == "" || msg.ModelName == modelFilter || strings.TrimSuffix(msg.ModelName, ":online") == modelFilter
			if !modelMatch {
				continue
			}
			if variantIndex != nil && msg.VariantIndex != nil && *msg.VariantIndex != *variantIndex {
				continue
			}
			out = append(out, llm.Message{Role: "assistant", Content: msg.Content})
		}
	}
	return out
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
	if err := guardrail.Validate(req.Prompt); err != nil {
		return err
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
	if len(req.ImageUrls) > 0 {
		imagesJSON, _ := json.Marshal(req.ImageUrls)
		s := string(imagesJSON)
		userMessage.Images = &s
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

			langchainMessages := historyToLLMMessages(historyMessages, model, nil)

			startTime := time.Now()
			fullContent := ""
			fullReasoning := ""
			outputTokens := 0

			err = s.langchainAdapter.CallStreamWithHistory(ctx, langchainMessages, req.Prompt, req.ImageUrls, model, req.WebSearchEnabled, func(data llm.StreamData) error {
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

			toolsUsedStr := buildToolsUsedJSON(req.WebSearchEnabled, req.Prompt, fullContent)
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
			if toolsUsedStr != nil {
				doneChunk.ToolsUsed = *toolsUsedStr
			}
			onChunk(doneChunk)

			assistantMessageIndex := messageIndex + 1
			assistantMessage := s.createAssistantMessage(conversationID, userID, assistantMessageIndex, effectiveModelForDB(model, req.WebSearchEnabled), fullContent, responseTimeMs, inputTokens, outputTokens)
			if fullReasoning != "" {
				assistantMessage.Reasoning = fullReasoning
			}
			assistantMessage.ToolsUsed = toolsUsedStr
			if err := s.conversationMessageRepo.Create(assistantMessage); err != nil {
				log.Printf("保存assistant消息失败: %v", err)
			}
		}(modelName)
	}

	wg.Wait()
	return nil
}

func getVariantImagesSafe(variantImageUrls [][]string, index int) []string {
	if variantImageUrls == nil || len(variantImageUrls) <= index {
		return nil
	}
	return variantImageUrls[index]
}

func (s *ConversationService) PromptLabStream(req *dto.PromptLabRequest, userID int64, onChunk func(chunk vo.StreamChunkVO) error) error {
	if req.Model == "" {
		return common.NewBusinessException(common.PARAMS_ERROR, "模型不能为空")
	}
	if len(req.PromptVariants) < constant.MIN_PROMPT_VARIANTS_COUNT || len(req.PromptVariants) > constant.MAX_PROMPT_VARIANTS_COUNT {
		return common.NewBusinessException(common.PARAMS_ERROR, "提示词变体数量必须在2-5之间")
	}
	for _, v := range req.PromptVariants {
		if err := guardrail.Validate(v); err != nil {
			return err
		}
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
		if imgs := getVariantImagesSafe(req.VariantImageUrls, i); len(imgs) > 0 {
			imagesJSON, _ := json.Marshal(imgs)
			s := string(imagesJSON)
			userMessage.Images = &s
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

			langchainMessages := historyToLLMMessages(historyMessages, req.Model, &vIndex)

			startTime := time.Now()
			fullContent := ""
			fullReasoning := ""
			outputTokens := 0

			variantImageUrls := getVariantImagesSafe(req.VariantImageUrls, vIndex)
			err = s.langchainAdapter.CallStreamWithHistory(ctx, langchainMessages, variant, variantImageUrls, req.Model, req.WebSearchEnabled, func(data llm.StreamData) error {
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

			toolsUsedStr := buildToolsUsedJSON(req.WebSearchEnabled, variant, fullContent)
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
			if toolsUsedStr != nil {
				doneChunk.ToolsUsed = *toolsUsedStr
			}
			onChunk(doneChunk)

			assistantMessage := s.createAssistantMessage(conversationID, userID, assistantMessageIndex, effectiveModelForDB(req.Model, req.WebSearchEnabled), fullContent, responseTimeMs, inputTokens, outputTokens)
			assistantMessage.VariantIndex = &vIndex
			if fullReasoning != "" {
				assistantMessage.Reasoning = fullReasoning
			}
			assistantMessage.ToolsUsed = toolsUsedStr
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
	if err := guardrail.Validate(req.Message); err != nil {
		return err
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
	if len(req.ImageUrls) > 0 {
		imagesJSON, _ := json.Marshal(req.ImageUrls)
		s := string(imagesJSON)
		userMessage.Images = &s
	}
	if err := s.conversationMessageRepo.Create(userMessage); err != nil {
		log.Printf("保存user消息失败: %v", err)
		return common.NewBusinessException(common.SYSTEM_ERROR, "保存消息失败")
	}

	historyMessages, err := s.getHistoryMessagesForContext(conversationID, messageIndex, nil)
	if err != nil {
		log.Printf("加载历史消息失败: %v", err)
	}

	langchainMessages := historyToLLMMessages(historyMessages, req.Model, nil)

	ctx := context.Background()
	startTime := time.Now()
	fullContent := ""
	fullReasoning := ""
	outputTokens := 0

	err = s.langchainAdapter.CallStreamWithHistory(ctx, langchainMessages, req.Message, req.ImageUrls, req.Model, req.WebSearchEnabled, func(data llm.StreamData) error {
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

	toolsUsedStr := buildToolsUsedJSON(req.WebSearchEnabled, req.Message, fullContent)
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
	if toolsUsedStr != nil {
		doneChunk.ToolsUsed = *toolsUsedStr
	}
	onChunk(doneChunk)

	assistantMessageIndex := messageIndex + 1
	assistantMessage := s.createAssistantMessage(conversationID, userID, assistantMessageIndex, effectiveModelForDB(req.Model, req.WebSearchEnabled), fullContent, responseTimeMs, inputTokens, outputTokens)
	if fullReasoning != "" {
		assistantMessage.Reasoning = fullReasoning
	}
	assistantMessage.ToolsUsed = toolsUsedStr
	if err := s.conversationMessageRepo.Create(assistantMessage); err != nil {
		log.Printf("保存assistant消息失败: %v", err)
		return err
	}

	return nil
}

func (s *ConversationService) calculateCostByModel(modelID string, inputTokens, outputTokens int) float64 {
	baseModelID := strings.TrimSuffix(modelID, ":online")
	model, err := s.modelRepo.FindByID(baseModelID)
	if err != nil || model == nil {
		log.Printf("查询模型%s价格失败，使用默认价格: %v", baseModelID, err)
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

func (s *ConversationService) CodeModeStream(req *dto.CodeModeRequest, userID int64, onChunk func(chunk vo.StreamChunkVO) error) error {
	if len(req.Models) == 0 || len(req.Models) > 8 {
		return common.NewBusinessException(common.PARAMS_ERROR, "模型数量必须在1-8之间")
	}
	if req.Prompt == "" {
		return common.NewBusinessException(common.PARAMS_ERROR, "需求描述不能为空")
	}
	if err := guardrail.Validate(req.Prompt); err != nil {
		return err
	}

	conversationID := req.ConversationID
	if conversationID == "" {
		createReq := &dto.CreateConversationRequest{
			Title:              s.generateTitle(req.Prompt),
			ConversationType:   "side_by_side",
			Models:             req.Models,
			CodePreviewEnabled: true,
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
	if len(req.ImageUrls) > 0 {
		imagesJSON, _ := json.Marshal(req.ImageUrls)
		s := string(imagesJSON)
		userMessage.Images = &s
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

			langchainMessages := historyToLLMMessages(historyMessages, model, nil)

			startTime := time.Now()
			fullContent := ""
			fullReasoning := ""
			outputTokens := 0

			err = s.langchainAdapter.CallStreamWithSystemPrompt(ctx, langchainMessages, req.Prompt, req.ImageUrls, constant.CODE_MODE_SYSTEM_PROMPT, model, req.WebSearchEnabled, func(data llm.StreamData) error {
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

			// 提取代码块
			codeBlocks := utils.ExtractCodeBlocks(fullContent)
			var codeBlocksJSON *string
			hasCodeBlocks := len(codeBlocks) > 0
			if hasCodeBlocks {
				codeBlocksData, _ := json.Marshal(codeBlocks)
				codeBlocksStr := string(codeBlocksData)
				codeBlocksJSON = &codeBlocksStr
				log.Printf("📝 提取代码块: 模型=%s, 数量=%d", model, len(codeBlocks))
			}

			inputTokens := len(req.Prompt) / 4
			totalTokens := inputTokens + outputTokens
			cost := s.calculateCostByModel(model, inputTokens, outputTokens)

			toolsUsedStr := buildToolsUsedJSON(req.WebSearchEnabled, req.Prompt, fullContent)
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
				CodeBlocks:     codeBlocks,
				HasCodeBlocks:  hasCodeBlocks,
				Done:           true,
				HasError:       false,
			}
			if toolsUsedStr != nil {
				doneChunk.ToolsUsed = *toolsUsedStr
			}
			onChunk(doneChunk)

			assistantMessageIndex := messageIndex + 1
			assistantMessage := s.createAssistantMessage(conversationID, userID, assistantMessageIndex, effectiveModelForDB(model, req.WebSearchEnabled), fullContent, responseTimeMs, inputTokens, outputTokens)
			if fullReasoning != "" {
				assistantMessage.Reasoning = fullReasoning
			}
			assistantMessage.CodeBlocks = codeBlocksJSON
			assistantMessage.ToolsUsed = toolsUsedStr
			if err := s.conversationMessageRepo.Create(assistantMessage); err != nil {
				log.Printf("保存assistant消息失败: %v", err)
			}
		}(modelName)
	}

	wg.Wait()
	return nil
}

func (s *ConversationService) CodeModePromptLabStream(req *dto.CodeModePromptLabRequest, userID int64, onChunk func(chunk vo.StreamChunkVO) error) error {
	if req.Model == "" {
		return common.NewBusinessException(common.PARAMS_ERROR, "模型不能为空")
	}
	if len(req.PromptVariants) < constant.MIN_PROMPT_VARIANTS_COUNT || len(req.PromptVariants) > constant.MAX_PROMPT_VARIANTS_COUNT {
		return common.NewBusinessException(common.PARAMS_ERROR, "提示词变体数量必须在2-5之间")
	}
	for _, v := range req.PromptVariants {
		if err := guardrail.Validate(v); err != nil {
			return err
		}
	}

	conversationID := req.ConversationID
	if conversationID == "" {
		createReq := &dto.CreateConversationRequest{
			Title:              s.generateTitle(req.PromptVariants[0]),
			ConversationType:   "prompt_lab",
			Models:             []string{req.Model},
			CodePreviewEnabled: true,
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
		if imgs := getVariantImagesSafe(req.VariantImageUrls, i); len(imgs) > 0 {
			imagesJSON, _ := json.Marshal(imgs)
			s := string(imagesJSON)
			userMessage.Images = &s
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

			langchainMessages := historyToLLMMessages(historyMessages, req.Model, &vIndex)

			startTime := time.Now()
			fullContent := ""
			fullReasoning := ""
			outputTokens := 0

			variantImageUrls := getVariantImagesSafe(req.VariantImageUrls, vIndex)
			err = s.langchainAdapter.CallStreamWithSystemPrompt(ctx, langchainMessages, variant, variantImageUrls, constant.CODE_MODE_SYSTEM_PROMPT, req.Model, req.WebSearchEnabled, func(data llm.StreamData) error {
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

			// 提取代码块
			codeBlocks := utils.ExtractCodeBlocks(fullContent)
			var codeBlocksJSON *string
			hasCodeBlocks := len(codeBlocks) > 0
			if hasCodeBlocks {
				codeBlocksData, _ := json.Marshal(codeBlocks)
				codeBlocksStr := string(codeBlocksData)
				codeBlocksJSON = &codeBlocksStr
				log.Printf("📝 提取代码块: 模型=%s, 变体=%d, 数量=%d", req.Model, vIndex, len(codeBlocks))
			}

			inputTokens := len(variant) / 4
			totalTokens := inputTokens + outputTokens
			cost := s.calculateCostByModel(req.Model, inputTokens, outputTokens)

			toolsUsedStr := buildToolsUsedJSON(req.WebSearchEnabled, variant, fullContent)
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
				CodeBlocks:     codeBlocks,
				HasCodeBlocks:  hasCodeBlocks,
				Done:           true,
				HasError:       false,
			}
			if toolsUsedStr != nil {
				doneChunk.ToolsUsed = *toolsUsedStr
			}
			onChunk(doneChunk)

			assistantMessage := s.createAssistantMessage(conversationID, userID, assistantMessageIndex, effectiveModelForDB(req.Model, req.WebSearchEnabled), fullContent, responseTimeMs, inputTokens, outputTokens)
			assistantMessage.VariantIndex = &vIndex
			if fullReasoning != "" {
				assistantMessage.Reasoning = fullReasoning
			}
			assistantMessage.CodeBlocks = codeBlocksJSON
			assistantMessage.ToolsUsed = toolsUsedStr
			if err := s.conversationMessageRepo.Create(assistantMessage); err != nil {
				log.Printf("保存assistant消息失败: %v", err)
			}
		}(promptVariant, variantIndex)
	}

	wg.Wait()
	return nil
}

const (
	battleAnonymousLabelA = "模型A"
	battleAnonymousLabelB = "模型B"
)

func (s *ConversationService) BattleStream(req *dto.BattleRequest, userID int64, onChunk func(chunk vo.StreamChunkVO) error) error {
	if strings.TrimSpace(req.Prompt) == "" {
		return common.NewBusinessException(common.PARAMS_ERROR, "提示词不能为空")
	}
	if err := guardrail.Validate(req.Prompt); err != nil {
		return err
	}

	hasImages := len(req.ImageUrls) > 0
	var models []string
	var modelMapping map[string]string
	var conversationID string
	var codePreviewEnabled bool

	if req.ConversationID != "" {
		existing, err := s.conversationRepo.FindByID(req.ConversationID, userID)
		if err != nil {
			return common.NewBusinessException(common.NOT_FOUND_ERROR, "对话不存在")
		}
		conversationID = existing.ID
		codePreviewEnabled = existing.CodePreviewEnabled
		if existing.ModelMapping == nil || strings.TrimSpace(*existing.ModelMapping) == "" {
			log.Printf("Battle模式：对话 %s 的 modelMapping 为空", conversationID)
			return common.NewBusinessException(common.SYSTEM_ERROR, "对话的模型映射不存在")
		}
		if err := json.Unmarshal([]byte(*existing.ModelMapping), &modelMapping); err != nil {
			return common.NewBusinessException(common.SYSTEM_ERROR, "解析模型映射失败")
		}
		modelA := modelMapping[battleAnonymousLabelA]
		modelB := modelMapping[battleAnonymousLabelB]
		if modelA == "" || modelB == "" {
			return common.NewBusinessException(common.SYSTEM_ERROR, "对话的模型映射不完整")
		}
		models = []string{modelA, modelB}
		log.Printf("Battle模式：使用已有对话的模型映射，conversationId=%s", conversationID)
	} else {
		if len(req.Models) == 0 {
			allModels, err := s.modelRepo.GetAll()
			if err != nil || len(allModels) < constant.MIN_BATTLE_MODELS_COUNT {
				return common.NewBusinessException(common.PARAMS_ERROR, "可用模型数量不足，无法进行Battle对比")
			}
			if hasImages {
				var multimodal []model.Model
				for i := range allModels {
					if allModels[i].SupportsMultimodal == 1 {
						multimodal = append(multimodal, allModels[i])
					}
				}
				if len(multimodal) < constant.MIN_BATTLE_MODELS_COUNT {
					return common.NewBusinessException(common.PARAMS_ERROR, "支持多模态的模型数量不足，无法进行图片对比")
				}
				allModels = multimodal
			}
			var chinaModels []model.Model
			for i := range allModels {
				if allModels[i].IsChina == 1 {
					chinaModels = append(chinaModels, allModels[i])
				}
			}
			if len(chinaModels) >= constant.MIN_BATTLE_MODELS_COUNT {
				rand.Shuffle(len(chinaModels), func(i, j int) { chinaModels[i], chinaModels[j] = chinaModels[j], chinaModels[i] })
				models = []string{chinaModels[0].ID, chinaModels[1].ID}
			} else if len(chinaModels) == 1 {
				rand.Shuffle(len(allModels), func(i, j int) { allModels[i], allModels[j] = allModels[j], allModels[i] })
				for _, m := range allModels {
					if m.ID != chinaModels[0].ID {
						models = []string{chinaModels[0].ID, m.ID}
						break
					}
				}
				if len(models) < 2 {
					return common.NewBusinessException(common.PARAMS_ERROR, "可用模型数量不足，无法进行Battle对比")
				}
			} else {
				rand.Shuffle(len(allModels), func(i, j int) { allModels[i], allModels[j] = allModels[j], allModels[i] })
				models = []string{allModels[0].ID, allModels[1].ID}
			}
			log.Printf("Battle模式：随机选择模型: %v", models)
		} else if len(req.Models) < constant.MIN_BATTLE_MODELS_COUNT {
			return common.NewBusinessException(common.PARAMS_ERROR, "Battle模式至少需要2个模型")
		} else {
			models = []string{req.Models[0], req.Models[1]}
			log.Printf("Battle模式：使用前2个模型: %v", models)
		}
		modelMapping = map[string]string{battleAnonymousLabelA: models[0], battleAnonymousLabelB: models[1]}
		conversationID = utils.GenerateUUID()
		codePreviewEnabled = req.CodePreviewEnabled
		modelsJSON, _ := json.Marshal(models)
		mappingJSON, _ := json.Marshal(modelMapping)
		mappingStr := string(mappingJSON)
		conv := &model.Conversation{
			ID:                 conversationID,
			UserID:             userID,
			Title:              s.generateTitle(req.Prompt),
			ConversationType:   constant.ConversationTypeBattle,
			CodePreviewEnabled: codePreviewEnabled,
			IsAnonymous:        true,
			ModelMapping:       &mappingStr,
			Models:             string(modelsJSON),
			TotalTokens:        0,
			TotalCost:          0,
			CreateTime:         time.Now(),
			UpdateTime:         time.Now(),
			IsDelete:           0,
		}
		if err := s.conversationRepo.Create(conv); err != nil {
			return common.NewBusinessException(common.OPERATION_ERROR, "创建对话失败")
		}
		log.Printf("Battle模式：创建新对话，conversationId=%s, codePreviewEnabled=%v", conversationID, codePreviewEnabled)
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
	if len(req.ImageUrls) > 0 {
		imagesJSON, _ := json.Marshal(req.ImageUrls)
		s := string(imagesJSON)
		userMessage.Images = &s
	}
	if err := s.conversationMessageRepo.Create(userMessage); err != nil {
		log.Printf("保存user消息失败: %v", err)
		return common.NewBusinessException(common.SYSTEM_ERROR, "保存消息失败")
	}
	assistantMessageIndex := messageIndex + 1

	ch := make(chan vo.StreamChunkVO, 64)
	var wg sync.WaitGroup
	var wgConsumer sync.WaitGroup
	wgConsumer.Add(1)
	go func() {
		defer wgConsumer.Done()
		for c := range ch {
			if err := onChunk(c); err != nil {
				log.Printf("Battle stream onChunk error: %v", err)
			}
		}
	}()

	ctx := context.Background()
	anonymousLabels := []string{battleAnonymousLabelA, battleAnonymousLabelB}
	for i := range models {
		realModelId := models[i]
		anonLabel := anonymousLabels[i]
		wg.Add(1)
		go func(realModelIdForDB, anonLabelForStream string) {
			defer wg.Done()
			historyMessages, _ := s.getHistoryMessagesForContext(conversationID, messageIndex, nil)
			langchainMessages := historyToLLMMessages(historyMessages, realModelIdForDB, nil)
			startTime := time.Now()
			fullContent := ""
			fullReasoning := ""
			outputTokens := 0
			var streamErr error
			if codePreviewEnabled {
				streamErr = s.langchainAdapter.CallStreamWithSystemPrompt(ctx, langchainMessages, req.Prompt, req.ImageUrls, constant.CODE_MODE_SYSTEM_PROMPT, realModelIdForDB, req.WebSearchEnabled, func(data llm.StreamData) error {
					fullContent += data.Content
					fullReasoning += data.Reasoning
					outputTokens += (len(data.Content) + len(data.Reasoning)) / 4
					chunk := vo.StreamChunkVO{
						ConversationID: conversationID,
						ModelName:      anonLabelForStream,
						Content:        data.Content,
						FullContent:    fullContent,
						Reasoning:      fullReasoning,
						HasReasoning:   fullReasoning != "",
						OutputTokens:   outputTokens,
						ElapsedMs:      time.Since(startTime).Milliseconds(),
						Done:           false,
						HasError:       false,
					}
					ch <- chunk
					return nil
				})
			} else {
				streamErr = s.langchainAdapter.CallStreamWithHistory(ctx, langchainMessages, req.Prompt, req.ImageUrls, realModelIdForDB, req.WebSearchEnabled, func(data llm.StreamData) error {
					fullContent += data.Content
					fullReasoning += data.Reasoning
					outputTokens += (len(data.Content) + len(data.Reasoning)) / 4
					chunk := vo.StreamChunkVO{
						ConversationID: conversationID,
						ModelName:      anonLabelForStream,
						Content:        data.Content,
						FullContent:    fullContent,
						Reasoning:      fullReasoning,
						HasReasoning:   fullReasoning != "",
						OutputTokens:   outputTokens,
						ElapsedMs:      time.Since(startTime).Milliseconds(),
						Done:           false,
						HasError:       false,
					}
					ch <- chunk
					return nil
				})
			}
			responseTimeMs := int(time.Since(startTime).Milliseconds())
			if streamErr != nil {
				log.Printf("Battle模式 模型 %s 调用失败: %v", anonLabelForStream, streamErr)
				ch <- vo.StreamChunkVO{
					ConversationID: conversationID,
					ModelName:      anonLabelForStream,
					Error:          streamErr.Error(),
					HasError:       true,
					Done:           true,
				}
				return
			}
			thinkingTime := 0
			if fullReasoning != "" {
				thinkingTime = max(1, min(len(fullReasoning)/200, 60))
			}
			inputTokens := len(req.Prompt) / 4
			totalTokens := inputTokens + outputTokens
			cost := s.calculateCostByModel(realModelIdForDB, inputTokens, outputTokens)
			codeBlocks := utils.ExtractCodeBlocks(fullContent)
			hasCodeBlocks := len(codeBlocks) > 0
			toolsUsedStr := buildToolsUsedJSON(req.WebSearchEnabled, req.Prompt, fullContent)
			doneChunk := vo.StreamChunkVO{
				ConversationID: conversationID,
				ModelName:      anonLabelForStream,
				FullContent:    fullContent,
				Reasoning:      fullReasoning,
				HasReasoning:   fullReasoning != "",
				ThinkingTime:   thinkingTime,
				InputTokens:    inputTokens,
				OutputTokens:   outputTokens,
				TotalTokens:    totalTokens,
				Cost:           cost,
				ResponseTimeMs: responseTimeMs,
				CodeBlocks:     codeBlocks,
				HasCodeBlocks:  hasCodeBlocks,
				Done:           true,
				HasError:       false,
			}
			if toolsUsedStr != nil {
				doneChunk.ToolsUsed = *toolsUsedStr
			}
			ch <- doneChunk
			assistantMessage := s.createAssistantMessage(conversationID, userID, assistantMessageIndex, effectiveModelForDB(realModelIdForDB, req.WebSearchEnabled), fullContent, responseTimeMs, inputTokens, outputTokens)
			if fullReasoning != "" {
				assistantMessage.Reasoning = fullReasoning
			}
			if hasCodeBlocks {
				codeBlocksData, _ := json.Marshal(codeBlocks)
				codeBlocksStr := string(codeBlocksData)
				assistantMessage.CodeBlocks = &codeBlocksStr
			}
			assistantMessage.ToolsUsed = toolsUsedStr
			if err := s.conversationMessageRepo.Create(assistantMessage); err != nil {
				log.Printf("保存assistant消息失败: %v", err)
			}
		}(realModelId, anonLabel)
	}
	wg.Wait()
	close(ch)
	wgConsumer.Wait()
	return nil
}

func (s *ConversationService) GetBattleModelMapping(conversationID string, userID int64) (*vo.BattleModelMappingVO, error) {
	conv, err := s.conversationRepo.FindByID(conversationID, userID)
	if err != nil {
		return nil, common.NewBusinessException(common.NOT_FOUND_ERROR, "对话不存在")
	}
	isBattle := conv.IsAnonymous || conv.ConversationType == constant.ConversationTypeBattle
	if !isBattle {
		return nil, common.NewBusinessException(common.PARAMS_ERROR, "该对话不是Battle模式")
	}
	mapping := make(map[string]string)
	if conv.ModelMapping != nil && strings.TrimSpace(*conv.ModelMapping) != "" {
		if err := json.Unmarshal([]byte(*conv.ModelMapping), &mapping); err != nil {
			return nil, common.NewBusinessException(common.SYSTEM_ERROR, "解析模型映射失败")
		}
	} else {
		var modelIDs []string
		if conv.Models != "" {
			if err := json.Unmarshal([]byte(conv.Models), &modelIDs); err == nil && len(modelIDs) >= 2 {
				mapping[battleAnonymousLabelA] = modelIDs[0]
				mapping[battleAnonymousLabelB] = modelIDs[1]
			}
		}
	}
	return &vo.BattleModelMappingVO{Mapping: mapping}, nil
}
