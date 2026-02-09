// Package service 图片生成服务
// @author <a href="https://codefather.cn">编程导航学习圈</a>
package service

import (
	"ai-test-go/internal/config"
	"ai-test-go/internal/model"
	"ai-test-go/internal/model/dto"
	"ai-test-go/internal/model/vo"
	"ai-test-go/internal/repository"
	"ai-test-go/pkg/common"
	"ai-test-go/pkg/utils"
	"bytes"
	"context"
	"encoding/base64"
	"encoding/json"
	"fmt"
	"io"
	"log"
	"math/rand"
	"net/http"
	"strings"
	"time"
)

const (
	imageGenMaxSizeBytes = 10 * 1024 * 1024
	imageGenTimeout      = 120 * time.Second
)

// OpenRouter 图片生成请求/响应结构（与 Java 对齐）
type openRouterImageReq struct {
	Model      string                   `json:"model"`
	Modalities []string                 `json:"modalities"`
	Stream     bool                     `json:"stream"`
	Messages   []openRouterImageMessage  `json:"messages"`
	N          int                      `json:"n"`
}

type openRouterImageMessage struct {
	Role    string      `json:"role"`
	Content interface{} `json:"content"`
}

type openRouterImageResp struct {
	Choices []openRouterImageChoice `json:"choices"`
	Usage   *openRouterImageUsage   `json:"usage"`
}

type openRouterImageChoice struct {
	Message openRouterImageMsg `json:"message"`
}

type openRouterImageMsg struct {
	Role    string                   `json:"role"`
	Content string                   `json:"content"`
	Images  []openRouterImagePart    `json:"images"`
}

type openRouterImagePart struct {
	Type     string              `json:"type"`
	ImageURL *openRouterImageURL `json:"image_url"`
}

type openRouterImageURL struct {
	URL string `json:"url"`
}

type openRouterImageUsage struct {
	PromptTokens     int     `json:"prompt_tokens"`
	CompletionTokens int     `json:"completion_tokens"`
	TotalTokens      int     `json:"total_tokens"`
	Cost             float64 `json:"cost,omitempty"`
}

// ImageService 图片生成服务
type ImageService struct {
	modelRepo               *repository.ModelRepository
	conversationRepo        *repository.ConversationRepository
	conversationMessageRepo *repository.ConversationMessageRepository
	fileService             *FileService
	modelService            *ModelService
	userModelUsageService   *UserModelUsageService
	apiKey                  string
	baseURL                 string
}

// NewImageService 创建图片生成服务
func NewImageService(
	modelRepo *repository.ModelRepository,
	conversationRepo *repository.ConversationRepository,
	conversationMessageRepo *repository.ConversationMessageRepository,
	fileService *FileService,
	modelService *ModelService,
	userModelUsageService *UserModelUsageService,
) *ImageService {
	baseURL := strings.TrimSuffix(config.AppConfig.OpenRouter.BaseURL, "/")
	return &ImageService{
		modelRepo:               modelRepo,
		conversationRepo:        conversationRepo,
		conversationMessageRepo: conversationMessageRepo,
		fileService:             fileService,
		modelService:            modelService,
		userModelUsageService:   userModelUsageService,
		apiKey:                  config.AppConfig.OpenRouter.APIKey,
		baseURL:                 baseURL,
	}
}

// GenerateImages 调用 OpenRouter 生成图片，返回结果列表
func (s *ImageService) GenerateImages(ctx context.Context, req *dto.GenerateImageRequest, userID int64) ([]vo.GeneratedImageVO, string, error) {
	if req == nil {
		return nil, "", common.NewBusinessException(common.PARAMS_ERROR, "请求参数不能为空")
	}
	count := 1
	if req.Count != nil && *req.Count > 0 {
		count = *req.Count
		if count > 4 {
			count = 4
		}
	}
	if userID <= 0 {
		return nil, "", common.NewBusinessException(common.NOT_LOGIN_ERROR, common.GetErrorMessage(common.NOT_LOGIN_ERROR))
	}
	if strings.TrimSpace(req.Prompt) == "" {
		return nil, "", common.NewBusinessException(common.PARAMS_ERROR, "提示词不能为空")
	}

	modelID := req.Model
	if req.IsAnonymous != nil && *req.IsAnonymous {
		m := s.selectRandomImageGenModel()
		if m == nil {
			return nil, "", common.NewBusinessException(common.NOT_FOUND_ERROR, "没有可用的图片生成模型")
		}
		modelID = m.ID
	}
	if modelID == "" {
		return nil, "", common.NewBusinessException(common.PARAMS_ERROR, "模型名称不能为空")
	}

	m, err := s.modelRepo.FindByID(modelID)
	if err != nil || m == nil {
		return nil, "", common.NewBusinessException(common.NOT_FOUND_ERROR, "模型不存在或已下线")
	}
	if m.IsDelete == 1 {
		return nil, "", common.NewBusinessException(common.NOT_FOUND_ERROR, "模型不存在或已下线")
	}
	if m.SupportsMultimodal != 1 && m.SupportsImageGen != 1 {
		return nil, "", common.NewBusinessException(common.PARAMS_ERROR, "当前模型不支持图片多模态，请更换模型")
	}

	body := s.buildRequestBody(req, modelID, count)
	reqBody, _ := json.Marshal(body)
	url := s.baseURL + "/chat/completions"
	httpReq, err := http.NewRequestWithContext(ctx, "POST", url, bytes.NewReader(reqBody))
	if err != nil {
		return nil, "", common.NewBusinessException(common.SYSTEM_ERROR, "创建请求失败")
	}
	httpReq.Header.Set("Content-Type", "application/json")
	httpReq.Header.Set("Authorization", "Bearer "+s.apiKey)

	client := &http.Client{Timeout: imageGenTimeout}
	resp, err := client.Do(httpReq)
	if err != nil {
		return nil, "", common.NewBusinessException(common.SYSTEM_ERROR, "调用图片生成服务失败："+err.Error())
	}
	defer resp.Body.Close()

	respBody, _ := io.ReadAll(resp.Body)
	if resp.StatusCode != http.StatusOK {
		return nil, "", common.NewBusinessException(common.SYSTEM_ERROR, fmt.Sprintf("图片生成接口错误: status=%d, body=%s", resp.StatusCode, string(respBody)))
	}

	var orResp openRouterImageResp
	if err := json.Unmarshal(respBody, &orResp); err != nil {
		return nil, "", common.NewBusinessException(common.SYSTEM_ERROR, "解析图片生成响应失败")
	}
	if len(orResp.Choices) == 0 {
		return nil, "", common.NewBusinessException(common.SYSTEM_ERROR, "图片生成失败：未返回结果")
	}

	var fullThinking strings.Builder
	promptTokens := 0
	completionTokens := 0
	totalTokens := 0
	cost := 0.0
	if orResp.Usage != nil {
		promptTokens = orResp.Usage.PromptTokens
		completionTokens = orResp.Usage.CompletionTokens
		totalTokens = orResp.Usage.TotalTokens
		if totalTokens == 0 {
			totalTokens = promptTokens + completionTokens
		}
		cost = orResp.Usage.Cost
	}

	var result []vo.GeneratedImageVO
	idx := 0
	for _, choice := range orResp.Choices {
		if choice.Message.Content != "" {
			fullThinking.WriteString(choice.Message.Content)
		}
		for _, img := range choice.Message.Images {
			if len(result) >= count {
				break
			}
			if img.ImageURL == nil || img.ImageURL.URL == "" {
				continue
			}
			imageURL := s.handleImageData(ctx, img.ImageURL.URL, userID)
			voItem := vo.GeneratedImageVO{
				Url:         imageURL,
				ModelName:   modelID,
				Index:       idx,
				InputTokens: &promptTokens,
				OutputTokens: &completionTokens,
				TotalTokens: &totalTokens,
				Cost:       &cost,
			}
			result = append(result, voItem)
			idx++
		}
	}

	if len(result) == 0 {
		return nil, "", common.NewBusinessException(common.SYSTEM_ERROR, "图片生成失败：未解析到图片结果")
	}

	reasoningStr := fullThinking.String()
	req.Reasoning = reasoningStr

	if totalTokens > 0 {
		_ = s.modelService.UpdateModelUsage(modelID, int64(totalTokens), cost)
		_ = s.userModelUsageService.UpdateUserModelUsage(userID, modelID, int64(totalTokens), cost)
	}

	conversationId := strings.TrimSpace(req.ConversationId)
	if conversationId != "" {
		conv, err := s.conversationRepo.FindByID(conversationId, userID)
		if err != nil || conv == nil {
			log.Printf("图片生成：会话不存在或不属于当前用户，跳过保存 conversationId=%s userId=%d", conversationId, userID)
		} else {
			savedIdx, err := s.saveToConversation(conversationId, userID, req, result, promptTokens, completionTokens, totalTokens, cost)
			if err != nil {
				log.Printf("保存图片生成结果到会话失败: %v", err)
			} else {
				for i := range result {
					result[i].ConversationId = conversationId
					result[i].MessageIndex = &savedIdx
				}
			}
		}
	} else if len(req.Models) > 0 {
		newConvID, err := s.createConversationForImageGen(userID, req)
		if err != nil {
			log.Printf("创建图片生成会话失败: %v", err)
		} else {
			savedIdx, err := s.saveToConversation(newConvID, userID, req, result, promptTokens, completionTokens, totalTokens, cost)
			if err != nil {
				log.Printf("保存图片生成结果到会话失败: %v", err)
			} else {
				for i := range result {
					result[i].ConversationId = newConvID
					result[i].MessageIndex = &savedIdx
				}
			}
		}
	}

	return result, reasoningStr, nil
}

// GenerateImagesStream 流式生成图片：先调用生成，再按 SSE 顺序推送 thinking -> image -> done
func (s *ImageService) GenerateImagesStream(ctx context.Context, req *dto.GenerateImageRequest, userID int64, onChunk func(vo.ImageStreamChunkVO)) error {
	if req == nil {
		onChunk(vo.ImageStreamChunkVO{Type: "error", Error: "请求参数不能为空"})
		return nil
	}
	if req.Count != nil && *req.Count <= 0 {
		onChunk(vo.ImageStreamChunkVO{Type: "error", Error: "生成数量必须大于 0"})
		return nil
	}
	if userID <= 0 {
		onChunk(vo.ImageStreamChunkVO{Type: "error", Error: "未登录"})
		return nil
	}

	images, reasoning, err := s.GenerateImages(ctx, req, userID)
	if err != nil {
		errMsg := err.Error()
		if ex, ok := err.(*common.BusinessException); ok {
			errMsg = ex.Message
		}
		convID, msgIdx := s.saveFailedMessage(req, userID, errMsg)
		onChunk(vo.ImageStreamChunkVO{Type: "error", Error: errMsg, ConversationId: convID, MessageIndex: msgIdx})
		return nil
	}

	if reasoning != "" {
		onChunk(vo.ImageStreamChunkVO{
			Type:         "thinking",
			Thinking:     reasoning,
			FullThinking: reasoning,
			ModelName:    req.Model,
			VariantIndex: req.VariantIndex,
		})
	}
	var conversationId string
	var messageIndex *int
	for i := range images {
		onChunk(vo.ImageStreamChunkVO{
			Type:           "image",
			Image:          &images[i],
			ConversationId: images[i].ConversationId,
			MessageIndex:   images[i].MessageIndex,
			VariantIndex:  req.VariantIndex,
			ModelName:     req.Model,
		})
		if images[i].ConversationId != "" {
			conversationId = images[i].ConversationId
		}
		if images[i].MessageIndex != nil {
			messageIndex = images[i].MessageIndex
		}
	}
	onChunk(vo.ImageStreamChunkVO{
		Type:           "done",
		ConversationId: conversationId,
		MessageIndex:   messageIndex,
		VariantIndex:   req.VariantIndex,
		ModelName:      req.Model,
		FullThinking:   reasoning,
	})
	return nil
}

func (s *ImageService) buildRequestBody(req *dto.GenerateImageRequest, modelID string, count int) openRouterImageReq {
	var content interface{} = req.Prompt
	if len(req.ReferenceImageUrls) > 0 {
		items := []map[string]interface{}{
			{"type": "text", "text": req.Prompt},
		}
		for _, u := range req.ReferenceImageUrls {
			if u == "" {
				continue
			}
			items = append(items, map[string]interface{}{
				"type": "image_url",
				"image_url": map[string]string{"url": u},
			})
		}
		content = items
	}
	return openRouterImageReq{
		Model:      modelID,
		Modalities: []string{"text", "image"},
		Stream:     false,
		Messages:   []openRouterImageMessage{{Role: "user", Content: content}},
		N:          count,
	}
}

func (s *ImageService) selectRandomImageGenModel() *model.Model {
	list, _, err := s.modelRepo.List(&dto.ModelQueryRequest{OnlySupportsImageGen: ptrTrue()})
	if err != nil || len(list) == 0 {
		return nil
	}
	return &list[rand.Intn(len(list))]
}

func ptrTrue() *bool { b := true; return &b }

func (s *ImageService) createConversationForImageGen(userID int64, req *dto.GenerateImageRequest) (string, error) {
	conversationID := utils.GenerateUUID()
	models := req.Models
	if len(models) == 0 {
		models = []string{req.Model}
	}
	modelsJSON, _ := json.Marshal(models)
	title := req.Prompt
	if len(title) > 50 {
		title = title[:50] + "..."
	}
	convType := "side_by_side"
	if req.ConversationType == "prompt_lab" || req.ConversationType == "battle" {
		convType = req.ConversationType
	}
	now := time.Now()
	conv := &model.Conversation{
		ID:                 conversationID,
		UserID:             userID,
		Title:              title,
		ConversationType:   convType,
		CodePreviewEnabled: false,
		IsAnonymous:        false,
		Models:             string(modelsJSON),
		TotalTokens:        0,
		TotalCost:          0,
		CreateTime:         now,
		UpdateTime:         now,
		IsDelete:           0,
	}
	if err := s.conversationRepo.Create(conv); err != nil {
		return "", err
	}
	return conversationID, nil
}

func (s *ImageService) saveToConversation(conversationID string, userID int64, req *dto.GenerateImageRequest, resultList []vo.GeneratedImageVO, inputTokens, outputTokens, totalTokens int, cost float64) (int, error) {
	userMessageIndex := 0
	if req.MessageIndex != nil {
		userMessageIndex = *req.MessageIndex
	} else {
		idx, err := s.conversationMessageRepo.GetNextMessageIndex(conversationID)
		if err != nil {
			return 0, err
		}
		userMessageIndex = idx
	}
	assistantMessageIndex := userMessageIndex
	variantIndex := req.VariantIndex

	var userImagesJSON *string
	if len(req.ReferenceImageUrls) > 0 {
		b, _ := json.Marshal(req.ReferenceImageUrls)
		s := string(b)
		userImagesJSON = &s
	}
	userMsg := &model.ConversationMessage{
		ID:             utils.GenerateUUID(),
		ConversationID: conversationID,
		UserID:         userID,
		MessageIndex:   userMessageIndex,
		VariantIndex:   variantIndex,
		Role:           "user",
		Content:        req.Prompt,
		Images:         userImagesJSON,
		CreateTime:     time.Now(),
		UpdateTime:     time.Now(),
		IsDelete:       0,
	}
	if err := s.conversationMessageRepo.Create(userMsg); err != nil {
		return 0, err
	}

	var generatedURLs []string
	for _, r := range resultList {
		if r.Url != "" {
			generatedURLs = append(generatedURLs, r.Url)
		}
	}
	var assistantImagesJSON *string
	if len(generatedURLs) > 0 {
		b, _ := json.Marshal(generatedURLs)
		s := string(b)
		assistantImagesJSON = &s
	}
	content := fmt.Sprintf("已生成 %d 张图片", len(generatedURLs))
	assistantMsg := &model.ConversationMessage{
		ID:             utils.GenerateUUID(),
		ConversationID: conversationID,
		UserID:         userID,
		MessageIndex:   assistantMessageIndex,
		VariantIndex:   variantIndex,
		Role:           "assistant",
		ModelName:      req.Model,
		Content:        content,
		Images:         assistantImagesJSON,
		InputTokens:    inputTokens,
		OutputTokens:   outputTokens,
		Cost:           cost,
		Reasoning:      req.Reasoning,
		CreateTime:     time.Now(),
		UpdateTime:     time.Now(),
		IsDelete:       0,
	}
	if err := s.conversationMessageRepo.Create(assistantMsg); err != nil {
		return 0, err
	}

	if err := s.conversationRepo.IncrementStats(conversationID, totalTokens, cost); err != nil {
		log.Printf("更新会话统计失败: %v", err)
	}
	return userMessageIndex, nil
}

func (s *ImageService) saveFailedMessage(req *dto.GenerateImageRequest, userID int64, errorMessage string) (conversationID string, messageIndex *int) {
	convID := strings.TrimSpace(req.ConversationId)
	if convID == "" {
		newID, err := s.createConversationForImageGen(userID, req)
		if err != nil {
			return "", nil
		}
		convID = newID
	} else {
		conv, err := s.conversationRepo.FindByID(convID, userID)
		if err != nil || conv == nil {
			return "", nil
		}
	}
	msgIdx := 0
	if req.MessageIndex != nil {
		msgIdx = *req.MessageIndex
	} else {
		idx, err := s.conversationMessageRepo.GetNextMessageIndex(convID)
		if err != nil {
			return convID, nil
		}
		msgIdx = idx
	}
	variantIndex := req.VariantIndex
	var userImagesJSON *string
	if len(req.ReferenceImageUrls) > 0 {
		b, _ := json.Marshal(req.ReferenceImageUrls)
		s := string(b)
		userImagesJSON = &s
	}
	userMsg := &model.ConversationMessage{
		ID:             utils.GenerateUUID(),
		ConversationID: convID,
		UserID:         userID,
		MessageIndex:   msgIdx,
		VariantIndex:   variantIndex,
		Role:           "user",
		Content:        req.Prompt,
		Images:         userImagesJSON,
		CreateTime:     time.Now(),
		UpdateTime:     time.Now(),
		IsDelete:       0,
	}
	if err := s.conversationMessageRepo.Create(userMsg); err != nil {
		return convID, nil
	}
	content := "图片生成失败: " + errorMessage
	if errorMessage == "" {
		content = "图片生成失败: 未知错误"
	}
	assistantMsg := &model.ConversationMessage{
		ID:             utils.GenerateUUID(),
		ConversationID: convID,
		UserID:         userID,
		MessageIndex:   msgIdx,
		VariantIndex:   variantIndex,
		Role:           "assistant",
		ModelName:      req.Model,
		Content:        content,
		Reasoning:      req.Reasoning,
		CreateTime:     time.Now(),
		UpdateTime:     time.Now(),
		IsDelete:       0,
	}
	if err := s.conversationMessageRepo.Create(assistantMsg); err != nil {
		return convID, &msgIdx
	}
	return convID, &msgIdx
}

// handleImageData 若为 data:image/...;base64,... 则解码上传 COS 后返回 URL，否则返回原 URL
func (s *ImageService) handleImageData(ctx context.Context, data string, userID int64) string {
	if !strings.HasPrefix(data, "data:image") {
		return data
	}
	comma := strings.Index(data, ",")
	if comma <= 0 {
		log.Printf("图片数据格式错误，无 base64 内容")
		return data
	}
	header := data[5:comma]
	mimeType := "image/png"
	if idx := strings.Index(header, ";"); idx > 0 {
		mimeType = strings.TrimSpace(header[:idx])
	}
	ext := "png"
	switch {
	case strings.Contains(mimeType, "jpeg"), strings.Contains(mimeType, "jpg"):
		ext = "jpg"
	case strings.Contains(mimeType, "gif"):
		ext = "gif"
	case strings.Contains(mimeType, "webp"):
		ext = "webp"
	}
	raw, err := base64.StdEncoding.DecodeString(strings.TrimSpace(data[comma+1:]))
	if err != nil {
		log.Printf("base64 解码失败: %v", err)
		return data
	}
	if len(raw) > imageGenMaxSizeBytes {
		log.Printf("生成图片过大: %d bytes", len(raw))
		return data
	}
	fileName := fmt.Sprintf("generated/%s.%s", time.Now().Format("20060102150405")+fmt.Sprintf("%d", time.Now().Nanosecond()%1000), ext)
	url, err := s.fileService.UploadImage(ctx, bytes.NewReader(raw), fileName, userID)
	if err != nil {
		log.Printf("上传生成图片到 COS 失败: %v", err)
		return data
	}
	return url
}
