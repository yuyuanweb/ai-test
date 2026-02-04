// Package dto 删除请求
// @author <a href="https://codefather.cn">编程导航学习圈</a>
package dto

type DeleteRequest struct {
	ID int64 `json:"id" binding:"required"`
}
