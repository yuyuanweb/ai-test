// Package dto 用户查询请求
// @author <a href="https://codefather.cn">编程导航学习圈</a>
package dto

type UserQueryRequest struct {
	PageNum     int64  `json:"pageNum"`
	PageSize    int64  `json:"pageSize"`
	ID          *int64 `json:"id"`
	UserName    string `json:"userName"`
	UserAccount string `json:"userAccount"`
	UserProfile string `json:"userProfile"`
	UserRole    string `json:"userRole"`
	SortField   string `json:"sortField"`
	SortOrder   string `json:"sortOrder"`
}
