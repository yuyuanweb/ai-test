// Package main 主程序入口
// @author <a href="https://codefather.cn">编程导航学习圈</a>
//
// @title           AI大模型评测平台 API
// @version         1.0
// @description     AI大模型评测平台 Go版后端接口文档
// @contact.name    编程导航学习圈
// @contact.url     https://codefather.cn
// @host            localhost:8080
// @BasePath        /api
package main

import (
	"fmt"
	"log"

	_ "ai-test-go/docs"
	"ai-test-go/internal/config"
	"ai-test-go/internal/handler"
	"ai-test-go/internal/job"
	"ai-test-go/internal/middleware"
	"ai-test-go/internal/repository"
	"ai-test-go/internal/service"
	"ai-test-go/internal/worker"
	"ai-test-go/pkg/logger"
	"ai-test-go/pkg/openrouter"
	"ai-test-go/pkg/rabbitmq"

	"github.com/gin-gonic/gin"
	swaggerFiles "github.com/swaggo/files"
	ginSwagger "github.com/swaggo/gin-swagger"
)

func main() {
	if err := config.LoadConfig(); err != nil {
		log.Fatalf("加载配置失败: %v", err)
	}

	if err := logger.Init(logger.Config{
		LogDir:     config.AppConfig.Log.Dir,
		Level:      config.AppConfig.Log.Level,
		MaxAge:     config.AppConfig.Log.MaxAge,
		MaxBackups: config.AppConfig.Log.MaxAge,
		Compress:   config.AppConfig.Log.Compress,
	}); err != nil {
		log.Fatalf("初始化日志失败: %v", err)
	}
	logger.Log.Info("日志系统初始化成功")

	if err := config.InitDatabase(); err != nil {
		logger.Log.Fatalf("初始化数据库失败: %v", err)
	}

	if err := config.InitRedis(); err != nil {
		logger.Log.Warnf("初始化Redis失败: %v (继续运行，但某些功能可能不可用)", err)
	} else {
		logger.Log.Info("Redis连接成功")
	}

	if err := config.InitRabbitMQ(); err != nil {
		logger.Log.Warnf("初始化RabbitMQ失败: %v (继续运行，但批量测试功能不可用)", err)
	} else {
		logger.Log.Info("RabbitMQ连接成功")
	}
	defer config.CloseRabbitMQ()

	gin.SetMode(config.AppConfig.Server.Mode)

	r := gin.Default()

	conversationMessageRepo := repository.NewConversationMessageRepository(config.DB)
	modelRepo := repository.NewModelRepository(config.DB)
	userModelUsageRepo := repository.NewUserModelUsageRepository(config.DB)
	userRepo := repository.NewUserRepository(config.DB)
	userService := service.NewUserService(userRepo, conversationMessageRepo, userModelUsageRepo, modelRepo)
	userHandler := handler.NewUserHandler(userService)

	testService := service.NewTestService()
	testHandler := handler.NewTestHandler(testService)

	conversationRepo := repository.NewConversationRepository(config.DB)
	conversationService := service.NewConversationService(conversationRepo, conversationMessageRepo, modelRepo)
	conversationHandler := handler.NewConversationHandler(conversationService)

	ratingRepo := repository.NewRatingRepository(config.DB)
	ratingService := service.NewRatingService(ratingRepo)
	ratingHandler := handler.NewRatingHandler(ratingService)

	modelService := service.NewModelService(modelRepo, config.RedisClient)
	modelHandler := handler.NewModelHandler(modelService)

	sceneRepo := repository.NewSceneRepository(config.DB)
	scenePromptRepo := repository.NewScenePromptRepository(config.DB)
	sceneService := service.NewSceneService(sceneRepo, scenePromptRepo, config.DB)
	sceneHandler := handler.NewSceneHandler(sceneService, userService)

	promptTemplateRepo := repository.NewPromptTemplateRepository(config.DB)
	promptTemplateService := service.NewPromptTemplateService(promptTemplateRepo)
	promptTemplateHandler := handler.NewPromptTemplateHandler(promptTemplateService)

	testTaskRepo := repository.NewTestTaskRepository(config.DB)
	testResultRepo := repository.NewTestResultRepository(config.DB)
	userModelUsageService := service.NewUserModelUsageService(userModelUsageRepo)
	progressService := service.NewProgressNotificationService()
	rabbitMQClient, _ := rabbitmq.NewRabbitMQClient()
	batchTestService := service.NewBatchTestService(testTaskRepo, testResultRepo, sceneRepo, scenePromptRepo, rabbitMQClient, config.DB)
	batchTestHandler := handler.NewBatchTestHandler(batchTestService, userService)

	reportService := service.NewReportService(testTaskRepo, testResultRepo)
	reportHandler := handler.NewReportHandler(reportService)

	openRouterClient := openrouter.NewClient(config.AppConfig.OpenRouter.APIKey, config.AppConfig.OpenRouter.BaseURL)

	aiScoringService := service.NewAIScoringService(openRouterClient, modelRepo, userModelUsageService)
	promptOptimizationService := service.NewPromptOptimizationService(openRouterClient, userModelUsageService)
	promptOptimizationHandler := handler.NewPromptOptimizationHandler(promptOptimizationService)

	stompHandler := handler.NewStompHandler(logger.Log)
	progressService.SetStompHandler(stompHandler)

	testWorker := worker.NewTestWorker(testResultRepo, testTaskRepo, batchTestService, modelService, userModelUsageService, progressService, openRouterClient, aiScoringService, rabbitMQClient)
	if err := testWorker.Start(); err != nil {
		logger.Log.Warnf("TestWorker启动失败: %v (批量测试功能不可用)", err)
	}

	syncModelJob := job.NewSyncModelJob(modelRepo, modelService)
	syncModelJob.Start()

	// 注册 SockJS handler（必须在 /api 之前，因为它需要处理 /api/ws/* 的所有请求）
	r.Any("/api/ws/*path", gin.WrapH(stompHandler.GetSockJSHandler()))

	api := r.Group("/api")
	{
		api.GET("/health", handler.NewHealthHandler().HealthCheck)

		user := api.Group("/user")
		{
			user.POST("/register", userHandler.UserRegister)
			user.POST("/login", userHandler.UserLogin)
			user.GET("/get/login", middleware.AuthMiddleware(), userHandler.GetLoginUser)
			user.POST("/logout", middleware.AuthMiddleware(), userHandler.UserLogout)
			user.POST("/add", middleware.AuthMiddleware(), middleware.AdminAuthMiddleware(), userHandler.AddUser)
			user.GET("/get", middleware.AuthMiddleware(), middleware.AdminAuthMiddleware(), userHandler.GetUserByID)
			user.GET("/get/vo", userHandler.GetUserVOByID)
			user.POST("/delete", middleware.AuthMiddleware(), middleware.AdminAuthMiddleware(), userHandler.DeleteUser)
			user.POST("/update", middleware.AuthMiddleware(), middleware.AdminAuthMiddleware(), userHandler.UpdateUser)
			user.POST("/list/page/vo", middleware.AuthMiddleware(), middleware.AdminAuthMiddleware(), userHandler.ListUserByPage)
			user.GET("/statistics", middleware.AuthMiddleware(), userHandler.GetUserStatistics)
		}

		test := api.Group("/test")
		{
			test.POST("/ai/simple", testHandler.TestAISimple)
			test.POST("/ai/stream", testHandler.TestAIStream)
		}

		conversation := api.Group("/conversation")
		{
			conversation.POST("/create", middleware.AuthMiddleware(), conversationHandler.CreateConversation)
			conversation.POST("/chat/stream", middleware.AuthMiddleware(), middleware.AIStreamRateLimit(), conversationHandler.ChatStream)
			conversation.POST("/side-by-side/stream", middleware.AuthMiddleware(), middleware.AIStreamRateLimit(), conversationHandler.SideBySideStream)
			conversation.POST("/prompt-lab/stream", middleware.AuthMiddleware(), middleware.AIStreamRateLimit(), conversationHandler.PromptLabStream)
			conversation.POST("/code-mode/stream", middleware.AuthMiddleware(), middleware.AIStreamRateLimit(), conversationHandler.CodeModeStream)
			conversation.POST("/code-mode/prompt-lab/stream", middleware.AuthMiddleware(), middleware.AIStreamRateLimit(), conversationHandler.CodeModePromptLabStream)
			conversation.GET("/get", middleware.AuthMiddleware(), conversationHandler.GetConversation)
			conversation.GET("/list", middleware.AuthMiddleware(), conversationHandler.ListConversations)
			conversation.GET("/messages", middleware.AuthMiddleware(), conversationHandler.GetConversationMessages)
			conversation.POST("/delete", middleware.AuthMiddleware(), conversationHandler.DeleteConversation)
		}

		rating := api.Group("/rating")
		{
			rating.POST("/add", middleware.AuthMiddleware(), ratingHandler.AddRating)
			rating.GET("/get", middleware.AuthMiddleware(), ratingHandler.GetRating)
			rating.GET("/list", middleware.AuthMiddleware(), ratingHandler.ListRatingsByConversation)
			rating.DELETE("/delete", middleware.AuthMiddleware(), ratingHandler.DeleteRating)
		}

		modelGroup := api.Group("/model")
		{
			modelGroup.POST("/list", modelHandler.ListModels)
			modelGroup.GET("/all", modelHandler.GetAllModels)
		}

		scene := api.Group("/scene")
		{
			scene.POST("/create", middleware.AuthMiddleware(), sceneHandler.CreateScene)
			scene.POST("/update", middleware.AuthMiddleware(), sceneHandler.UpdateScene)
			scene.POST("/delete", middleware.AuthMiddleware(), sceneHandler.DeleteScene)
			scene.GET("/get", middleware.AuthMiddleware(), sceneHandler.GetScene)
			scene.GET("/list", middleware.AuthMiddleware(), sceneHandler.ListScenes)
			scene.GET("/list/page", middleware.AuthMiddleware(), sceneHandler.ListScenesPage)
			scene.GET("/prompts", middleware.AuthMiddleware(), sceneHandler.GetScenePrompts)
			scene.POST("/prompt/add", middleware.AuthMiddleware(), sceneHandler.AddScenePrompt)
			scene.POST("/prompt/update", middleware.AuthMiddleware(), sceneHandler.UpdateScenePrompt)
			scene.POST("/prompt/delete", middleware.AuthMiddleware(), sceneHandler.DeleteScenePrompt)
		}

		promptTemplate := api.Group("/prompt/template")
		{
			promptTemplate.GET("/list", middleware.AuthMiddleware(), promptTemplateHandler.ListTemplates)
			promptTemplate.GET("/get", middleware.AuthMiddleware(), promptTemplateHandler.GetTemplate)
			promptTemplate.POST("/create", middleware.AuthMiddleware(), promptTemplateHandler.CreateTemplate)
			promptTemplate.POST("/update", middleware.AuthMiddleware(), promptTemplateHandler.UpdateTemplate)
			promptTemplate.POST("/delete", middleware.AuthMiddleware(), promptTemplateHandler.DeleteTemplate)
			promptTemplate.POST("/increment-usage", promptTemplateHandler.IncrementUsage)
		}

		promptOptimization := api.Group("/prompt/optimization")
		{
			promptOptimization.POST("/analyze", middleware.AuthMiddleware(), promptOptimizationHandler.OptimizePrompt)
		}

		report := api.Group("/report")
		{
			report.GET("/generate", middleware.AuthMiddleware(), reportHandler.GenerateReport)
		}

		batchTest := api.Group("/batch-test")
		{
			batchTest.POST("/create", middleware.AuthMiddleware(), middleware.BatchCreateRateLimit(), batchTestHandler.CreateBatchTestTask)
			batchTest.GET("/task/get", middleware.AuthMiddleware(), batchTestHandler.GetTask)
			batchTest.POST("/task/list/page", middleware.AuthMiddleware(), batchTestHandler.ListTasks)
			batchTest.POST("/task/delete", middleware.AuthMiddleware(), batchTestHandler.DeleteTask)
			batchTest.GET("/result/list", middleware.AuthMiddleware(), batchTestHandler.GetTaskResults)
			batchTest.POST("/result/rating", middleware.AuthMiddleware(), batchTestHandler.UpdateTestResultRating)
		}
	}

	r.GET("/swagger/*any", ginSwagger.WrapHandler(swaggerFiles.Handler))

	port := config.AppConfig.Server.Port
	addr := fmt.Sprintf(":%d", port)
	logger.Log.Infof("服务器启动成功，监听端口：%d", port)

	if err := r.Run(addr); err != nil {
		logger.Log.Fatalf("服务器启动失败: %v", err)
	}
}
