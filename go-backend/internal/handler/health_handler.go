// Package handler HTTP处理器
// @author <a href="https://codefather.cn">编程导航学习圈</a>
package handler

import (
	"net/http"

	"github.com/gin-gonic/gin"
)

type HealthHandler struct{}

func NewHealthHandler() *HealthHandler {
	return &HealthHandler{}
}

// HealthCheck 健康检查接口
// @Summary      健康检查
// @Description  检查服务是否正常运行
// @Tags         系统管理
// @Accept       json
// @Produce      json
// @Success      200  {object}  map[string]interface{}  "成功"
// @Router       /health [get]
func (h *HealthHandler) HealthCheck(c *gin.Context) {
	c.JSON(http.StatusOK, gin.H{
		"code":    0,
		"message": "success",
		"data":    "Hello World! AI大模型评测平台 Go版 运行正常",
	})
}
