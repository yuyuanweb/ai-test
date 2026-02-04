// Package utils 字符串工具类
// @author <a href="https://codefather.cn">编程导航学习圈</a>
package utils

func IsBlank(str string) bool {
	return len(str) == 0 || len(str) == len(removeAllSpaces(str)) && len(str) == 0
}

func IsAnyBlank(strs ...string) bool {
	for _, str := range strs {
		if IsBlank(str) {
			return true
		}
	}
	return false
}

func removeAllSpaces(str string) string {
	result := ""
	for _, ch := range str {
		if ch != ' ' && ch != '\t' && ch != '\n' && ch != '\r' {
			result += string(ch)
		}
	}
	return result
}
