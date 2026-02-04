// Package middleware 认证中间件
// @author <a href="https://codefather.cn">编程导航学习圈</a>
package middleware

import (
	"ai-test-go/internal/config"
	"ai-test-go/internal/model"
	"ai-test-go/pkg/common"
	"ai-test-go/pkg/utils"
	"log"
	"net/http"

	"github.com/gin-gonic/gin"
)

func AuthMiddleware() gin.HandlerFunc {
	return func(c *gin.Context) {
		cookieName := config.AppConfig.Session.CookieName
		sessionID, err := c.Cookie(cookieName)
		if err != nil || sessionID == "" {
			c.JSON(http.StatusOK, common.Error(common.NOT_LOGIN_ERROR, "未登录"))
			c.Abort()
			return
		}

		user, err := utils.GetSession(c.Request.Context(), sessionID)
		if err != nil {
			log.Printf("获取Redis Session失败: %v, 尝试内存存储", err)
			user = utils.GetMemorySession(sessionID)
		}

		if user == nil {
			c.JSON(http.StatusOK, common.Error(common.NOT_LOGIN_ERROR, "未登录"))
			c.Abort()
			return
		}

		if err := utils.RefreshSession(c.Request.Context(), sessionID); err != nil {
			log.Printf("刷新Session失败: %v", err)
		}

		c.Set("userID", user.ID)
		c.Set("user", user)

		c.Next()
	}
}

func AdminAuthMiddleware() gin.HandlerFunc {
	return func(c *gin.Context) {
		userInterface, exists := c.Get("user")
		if !exists {
			c.JSON(http.StatusOK, common.Error(common.NOT_LOGIN_ERROR, "未登录"))
			c.Abort()
			return
		}

		user, ok := userInterface.(*model.User)
		if !ok || user.UserRole != "admin" {
			c.JSON(http.StatusOK, common.Error(common.NO_AUTH_ERROR, "无权限"))
			c.Abort()
			return
		}

		c.Next()
	}
}
