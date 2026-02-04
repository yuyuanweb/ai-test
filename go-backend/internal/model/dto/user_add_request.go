// Package dto 用户创建请求
// @author <a href="https://codefather.cn">编程导航学习圈</a>
package dto

type UserAddRequest struct {
	UserName    string `json:"userName"`
	UserAccount string `json:"userAccount" binding:"required"`
	UserAvatar  string `json:"userAvatar"`
	UserProfile string `json:"userProfile"`
	UserRole    string `json:"userRole"`
}
