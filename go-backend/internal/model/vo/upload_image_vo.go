// Package vo 视图对象
// @author <a href="https://codefather.cn">编程导航学习圈</a>
package vo

// UploadImageVO 图片上传响应（与前端约定一致）
type UploadImageVO struct {
	URL              string `json:"url"`
	OriginalFilename string `json:"originalFilename"`
	Size             int64  `json:"size"`
	ContentType      string `json:"contentType"`
}
