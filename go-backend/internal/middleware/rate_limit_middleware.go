// Package middleware 限流中间件，基于 Redis 实现分布式限流
// @author <a href="https://codefather.cn">编程导航学习圈</a>
package middleware

import (
	"ai-test-go/internal/config"
	"ai-test-go/internal/model"
	"ai-test-go/pkg/common"
	"ai-test-go/pkg/logger"
	"ai-test-go/pkg/utils"
	"encoding/json"
	"net/http"
	"strconv"
	"strings"

	"github.com/gin-gonic/gin"
)

const (
	rateLimitKeyPrefix = "rate_limit:"
)

const rateLimitLua = `
local current = redis.call('INCR', KEYS[1])
if current == 1 then
  redis.call('EXPIRE', KEYS[1], ARGV[1])
end
if current > tonumber(ARGV[2]) then
  return 0
end
return 1
`

type LimitType int

const (
	LimitTypeAPI LimitType = iota
	LimitTypeUser
	LimitTypeIP
)

type RateLimitConfig struct {
	Rate         int
	RateInterval int
	LimitType    LimitType
	Message      string
}

func RateLimitMiddleware(cfg RateLimitConfig) gin.HandlerFunc {
	return func(c *gin.Context) {
		if config.RedisClient == nil {
			c.Next()
			return
		}

		key := buildRateLimitKey(c, cfg)
		if key == "" {
			c.Next()
			return
		}

		fullKey := rateLimitKeyPrefix + key
		ttl := cfg.RateInterval
		if ttl < 1 {
			ttl = 60
		}
		rate := cfg.Rate
		if rate < 1 {
			rate = 10
		}

		res, err := config.RedisClient.Eval(c.Request.Context(), rateLimitLua, []string{fullKey}, ttl, rate).Result()
		if err != nil {
			logger.Log.Warnf("限流检查失败: key=%s, error=%v", fullKey, err)
			c.Next()
			return
		}

		allowed, _ := res.(int64)
		if allowed == 0 {
			msg := cfg.Message
			if msg == "" {
				msg = "请求过于频繁，请稍后再试"
			}
			if isSseRequest(c) {
				WriteSseError(c, common.TOO_MANY_REQUEST, msg)
			} else {
				c.JSON(http.StatusOK, common.Error(common.TOO_MANY_REQUEST, msg))
			}
			c.Abort()
			return
		}

		c.Next()
	}
}

func buildRateLimitKey(c *gin.Context, cfg RateLimitConfig) string {
	var sb strings.Builder
	sb.WriteString("ai:")

	switch cfg.LimitType {
	case LimitTypeAPI:
		sb.WriteString("api:")
		sb.WriteString(c.FullPath())
	case LimitTypeUser:
		userID := getUserIDFromContext(c)
		if userID > 0 {
			sb.WriteString("user:")
			sb.WriteString(strconv.FormatInt(userID, 10))
		} else {
			sb.WriteString("ip:")
			sb.WriteString(getClientIP(c))
		}
	case LimitTypeIP:
		sb.WriteString("ip:")
		sb.WriteString(getClientIP(c))
	default:
		sb.WriteString("ip:")
		sb.WriteString(getClientIP(c))
	}

	return sb.String()
}

func getUserIDFromContext(c *gin.Context) int64 {
	val, exists := c.Get("userID")
	if !exists {
		sessionID, _ := c.Cookie(config.AppConfig.Session.CookieName)
		if sessionID != "" {
			user, _ := utils.GetSession(c.Request.Context(), sessionID)
			if user == nil {
				user = utils.GetMemorySession(sessionID)
			}
			if user != nil {
				return user.ID
			}
		}
		return 0
	}

	switch v := val.(type) {
	case int64:
		return v
	case int:
		return int64(v)
	case *model.User:
		if v != nil {
			return v.ID
		}
	}
	return 0
}

func getClientIP(c *gin.Context) string {
	ip := c.GetHeader("X-Forwarded-For")
	if ip == "" || ip == "unknown" {
		ip = c.GetHeader("X-Real-IP")
	}
	if ip == "" || ip == "unknown" {
		ip = c.ClientIP()
	}
	if idx := strings.Index(ip, ","); idx > 0 {
		ip = strings.TrimSpace(ip[:idx])
	}
	if ip == "" {
		ip = "unknown"
	}
	return ip
}

func AIStreamRateLimit() gin.HandlerFunc {
	return RateLimitMiddleware(RateLimitConfig{
		Rate:         5,
		RateInterval: 60,
		LimitType:    LimitTypeUser,
		Message:      "AI 对话请求过于频繁，请稍后再试",
	})
}

func BatchCreateRateLimit() gin.HandlerFunc {
	return RateLimitMiddleware(RateLimitConfig{
		Rate:         3,
		RateInterval: 60,
		LimitType:    LimitTypeUser,
		Message:      "批量测试创建过于频繁，请稍后再试",
	})
}

func isSseRequest(c *gin.Context) bool {
	accept := c.GetHeader("Accept")
	if accept != "" && strings.Contains(accept, "text/event-stream") {
		return true
	}
	return strings.Contains(c.Request.URL.Path, "/stream")
}

func WriteSseError(c *gin.Context, code int, message string) {
	c.Header("Content-Type", "text/event-stream")
	c.Header("Cache-Control", "no-cache")
	c.Header("Connection", "keep-alive")

	payload := map[string]interface{}{
		"error":   true,
		"code":    code,
		"message": message,
	}
	data, _ := json.Marshal(payload)
	c.Writer.WriteString("event: business-error\ndata: ")
	c.Writer.Write(data)
	c.Writer.WriteString("\n\n")
	c.Writer.WriteString("event: done\ndata: {}\n\n")
	c.Writer.Flush()
}
