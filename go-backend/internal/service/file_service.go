// Package service 文件上传服务
// @author <a href="https://codefather.cn">编程导航学习圈</a>
package service

import (
	"ai-test-go/pkg/common"
	"ai-test-go/pkg/cos"
	"context"
	"fmt"
	"io"
	"log"
	"path"
	"strings"
)

const (
	MaxImageSizeBytes = 10 * 1024 * 1024
)

var allowedImageSuffix = map[string]bool{
	"jpeg": true, "jpg": true, "png": true, "gif": true, "webp": true,
}

var contentTypeToSuffix = map[string]string{
	"image/jpeg": "jpg",
	"image/png":  "png",
	"image/gif":  "gif",
	"image/webp": "webp",
}

// FileService 文件上传服务
type FileService struct {
	cosClient *cos.Client
}

// NewFileService 创建文件服务
func NewFileService(cosClient *cos.Client) *FileService {
	return &FileService{cosClient: cosClient}
}

// ValidImageFile 校验图片：大小不超过 10MB，类型为 jpeg/jpg/png/gif/webp
func (s *FileService) ValidImageFile(size int64, filename, contentType string) error {
	if size <= 0 {
		return common.NewBusinessException(common.PARAMS_ERROR, "文件为空")
	}
	if size > MaxImageSizeBytes {
		return common.NewBusinessException(common.PARAMS_ERROR, "图片大小不能超过 10M")
	}

	ext := strings.TrimPrefix(strings.ToLower(path.Ext(filename)), ".")
	if ext == "" && contentType != "" {
		if suf, ok := contentTypeToSuffix[strings.ToLower(contentType)]; ok {
			ext = suf
		}
	}
	if ext != "" && allowedImageSuffix[ext] {
		return nil
	}
	if contentType != "" && contentTypeToSuffix[strings.ToLower(contentType)] != "" {
		return nil
	}
	return common.NewBusinessException(common.PARAMS_ERROR, "图片类型错误")
}

// UploadImage 上传图片到 COS，路径为 /aitest/{userID}/images/{fileName}，返回可访问 URL
func (s *FileService) UploadImage(ctx context.Context, body io.Reader, fileName string, userID int64) (string, error) {
	if s.cosClient == nil {
		return "", common.NewBusinessException(common.SYSTEM_ERROR, "未配置对象存储")
	}
	key := path.Join("aitest", fmt.Sprintf("%d", userID), "images", fileName)
	key = strings.TrimPrefix(key, "/")
	if err := s.cosClient.PutObject(ctx, key, body); err != nil {
		log.Printf("COS 上传失败: %v", err)
		return "", common.NewBusinessException(common.SYSTEM_ERROR, "图片上传失败")
	}
	return s.cosClient.PublicURL(key), nil
}
