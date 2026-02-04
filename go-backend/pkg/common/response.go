// Package common 通用响应结构
// @author <a href="https://codefather.cn">编程导航学习圈</a>
package common

type BaseResponse struct {
	Code    int         `json:"code"`
	Message string      `json:"message"`
	Data    interface{} `json:"data,omitempty"`
}

func Success(data interface{}) *BaseResponse {
	return &BaseResponse{
		Code:    0,
		Message: "success",
		Data:    data,
	}
}

func Error(code int, message string) *BaseResponse {
	return &BaseResponse{
		Code:    code,
		Message: message,
	}
}

func ErrorWithData(code int, message string, data interface{}) *BaseResponse {
	return &BaseResponse{
		Code:    code,
		Message: message,
		Data:    data,
	}
}
