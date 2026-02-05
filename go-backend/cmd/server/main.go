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
	"ai-test-go/pkg/logger"

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

	gin.SetMode(config.AppConfig.Server.Mode)

	r := gin.Default()

	userRepo := repository.NewUserRepository(config.DB)
	userService := service.NewUserService(userRepo)
	userHandler := handler.NewUserHandler(userService)

	testService := service.NewTestService()
	testHandler := handler.NewTestHandler(testService)

	conversationRepo := repository.NewConversationRepository(config.DB)
	conversationMessageRepo := repository.NewConversationMessageRepository(config.DB)
	modelRepo := repository.NewModelRepository(config.DB)
	conversationService := service.NewConversationService(conversationRepo, conversationMessageRepo, modelRepo)
	conversationHandler := handler.NewConversationHandler(conversationService)

	ratingRepo := repository.NewRatingRepository(config.DB)
	ratingService := service.NewRatingService(ratingRepo)
	ratingHandler := handler.NewRatingHandler(ratingService)

	modelService := service.NewModelService(modelRepo)
	modelHandler := handler.NewModelHandler(modelService)

	syncModelJob := job.NewSyncModelJob(modelRepo)
	syncModelJob.Start()

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
		}

		test := api.Group("/test")
		{
			test.POST("/ai/simple", testHandler.TestAISimple)
			test.POST("/ai/stream", testHandler.TestAIStream)
		}

		conversation := api.Group("/conversation")
		{
			conversation.POST("/create", middleware.AuthMiddleware(), conversationHandler.CreateConversation)
			conversation.POST("/chat/stream", middleware.AuthMiddleware(), conversationHandler.ChatStream)
			conversation.POST("/side-by-side/stream", middleware.AuthMiddleware(), conversationHandler.SideBySideStream)
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
	}

	r.GET("/swagger/*any", ginSwagger.WrapHandler(swaggerFiles.Handler))

	port := config.AppConfig.Server.Port
	addr := fmt.Sprintf(":%d", port)
	logger.Log.Infof("服务器启动成功，监听端口：%d", port)

	if err := r.Run(addr); err != nil {
		logger.Log.Fatalf("服务器启动失败: %v", err)
	}
}
