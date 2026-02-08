// Package dto 提示词模板请求
// @author <a href="https://codefather.cn">编程导航学习圈</a>
package dto

type CreatePromptTemplateRequest struct {
	Name        string   `json:"name" binding:"required"`
	Description string   `json:"description"`
	Strategy    string   `json:"strategy" binding:"required"`
	Content     string   `json:"content" binding:"required"`
	Variables   []string `json:"variables"`
	Category    string   `json:"category"`
}

type UpdatePromptTemplateRequest struct {
	ID          string   `json:"id" binding:"required"`
	Name        string   `json:"name"`
	Description string   `json:"description"`
	Strategy    string   `json:"strategy"`
	Content     string   `json:"content"`
	Variables   []string `json:"variables"`
	Category    string   `json:"category"`
	IsActive    *bool    `json:"isActive"`
}
