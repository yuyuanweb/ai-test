// Package dto 用户登录请求
// @author <a href="https://codefather.cn">编程导航学习圈</a>
package dto

type UserLoginRequest struct {
	UserAccount  string `json:"userAccount" binding:"required"`
	UserPassword string `json:"userPassword" binding:"required"`
}
