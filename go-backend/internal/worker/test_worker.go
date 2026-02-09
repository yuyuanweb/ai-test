// Package worker 测试任务异步执行器
// @author <a href="https://codefather.cn">编程导航学习圈</a>
package worker

import (
	"ai-test-go/internal/config"
	"ai-test-go/internal/constant"
	"ai-test-go/internal/guardrail"
	"ai-test-go/internal/model"
	"ai-test-go/pkg/common"
	"ai-test-go/internal/model/dto"
	"ai-test-go/internal/model/vo"
	"ai-test-go/internal/repository"
	"ai-test-go/internal/service"
	"ai-test-go/pkg/logger"
	"ai-test-go/pkg/openrouter"
	"ai-test-go/pkg/rabbitmq"
	"ai-test-go/pkg/utils"
	"context"
	"encoding/json"
	"fmt"
	"time"

	amqp "github.com/rabbitmq/amqp091-go"
)

type TestWorker struct {
	testResultRepo        *repository.TestResultRepository
	testTaskRepo          *repository.TestTaskRepository
	batchTestService      *service.BatchTestService
	modelService          *service.ModelService
	userModelUsageService *service.UserModelUsageService
	budgetService         *service.BudgetService
	progressService       *service.ProgressNotificationService
	openRouterClient      *openrouter.Client
	aiScoringService      service.AIScoringService
	rabbitMQClient        *rabbitmq.RabbitMQClient
}

func NewTestWorker(
	testResultRepo *repository.TestResultRepository,
	testTaskRepo *repository.TestTaskRepository,
	batchTestService *service.BatchTestService,
	modelService *service.ModelService,
	userModelUsageService *service.UserModelUsageService,
	budgetService *service.BudgetService,
	progressService *service.ProgressNotificationService,
	openRouterClient *openrouter.Client,
	aiScoringService service.AIScoringService,
	rabbitMQClient *rabbitmq.RabbitMQClient,
) *TestWorker {
	return &TestWorker{
		testResultRepo:        testResultRepo,
		testTaskRepo:          testTaskRepo,
		batchTestService:      batchTestService,
		modelService:          modelService,
		userModelUsageService: userModelUsageService,
		budgetService:         budgetService,
		progressService:       progressService,
		openRouterClient:      openRouterClient,
		aiScoringService:      aiScoringService,
		rabbitMQClient:        rabbitMQClient,
	}
}

func (w *TestWorker) Start() error {
	if config.RabbitMQChannel == nil {
		logger.Log.Warn("RabbitMQ未初始化，TestWorker跳过启动")
		return nil
	}

	msgs, err := config.RabbitMQChannel.Consume(
		constant.TestQueue,
		"",
		false,
		false,
		false,
		false,
		nil,
	)
	if err != nil {
		return fmt.Errorf("消费队列失败: %w", err)
	}

	logger.Log.Info("TestWorker启动成功，开始监听队列")

	go func() {
		for d := range msgs {
			w.processSubTask(d)
		}
	}()

	return nil
}

func (w *TestWorker) processSubTask(d amqp.Delivery) {
	var subTask dto.SubTaskMessage
	if err := json.Unmarshal(d.Body, &subTask); err != nil {
		logger.Log.Errorf("解析子任务消息失败: %v", err)
		d.Ack(false)
		return
	}

	logger.Log.Infof("开始处理子任务: taskId=%s, model=%s, prompt=%s",
		subTask.TaskID, subTask.ModelName, subTask.PromptTitle)

	startTime := time.Now()
	resultID := utils.GenerateUUID()

	defer func() {
		if r := recover(); r != nil {
			logger.Log.Errorf("子任务执行崩溃: taskId=%s, model=%s, error=%v",
				subTask.TaskID, subTask.ModelName, r)
			w.batchTestService.MarkTaskFailed(subTask.TaskID)
			d.Nack(false, false)
		}
	}()

	task, err := w.testTaskRepo.FindByID(subTask.TaskID)
	if err != nil {
		logger.Log.Errorf("任务不存在: taskId=%s", subTask.TaskID)
		d.Ack(false)
		return
	}

	if task.Status == "cancelled" || task.Status == "failed" {
		logger.Log.Warnf("任务已取消或失败，跳过子任务: taskId=%s, status=%s", subTask.TaskID, task.Status)
		d.Ack(false)
		return
	}

	if err := guardrail.Validate(subTask.PromptContent); err != nil {
		rejectMsg := "输入包含不当内容，请求被拒绝"
		if bizErr, ok := err.(*common.BusinessException); ok {
			rejectMsg = bizErr.Message
			logger.Log.Warnf("Prompt护轨拒绝: taskId=%s, model=%s, reason=%s", subTask.TaskID, subTask.ModelName, bizErr.Message)
		}
		guardrailResult := &model.TestResult{
			ID:             resultID,
			TaskID:         subTask.TaskID,
			UserID:         subTask.UserID,
			SceneID:        subTask.SceneID,
			PromptID:       subTask.PromptID,
			ModelName:      subTask.ModelName,
			InputPrompt:    subTask.PromptContent,
			OutputText:     "[护轨拒绝] " + rejectMsg,
			ResponseTimeMs: 0,
			InputTokens:    0,
			OutputTokens:   0,
			Cost:           0,
			AIScore:        "{}",
			IsDelete:       0,
		}
		if createErr := w.testResultRepo.Create(guardrailResult); createErr == nil {
			w.batchTestService.UpdateTaskProgress(subTask.TaskID)
		}
		d.Ack(false)
		return
	}

	var configMap map[string]interface{}
	if subTask.Config != "" {
		json.Unmarshal([]byte(subTask.Config), &configMap)
	}

	req := &openrouter.ChatRequest{
		Model: subTask.ModelName,
		Messages: []openrouter.Message{
			{
				Role:    "user",
				Content: subTask.PromptContent,
			},
		},
	}

	if temp, ok := configMap["temperature"].(float64); ok {
		req.Temperature = temp
	}
	if maxTokens, ok := configMap["maxTokens"].(float64); ok {
		req.MaxTokens = int(maxTokens)
	}

	ctx, cancel := context.WithTimeout(context.Background(), constant.SubTaskTimeoutSeconds*time.Second)
	defer cancel()

	resp, err := w.openRouterClient.ChatWithContext(ctx, req)
	if err != nil {
		logger.Log.Errorf("调用OpenRouter失败: taskId=%s, model=%s, retryCount=%d, error=%v",
			subTask.TaskID, subTask.ModelName, subTask.RetryCount, err)
		if subTask.RetryCount < constant.MaxSubTaskRetryCount && w.rabbitMQClient != nil {
			subTask.RetryCount++
			priority := rabbitmq.CalculatePriority(task.TotalSubtasks)
			if repubErr := w.rabbitMQClient.PublishMessage(context.Background(), subTask, priority); repubErr != nil {
				logger.Log.Errorf("重试入队失败: taskId=%s, error=%v", subTask.TaskID, repubErr)
				w.batchTestService.MarkTaskFailed(subTask.TaskID)
			}
		} else {
			w.batchTestService.MarkTaskFailed(subTask.TaskID)
		}
		d.Ack(false)
		return
	}

	responseTimeMs := int(time.Since(startTime).Milliseconds())
	outputText := ""
	if len(resp.Choices) > 0 {
		outputText = resp.Choices[0].Message.Content
	}

	inputTokens := resp.Usage.PromptTokens
	outputTokens := resp.Usage.CompletionTokens

	cost := w.calculateCost(subTask.ModelName, inputTokens, outputTokens)

	testResult := &model.TestResult{
		ID:             resultID,
		TaskID:         subTask.TaskID,
		UserID:         subTask.UserID,
		SceneID:        subTask.SceneID,
		PromptID:       subTask.PromptID,
		ModelName:      subTask.ModelName,
		InputPrompt:    subTask.PromptContent,
		OutputText:     outputText,
		ResponseTimeMs: responseTimeMs,
		InputTokens:    inputTokens,
		OutputTokens:   outputTokens,
		Cost:           cost,
		AIScore:        "{}",
		IsDelete:       0,
	}

	if err := w.testResultRepo.Create(testResult); err != nil {
		logger.Log.Errorf("保存测试结果失败: taskId=%s, error=%v", subTask.TaskID, err)
		d.Nack(false, true)
		return
	}

	totalTokens := int64(inputTokens + outputTokens)
	w.modelService.UpdateModelUsage(subTask.ModelName, totalTokens, cost)

	if subTask.UserID > 0 {
		w.userModelUsageService.UpdateUserModelUsage(subTask.UserID, subTask.ModelName, totalTokens, cost)
		if cost > 0 && w.budgetService != nil {
			w.budgetService.AddCost(subTask.UserID, cost)
		}
	}

	enableAiScoring := isEnableAiScoring(configMap)
	if enableAiScoring && w.aiScoringService != nil {
		logger.Log.Infof("执行AI评分: taskId=%s, model=%s, prompt=%s", subTask.TaskID, subTask.ModelName, subTask.PromptTitle)
		aiScoreResult, err := w.aiScoringService.ScoreWithMultipleJudges(
			subTask.PromptContent,
			outputText,
			subTask.ModelName,
			subTask.UserID,
		)
		if err != nil {
			logger.Log.Errorf("AI评分失败: taskId=%s, model=%s, prompt=%s, error=%v",
				subTask.TaskID, subTask.ModelName, subTask.PromptTitle, err)
		} else {
			aiScoreJSON, _ := json.Marshal(aiScoreResult)
			if errUpdate := w.testResultRepo.UpdateByID(resultID, map[string]interface{}{"aiScore": string(aiScoreJSON)}); errUpdate != nil {
				logger.Log.Warnf("更新测试结果AI评分失败: resultId=%s, error=%v", resultID, errUpdate)
			} else {
				logger.Log.Infof("AI评分已写入: taskId=%s, model=%s, judges=%d, averageRating=%.2f",
					subTask.TaskID, subTask.ModelName, len(aiScoreResult.Judges), aiScoreResult.AverageRating)
			}
		}
	} else if enableAiScoring && w.aiScoringService == nil {
		logger.Log.Warn("已开启AI评分但 aiScoringService 未注入，跳过评分")
	}

	w.batchTestService.UpdateTaskProgress(subTask.TaskID)

	updatedTask, err := w.testTaskRepo.FindByID(subTask.TaskID)
	if err == nil {
		percentage := 0
		if updatedTask.TotalSubtasks > 0 {
			percentage = (updatedTask.CompletedSubtasks * 100) / updatedTask.TotalSubtasks
		}

		shouldPush := updatedTask.Status == "completed" ||
			updatedTask.CompletedSubtasks == 1 ||
			(updatedTask.CompletedSubtasks%constant.ProgressPushEveryN == 0)
		if shouldPush {
			w.progressService.SendProgress(subTask.TaskID, &vo.TaskProgressVO{
				TaskID:            subTask.TaskID,
				Percentage:        percentage,
				CompletedSubtasks: updatedTask.CompletedSubtasks,
				TotalSubtasks:     updatedTask.TotalSubtasks,
				CurrentModel:      subTask.ModelName,
				CurrentPrompt:     subTask.PromptTitle,
				Status:            updatedTask.Status,
				Timestamp:         time.Now().UnixMilli(),
			})
		}
	}

	logger.Log.Infof("子任务完成: taskId=%s, model=%s, prompt=%s, responseTime=%dms, tokens=%d/%d, cost=$%.6f",
		subTask.TaskID, subTask.ModelName, subTask.PromptTitle,
		responseTimeMs, inputTokens, outputTokens, cost)

	d.Ack(false)
}

func isEnableAiScoring(configMap map[string]interface{}) bool {
	if configMap == nil {
		return false
	}
	v, ok := configMap["enableAiScoring"]
	if !ok {
		return false
	}
	switch val := v.(type) {
	case bool:
		return val
	case float64:
		return val != 0
	case int:
		return val != 0
	default:
		return false
	}
}

func (w *TestWorker) calculateCost(modelName string, inputTokens, outputTokens int) float64 {
	pricing := w.modelService.GetModelPricing(modelName)
	if pricing == nil {
		logger.Log.Warnf("模型不存在，使用默认价格: modelName=%s", modelName)
		return (float64(inputTokens)*0.5 + float64(outputTokens)*1.5) / 1000000.0
	}

	inputCost := float64(inputTokens) * pricing.InputPrice / 1000000.0
	outputCost := float64(outputTokens) * pricing.OutputPrice / 1000000.0

	return inputCost + outputCost
}
