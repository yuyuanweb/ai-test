// Package dto OpenRouter API响应结构
// @author <a href="https://codefather.cn">编程导航学习圈</a>
package dto

type OpenRouterModelResponse struct {
	Data []OpenRouterModel `json:"data"`
}

type OpenRouterModel struct {
	ID                  string       `json:"id"`
	Name                string       `json:"name"`
	Created             int64        `json:"created"`
	Description         string       `json:"description"`
	ContextLength       int          `json:"context_length"`
	Pricing             Pricing      `json:"pricing"`
	Architecture        Architecture `json:"architecture"`
	SupportedParameters []string     `json:"supported_parameters"`
}

type Pricing struct {
	Prompt     string `json:"prompt"`
	Completion string `json:"completion"`
}

type Architecture struct {
	InputModalities  []string `json:"input_modalities"`
	OutputModalities []string `json:"output_modalities"`
}
