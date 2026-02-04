// Package common 错误码定义
// @author <a href="https://codefather.cn">编程导航学习圈</a>
package common

const (
	SUCCESS         = 0
	PARAMS_ERROR    = 40000
	NOT_LOGIN_ERROR = 40100
	NO_AUTH_ERROR   = 40101
	NOT_FOUND_ERROR = 40400
	FORBIDDEN_ERROR = 40300
	SYSTEM_ERROR    = 50000
	OPERATION_ERROR = 50001
)

var ErrorCodeMap = map[int]string{
	SUCCESS:         "操作成功",
	PARAMS_ERROR:    "请求参数错误",
	NOT_LOGIN_ERROR: "未登录",
	NO_AUTH_ERROR:   "无权限",
	NOT_FOUND_ERROR: "请求数据不存在",
	FORBIDDEN_ERROR: "禁止访问",
	SYSTEM_ERROR:    "系统内部异常",
	OPERATION_ERROR: "操作失败",
}

func GetErrorMessage(code int) string {
	if msg, ok := ErrorCodeMap[code]; ok {
		return msg
	}
	return "未知错误"
}
