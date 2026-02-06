// Package dto 场景请求DTO
// @author <a href="https://codefather.cn">编程导航学习圈</a>
package dto

type CreateSceneRequest struct {
	Name        string `json:"name" binding:"required"`
	Description string `json:"description"`
	Category    string `json:"category"`
}

type UpdateSceneRequest struct {
	ID          string `json:"id" binding:"required"`
	Name        string `json:"name"`
	Description string `json:"description"`
	Category    string `json:"category"`
	IsActive    *int   `json:"isActive"`
}

type AddScenePromptRequest struct {
	SceneID        string `json:"sceneId" binding:"required"`
	Title          string `json:"title" binding:"required"`
	Content        string `json:"content" binding:"required"`
	Difficulty     string `json:"difficulty"`
	ExpectedOutput string `json:"expectedOutput"`
}

type UpdateScenePromptRequest struct {
	ID             string `json:"id" binding:"required"`
	Title          string `json:"title"`
	Content        string `json:"content"`
	Difficulty     string `json:"difficulty"`
	ExpectedOutput string `json:"expectedOutput"`
}
