// Package dto 分页响应
// @author <a href="https://codefather.cn">编程导航学习圈</a>
package dto

type PageResponse struct {
	Total    int64       `json:"total"`
	PageNum  int64       `json:"pageNum"`
	PageSize int64       `json:"pageSize"`
	Records  interface{} `json:"records"`
}
