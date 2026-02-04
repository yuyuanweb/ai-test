// Package utils 内存Session存储（Fallback）
// @author <a href="https://codefather.cn">编程导航学习圈</a>
package utils

import (
	"ai-test-go/internal/model"
	"sync"
	"time"
)

type MemorySession struct {
	User     *model.User
	ExpireAt time.Time
}

var (
	memoryStore = make(map[string]*MemorySession)
	storeMutex  sync.RWMutex
)

func SaveMemorySession(sessionID string, user *model.User) {
	storeMutex.Lock()
	defer storeMutex.Unlock()

	memoryStore[sessionID] = &MemorySession{
		User:     user,
		ExpireAt: time.Now().Add(SESSION_EXPIRE),
	}
}

func GetMemorySession(sessionID string) *model.User {
	storeMutex.RLock()
	defer storeMutex.RUnlock()

	session, exists := memoryStore[sessionID]
	if !exists {
		return nil
	}

	if time.Now().After(session.ExpireAt) {
		delete(memoryStore, sessionID)
		return nil
	}

	return session.User
}

func DeleteMemorySession(sessionID string) {
	storeMutex.Lock()
	defer storeMutex.Unlock()

	delete(memoryStore, sessionID)
}
