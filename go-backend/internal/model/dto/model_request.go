// Package dto 模型相关请求DTO
// @author <a href="https://codefather.cn">编程导航学习圈</a>
package dto

type ModelQueryRequest struct {
	Current                int64  `json:"current"`
	PageSize               int64  `json:"pageSize"`
	SearchText             string `json:"searchText"`
	Provider               string `json:"provider"`
	OnlyRecommended        *bool  `json:"onlyRecommended"`
	OnlyChina              *bool  `json:"onlyChina"`
	OnlySupportsImageGen   *bool  `json:"onlySupportsImageGen"`
	OnlySupportsMultimodal *bool  `json:"onlySupportsMultimodal"`
}
