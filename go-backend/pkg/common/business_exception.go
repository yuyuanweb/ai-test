// Package common 业务异常定义
// @author <a href="https://codefather.cn">编程导航学习圈</a>
package common

import "fmt"

type BusinessException struct {
	Code    int
	Message string
}

func (e *BusinessException) Error() string {
	return fmt.Sprintf("业务异常: code=%d, message=%s", e.Code, e.Message)
}

func NewBusinessException(code int, message string) *BusinessException {
	return &BusinessException{
		Code:    code,
		Message: message,
	}
}

func NewBusinessExceptionWithDefaultMsg(code int) *BusinessException {
	return &BusinessException{
		Code:    code,
		Message: GetErrorMessage(code),
	}
}
