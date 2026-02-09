// Package utils AI 非流式调用重试：仅对可重试异常（超时、5xx、429）重试，最多 3 次，固定间隔 2 秒
// @author <a href="https://codefather.cn">编程导航学习圈</a>
package utils

import (
	"errors"
	"strings"
	"time"
)

const (
	aiRetryMaxAttempts = 3
	aiRetryWaitSeconds = 2
)

// RunWithRetry 对幂等的非流式 AI 调用执行重试，仅对可重试异常重试
func RunWithRetry[T any](fn func() (T, error)) (T, error) {
	var lastErr error
	for attempt := 1; attempt <= aiRetryMaxAttempts; attempt++ {
		result, err := fn()
		if err == nil {
			return result, nil
		}
		lastErr = err
		if !IsRetryable(err) {
			return result, err
		}
		if attempt < aiRetryMaxAttempts {
			time.Sleep(aiRetryWaitSeconds * time.Second)
		}
	}
	var zero T
	return zero, lastErr
}

// IsRetryable 判断异常是否可重试（超时、5xx、429、连接重置等）
func IsRetryable(err error) bool {
	for err != nil {
		msg := strings.ToLower(err.Error())
		retryableSubstrs := []string{
			"429", "rate limit", "timeout", "timed out", "connection reset",
			"502", "503", "504", "context deadline exceeded",
		}
		for _, s := range retryableSubstrs {
			if strings.Contains(msg, s) {
				return true
			}
		}
		err = errors.Unwrap(err)
	}
	return false
}
