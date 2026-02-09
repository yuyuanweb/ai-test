// Package job 同步OpenRouter模型列表定时任务
// @author <a href="https://codefather.cn">编程导航学习圈</a>
package job

import (
	"ai-test-go/internal/config"
	"ai-test-go/internal/model"
	"ai-test-go/internal/model/dto"
	"ai-test-go/internal/repository"
	"encoding/json"
	"fmt"
	"io"
	"log"
	"net/http"
	"strconv"
	"strings"
	"time"

	"github.com/robfig/cron/v3"
)

const (
	OPENROUTER_MODELS_URL = "https://openrouter.ai/api/v1/models"
	TOKENS_PER_MILLION    = 1000000
)

type ModelPricingCacheEvictor interface {
	EvictModelPricingCache(modelName string)
}

type SyncModelJob struct {
	modelRepo   *repository.ModelRepository
	cacheEvict  ModelPricingCacheEvictor
	cron        *cron.Cron
}

func NewSyncModelJob(modelRepo *repository.ModelRepository, cacheEvict ModelPricingCacheEvictor) *SyncModelJob {
	return &SyncModelJob{
		modelRepo:  modelRepo,
		cacheEvict: cacheEvict,
		cron:       cron.New(),
	}
}

func (j *SyncModelJob) Start() {
	j.cron.AddFunc("0 2 * * *", func() {
		j.SyncModels()
	})
	j.cron.Start()
	log.Println("模型同步定时任务已启动，每天凌晨2点执行")
}

func (j *SyncModelJob) Stop() {
	j.cron.Stop()
}

func (j *SyncModelJob) SyncModels() {
	log.Println("开始同步OpenRouter模型列表")

	openRouterModels, err := j.fetchModelsFromOpenRouter()
	if err != nil {
		log.Printf("获取OpenRouter模型列表失败: %v", err)
		return
	}

	if len(openRouterModels) == 0 {
		log.Println("未获取到任何模型数据")
		return
	}

	log.Printf("从OpenRouter获取到%d个模型", len(openRouterModels))

	successCount := 0
	failCount := 0

	for _, openRouterModel := range openRouterModels {
		m := j.convertToModel(openRouterModel)
		if err := j.modelRepo.SaveOrUpdate(m); err != nil {
			log.Printf("保存模型失败: %s, 错误: %v", openRouterModel.ID, err)
			failCount++
		} else {
			if j.cacheEvict != nil {
				j.cacheEvict.EvictModelPricingCache(openRouterModel.ID)
			}
			successCount++
		}
	}

	log.Printf("模型同步完成: 成功%d个, 失败%d个", successCount, failCount)
}

func (j *SyncModelJob) fetchModelsFromOpenRouter() ([]dto.OpenRouterModel, error) {
	client := &http.Client{Timeout: 30 * time.Second}

	req, err := http.NewRequest("GET", OPENROUTER_MODELS_URL, nil)
	if err != nil {
		return nil, err
	}

	req.Header.Set("Authorization", "Bearer "+config.AppConfig.OpenRouter.APIKey)

	resp, err := client.Do(req)
	if err != nil {
		return nil, err
	}
	defer resp.Body.Close()

	if resp.StatusCode != http.StatusOK {
		body, _ := io.ReadAll(resp.Body)
		return nil, fmt.Errorf("API请求失败: status=%d, body=%s", resp.StatusCode, string(body))
	}

	var response dto.OpenRouterModelResponse
	if err := json.NewDecoder(resp.Body).Decode(&response); err != nil {
		return nil, err
	}

	return response.Data, nil
}

func (j *SyncModelJob) convertToModel(openRouterModel dto.OpenRouterModel) *model.Model {
	provider := j.extractProvider(openRouterModel.ID)
	isChina := 0
	if j.isChinaModel(openRouterModel.ID) {
		isChina = 1
	}

	supportsMultimodal := 0
	if openRouterModel.Architecture.InputModalities != nil {
		for _, m := range openRouterModel.Architecture.InputModalities {
			if m == "image" {
				supportsMultimodal = 1
				break
			}
		}
	}

	supportsImageGen := 0
	if openRouterModel.Architecture.OutputModalities != nil {
		for _, m := range openRouterModel.Architecture.OutputModalities {
			if m == "image" {
				supportsImageGen = 1
				break
			}
		}
	}

	supportsToolCalling := 0
	if openRouterModel.SupportedParameters != nil {
		for _, param := range openRouterModel.SupportedParameters {
			if param == "tools" || param == "tool_choice" || param == "functions" || param == "function_call" {
				supportsToolCalling = 1
				break
			}
		}
	}

	inputPrice := j.convertPrice(openRouterModel.Pricing.Prompt)
	outputPrice := j.convertPrice(openRouterModel.Pricing.Completion)

	tags := j.generateTags(openRouterModel)
	tagsJSON, _ := json.Marshal(tags)

	rawData, _ := json.Marshal(openRouterModel)

	now := time.Now()
	return &model.Model{
		ID:                  openRouterModel.ID,
		Name:                openRouterModel.Name,
		Description:         openRouterModel.Description,
		Provider:            provider,
		ContextLength:       openRouterModel.ContextLength,
		InputPrice:          inputPrice,
		OutputPrice:         outputPrice,
		Recommended:         0,
		IsChina:             isChina,
		SupportsMultimodal:  supportsMultimodal,
		SupportsImageGen:    supportsImageGen,
		SupportsToolCalling: supportsToolCalling,
		Tags:                string(tagsJSON),
		RawData:             string(rawData),
		TotalTokens:         0,
		TotalCost:           0,
		CreateTime:          now,
		UpdateTime:          now,
		IsDelete:            0,
	}
}

func (j *SyncModelJob) isChinaModel(modelID string) bool {
	if !strings.Contains(modelID, "/") {
		return false
	}
	prefix := strings.ToLower(strings.Split(modelID, "/")[0])
	chinaProviders := []string{"qwen", "alibaba", "deepseek", "z-ai", "zhipu", "moonshotai", "baidu", "tencent", "bytedance", "bytedance-seed", "meituan"}
	for _, p := range chinaProviders {
		if prefix == p {
			return true
		}
	}
	return false
}

func (j *SyncModelJob) extractProvider(modelID string) string {
	if !strings.Contains(modelID, "/") {
		return "Unknown"
	}
	prefix := strings.ToLower(strings.Split(modelID, "/")[0])

	providerMap := map[string]string{
		"qwen":           "Alibaba",
		"alibaba":        "Alibaba",
		"deepseek":       "DeepSeek",
		"z-ai":           "Zhipu AI",
		"zhipu":          "Zhipu AI",
		"moonshotai":     "Moonshotai",
		"baidu":          "Baidu",
		"tencent":        "Tencent",
		"bytedance":      "Bytedance",
		"bytedance-seed": "Bytedance",
		"meituan":        "Meituan",
		"openai":         "OpenAI",
		"anthropic":      "Anthropic",
		"google":         "Google",
		"meta-llama":     "Meta",
		"meta":           "Meta",
		"mistralai":      "Mistral AI",
	}

	if provider, ok := providerMap[prefix]; ok {
		return provider
	}

	return capitalize(prefix)
}

func capitalize(str string) string {
	if len(str) == 0 {
		return str
	}
	return strings.ToUpper(str[:1]) + str[1:]
}

func (j *SyncModelJob) convertPrice(priceStr string) float64 {
	if priceStr == "" {
		return 0
	}
	price, err := strconv.ParseFloat(priceStr, 64)
	if err != nil {
		log.Printf("价格转换失败: %s", priceStr)
		return 0
	}
	return price * TOKENS_PER_MILLION
}

func (j *SyncModelJob) generateTags(m dto.OpenRouterModel) []string {
	tags := []string{}

	if m.Architecture.InputModalities != nil {
		for _, mod := range m.Architecture.InputModalities {
			if mod == "image" {
				tags = append(tags, "多模态")
				break
			}
		}
	}

	if m.Architecture.OutputModalities != nil {
		for _, mod := range m.Architecture.OutputModalities {
			if mod == "image" {
				tags = append(tags, "图像生成")
				break
			}
		}
	}

	nameLower := strings.ToLower(m.Name)
	idLower := strings.ToLower(m.ID)

	if strings.Contains(nameLower, "code") || strings.Contains(idLower, "code") {
		tags = append(tags, "代码")
	}
	if strings.Contains(nameLower, "mini") || strings.Contains(nameLower, "flash") || strings.Contains(nameLower, "haiku") {
		tags = append(tags, "快速")
	}
	if strings.Contains(nameLower, "vision") || strings.Contains(nameLower, "multimodal") {
		tags = append(tags, "多模态")
	}
	if strings.Contains(idLower, "qwen") || strings.Contains(idLower, "glm") || strings.Contains(idLower, "ernie") {
		tags = append(tags, "中文")
	}

	hasTextGen := false
	for _, tag := range tags {
		if tag == "文本生成" {
			hasTextGen = true
			break
		}
	}
	if !hasTextGen {
		tags = append(tags, "文本生成")
	}

	return tags
}
