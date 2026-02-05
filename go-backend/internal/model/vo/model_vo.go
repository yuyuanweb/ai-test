// Package vo 模型视图对象
// @author <a href="https://codefather.cn">编程导航学习圈</a>
package vo

type ModelVO struct {
	ID                  string   `json:"id"`
	Name                string   `json:"name"`
	Description         string   `json:"description"`
	Provider            string   `json:"provider"`
	ContextLength       int      `json:"contextLength"`
	InputPrice          float64  `json:"inputPrice"`
	OutputPrice         float64  `json:"outputPrice"`
	Recommended         bool     `json:"recommended"`
	IsChina             bool     `json:"isChina"`
	SupportsMultimodal  bool     `json:"supportsMultimodal"`
	SupportsImageGen    bool     `json:"supportsImageGen"`
	SupportsToolCalling bool     `json:"supportsToolCalling"`
	Tags                []string `json:"tags"`
	TotalTokens         int64    `json:"totalTokens"`
	TotalCost           float64  `json:"totalCost"`
	UserTotalTokens     int64    `json:"userTotalTokens"`
	UserTotalCost       float64  `json:"userTotalCost"`
}
