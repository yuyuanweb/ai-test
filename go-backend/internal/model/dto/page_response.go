// Package dto 分页响应
// @author <a href="https://codefather.cn">编程导航学习圈</a>
package dto

type PageResponse struct {
	Records interface{} `json:"records"`
	Total   int64       `json:"total"`
	Size    int64       `json:"size"`
	Current int64       `json:"current"`
	Pages   int64       `json:"pages"`
}

func NewPageResponse(records interface{}, total, current, size int64) *PageResponse {
	pages := total / size
	if total%size != 0 {
		pages++
	}
	return &PageResponse{
		Records: records,
		Total:   total,
		Size:    size,
		Current: current,
		Pages:   pages,
	}
}
