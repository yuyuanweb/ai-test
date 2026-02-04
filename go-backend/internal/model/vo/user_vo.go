// Package vo 用户视图对象
// @author <a href="https://codefather.cn">编程导航学习圈</a>
package vo

import "time"

type UserVO struct {
	ID          int64     `json:"id"`
	UserAccount string    `json:"userAccount"`
	UserName    string    `json:"userName"`
	UserAvatar  string    `json:"userAvatar"`
	UserProfile string    `json:"userProfile"`
	UserRole    string    `json:"userRole"`
	CreateTime  time.Time `json:"createTime"`
}
