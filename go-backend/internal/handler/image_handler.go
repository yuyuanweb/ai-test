// Package handler 图片生成 HTTP 处理器
// @author <a href="https://codefather.cn">编程导航学习圈</a>
package handler

import (
	"ai-test-go/internal/model/dto"
	"ai-test-go/internal/model/vo"
	"ai-test-go/internal/service"
	"ai-test-go/pkg/common"
	"ai-test-go/pkg/logger"
	"encoding/json"
	"net/http"

	"github.com/gin-gonic/gin"
)

// ImageHandler 图片生成处理器
type ImageHandler struct {
	imageService *service.ImageService
}

// NewImageHandler 创建图片生成处理器
func NewImageHandler(imageService *service.ImageService) *ImageHandler {
	return &ImageHandler{imageService: imageService}
}

// GenerateImageStream 流式生成图片（SSE）
// @Summary      流式生成图片
// @Description  输出思考过程与图片结果（SSE）
// @Tags         图片生成
// @Accept       json
// @Produce      text/event-stream
// @Param        request  body      dto.GenerateImageRequest  true  "生成请求"
// @Success      200      {object}  vo.ImageStreamChunkVO    "SSE 流"
// @Router       /image/generate/stream [post]
func (h *ImageHandler) GenerateImageStream(c *gin.Context) {
	userIDVal, exists := c.Get("userID")
	if !exists {
		c.JSON(http.StatusOK, common.Error(common.NOT_LOGIN_ERROR, "未登录"))
		return
	}
	userID, ok := userIDVal.(int64)
	if !ok {
		c.JSON(http.StatusOK, common.Error(common.NOT_LOGIN_ERROR, "未登录"))
		return
	}

	var req dto.GenerateImageRequest
	if err := c.ShouldBindJSON(&req); err != nil {
		logger.Log.Warnf("图片生成流式请求参数错误: %v", err)
		c.JSON(http.StatusOK, common.Error(common.PARAMS_ERROR, "参数错误"))
		return
	}

	c.Header("Content-Type", "text/event-stream")
	c.Header("Cache-Control", "no-cache")
	c.Header("Connection", "keep-alive")
	c.Header("X-Accel-Buffering", "no")
	c.Status(http.StatusOK)
	c.Writer.Flush()

	enc := json.NewEncoder(c.Writer)
	flush := func() {
		c.Writer.Flush()
	}

	err := h.imageService.GenerateImagesStream(c.Request.Context(), &req, userID, func(chunk vo.ImageStreamChunkVO) {
		if _, err := c.Writer.Write([]byte("data: ")); err != nil {
			return
		}
		if err := enc.Encode(chunk); err != nil {
			return
		}
		if _, err := c.Writer.Write([]byte("\n")); err != nil {
			return
		}
		flush()
	})
	if err != nil {
		logger.Log.Warnf("图片生成流式处理异常: %v", err)
	}
}
