// Package dto 用户注册请求
// @author <a href="https://codefather.cn">编程导航学习圈</a>
package dto

type UserRegisterRequest struct {
	UserAccount   string `json:"userAccount" binding:"required"`
	UserPassword  string `json:"userPassword" binding:"required"`
	CheckPassword string `json:"checkPassword" binding:"required"`
}
