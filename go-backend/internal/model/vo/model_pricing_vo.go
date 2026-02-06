// Package vo 模型价格视图对象
// @author <a href="https://codefather.cn">编程导航学习圈</a>
package vo

type ModelPricingVO struct {
	InputPrice  float64 `json:"inputPrice"`
	OutputPrice float64 `json:"outputPrice"`
}
