// Package utils 加密工具类
// @author <a href="https://codefather.cn">编程导航学习圈</a>
package utils

import (
	"crypto/md5"
	"encoding/hex"
)

const SALT = "yupi"

func EncryptPassword(password string) string {
	data := []byte(password + SALT)
	hash := md5.Sum(data)
	return hex.EncodeToString(hash[:])
}
