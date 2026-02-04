// Package utils Session工具类
// @author <a href="https://codefather.cn">编程导航学习圈</a>
package utils

import (
	"context"
	"crypto/rand"
	"encoding/hex"
	"encoding/json"
	"fmt"
	"time"

	"ai-test-go/internal/config"
	"ai-test-go/internal/model"

	"github.com/redis/go-redis/v9"
)

const (
	SESSION_PREFIX = "session:"
	SESSION_EXPIRE = 30 * 24 * time.Hour
)

func GenerateSessionID() string {
	b := make([]byte, 16)
	rand.Read(b)
	return hex.EncodeToString(b)
}

func SaveSession(ctx context.Context, sessionID string, user *model.User) error {
	if config.RedisClient == nil {
		return fmt.Errorf("Redis未初始化")
	}

	key := SESSION_PREFIX + sessionID
	userData, err := json.Marshal(user)
	if err != nil {
		return err
	}

	return config.RedisClient.Set(ctx, key, userData, SESSION_EXPIRE).Err()
}

func GetSession(ctx context.Context, sessionID string) (*model.User, error) {
	if config.RedisClient == nil {
		return nil, fmt.Errorf("Redis未初始化")
	}

	key := SESSION_PREFIX + sessionID
	userData, err := config.RedisClient.Get(ctx, key).Result()
	if err != nil {
		if err == redis.Nil {
			return nil, nil
		}
		return nil, err
	}

	var user model.User
	if err := json.Unmarshal([]byte(userData), &user); err != nil {
		return nil, err
	}

	return &user, nil
}

func DeleteSession(ctx context.Context, sessionID string) error {
	if config.RedisClient == nil {
		return fmt.Errorf("Redis未初始化")
	}

	key := SESSION_PREFIX + sessionID
	return config.RedisClient.Del(ctx, key).Err()
}

func RefreshSession(ctx context.Context, sessionID string) error {
	if config.RedisClient == nil {
		return fmt.Errorf("Redis未初始化")
	}

	key := SESSION_PREFIX + sessionID
	return config.RedisClient.Expire(ctx, key, SESSION_EXPIRE).Err()
}
