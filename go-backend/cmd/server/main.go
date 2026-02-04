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
	"ai-test-go/internal/middleware"
	"ai-test-go/internal/repository"
	"ai-test-go/internal/service"

	"github.com/gin-gonic/gin"
	swaggerFiles "github.com/swaggo/files"
	ginSwagger "github.com/swaggo/gin-swagger"
)

func main() {
	if err := config.LoadConfig(); err != nil {
		log.Fatalf("加载配置失败: %v", err)
	}

	if err := config.InitDatabase(); err != nil {
		log.Fatalf("初始化数据库失败: %v", err)
	}

	if err := config.InitRedis(); err != nil {
		log.Printf("警告: 初始化Redis失败: %v (继续运行，但某些功能可能不可用)", err)
	}

	gin.SetMode(config.AppConfig.Server.Mode)

	r := gin.Default()

	userRepo := repository.NewUserRepository(config.DB)
	userService := service.NewUserService(userRepo)
	userHandler := handler.NewUserHandler(userService)

	testService := service.NewTestService()
	testHandler := handler.NewTestHandler(testService)

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
	}

	r.GET("/swagger/*any", ginSwagger.WrapHandler(swaggerFiles.Handler))

	port := config.AppConfig.Server.Port
	addr := fmt.Sprintf(":%d", port)
	log.Printf("服务器启动成功，监听端口：%d", port)
	
	if err := r.Run(addr); err != nil {
		log.Fatalf("服务器启动失败: %v", err)
	}
}
