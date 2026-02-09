// Package cos 腾讯云 COS 对象存储客户端封装
// @author <a href="https://codefather.cn">编程导航学习圈</a>
package cos

import (
	"context"
	"fmt"
	"io"
	"net/http"
	"net/url"

	"github.com/tencentyun/cos-go-sdk-v5"
)

const (
	SchemeHTTPS = "https"
)

// Client 腾讯云 COS 客户端
type Client struct {
	client *cos.Client
	bucket string
	region string
	host   string
}

// Config COS 配置（与 internal/config 解耦，便于测试）
type Config struct {
	AccessKey string
	SecretKey string
	Region    string
	Bucket   string
	Host     string
}

// NewClient 根据配置创建 COS 客户端
func NewClient(cfg *Config) (*Client, error) {
	if cfg == nil || cfg.Bucket == "" || cfg.Region == "" {
		return nil, fmt.Errorf("cos config incomplete: bucket and region required")
	}

	bucketURLStr := cfg.Host
	if bucketURLStr == "" {
		bucketURLStr = fmt.Sprintf("%s://%s.cos.%s.myqcloud.com", SchemeHTTPS, cfg.Bucket, cfg.Region)
	}
	u, err := url.Parse(bucketURLStr)
	if err != nil {
		return nil, fmt.Errorf("parse cos bucket url: %w", err)
	}

	b := &cos.BaseURL{BucketURL: u}
	transport := &cos.AuthorizationTransport{
		SecretID:  cfg.AccessKey,
		SecretKey: cfg.SecretKey,
	}
	client := cos.NewClient(b, &http.Client{Transport: transport})

	publicHost := cfg.Host
	if publicHost == "" {
		publicHost = fmt.Sprintf("%s://%s.cos.%s.myqcloud.com", SchemeHTTPS, cfg.Bucket, cfg.Region)
	}

	return &Client{
		client: client,
		bucket: cfg.Bucket,
		region: cfg.Region,
		host:   publicHost,
	}, nil
}

// PutObject 上传对象，key 为对象在桶内的路径（如 /aitest/1/images/xxx.jpg）
func (c *Client) PutObject(ctx context.Context, key string, body io.Reader) error {
	_, err := c.client.Object.Put(ctx, key, body, nil)
	return err
}

// PublicURL 返回对象的公网访问 URL
func (c *Client) PublicURL(key string) string {
	if key == "" {
		return ""
	}
	if key[0] != '/' {
		key = "/" + key
	}
	return c.host + key
}
