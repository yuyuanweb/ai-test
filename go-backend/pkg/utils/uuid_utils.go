// Package utils UUID生成工具
// @author <a href="https://codefather.cn">编程导航学习圈</a>
package utils

import (
	"crypto/rand"
	"encoding/hex"
	"fmt"
)

func GenerateUUID() string {
	b := make([]byte, 16)
	_, err := rand.Read(b)
	if err != nil {
		return fmt.Sprintf("%d", GenerateSnowflakeID())
	}

	b[6] = (b[6] & 0x0f) | 0x40
	b[8] = (b[8] & 0x3f) | 0x80

	return fmt.Sprintf("%x-%x-%x-%x-%x", b[0:4], b[4:6], b[6:8], b[8:10], b[10:])
}

func GenerateSnowflakeID() int64 {
	b := make([]byte, 8)
	rand.Read(b)
	id := int64(0)
	for i := 0; i < 8; i++ {
		id = (id << 8) | int64(b[i])
	}
	if id < 0 {
		id = -id
	}
	return id
}

func BytesToHex(b []byte) string {
	return hex.EncodeToString(b)
}
