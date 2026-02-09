// Package handler 上传 HTTP 处理器
// @author <a href="https://codefather.cn">编程导航学习圈</a>
package handler

import (
	"ai-test-go/internal/model/vo"
	"ai-test-go/internal/service"
	"ai-test-go/pkg/common"
	"ai-test-go/pkg/utils"
	"context"
	"log"
	"net/http"
	"path"
	"strings"

	"github.com/gin-gonic/gin"
)

const uploadFileNameLen = 16

// UploadHandler 上传接口
type UploadHandler struct {
	fileService *service.FileService
}

// NewUploadHandler 创建上传处理器
func NewUploadHandler(fileService *service.FileService) *UploadHandler {
	return &UploadHandler{fileService: fileService}
}

// UploadImage 图片上传（用于多模态输入）
// @Summary      图片上传
// @Description  上传图片用于多模态对话，支持 JPG/PNG/GIF/WebP，单张最大 10MB
// @Tags         上传
// @Accept       multipart/form-data
// @Produce      json
// @Param        file  formData  file   true  "图片文件"
// @Success      200   {object}  common.BaseResponse{data=vo.UploadImageVO}
// @Failure      400   {object}  common.BaseResponse
// @Router       /upload/image [post]
func (h *UploadHandler) UploadImage(c *gin.Context) {
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

	fileHeader, err := c.FormFile("file")
	if err != nil {
		log.Printf("获取上传文件失败: %v", err)
		c.JSON(http.StatusOK, common.Error(common.PARAMS_ERROR, "请选择图片文件"))
		return
	}

	originalFilename := fileHeader.Filename
	contentType := fileHeader.Header.Get("Content-Type")
	if err := h.fileService.ValidImageFile(fileHeader.Size, originalFilename, contentType); err != nil {
		if bizErr, ok := err.(*common.BusinessException); ok {
			c.JSON(http.StatusOK, common.Error(bizErr.Code, bizErr.Message))
		} else {
			c.JSON(http.StatusOK, common.Error(common.PARAMS_ERROR, "图片校验失败"))
		}
		return
	}

	suffix := strings.TrimPrefix(strings.ToLower(path.Ext(originalFilename)), ".")
	if suffix == "" {
		if ct, ok := map[string]string{"image/jpeg": "jpg", "image/png": "png", "image/gif": "gif", "image/webp": "webp"}[strings.ToLower(contentType)]; ok {
			suffix = ct
		} else {
			suffix = "jpg"
		}
	}
	fileName := strings.ReplaceAll(utils.GenerateUUID(), "-", "")[:uploadFileNameLen] + "." + suffix

	file, err := fileHeader.Open()
	if err != nil {
		log.Printf("打开上传文件失败: %v", err)
		c.JSON(http.StatusOK, common.Error(common.SYSTEM_ERROR, "图片上传失败"))
		return
	}
	defer file.Close()

	url, err := h.fileService.UploadImage(context.Background(), file, fileName, userID)
	if err != nil {
		if bizErr, ok := err.(*common.BusinessException); ok {
			c.JSON(http.StatusOK, common.Error(bizErr.Code, bizErr.Message))
		} else {
			c.JSON(http.StatusOK, common.Error(common.SYSTEM_ERROR, "图片上传失败"))
		}
		return
	}

	res := &vo.UploadImageVO{
		URL:              url,
		OriginalFilename: originalFilename,
		Size:             fileHeader.Size,
		ContentType:      contentType,
	}
	c.JSON(http.StatusOK, common.Success(res))
}
