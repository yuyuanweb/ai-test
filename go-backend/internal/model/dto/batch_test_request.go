// Package dto 批量测试请求DTO
// @author <a href="https://codefather.cn">编程导航学习圈</a>
package dto

type CreateBatchTestRequest struct {
	Name              string   `json:"name"`
	SceneID           string   `json:"sceneId" binding:"required"`
	Models            []string `json:"models" binding:"required"`
	Temperature       *float32 `json:"temperature"`
	TopP              *float32 `json:"topP"`
	MaxTokens         *int     `json:"maxTokens"`
	TopK              *int     `json:"topK"`
	FrequencyPenalty  *float32 `json:"frequencyPenalty"`
	PresencePenalty   *float32 `json:"presencePenalty"`
	EnableAiScoring   *bool    `json:"enableAiScoring"`
}

type SubTaskMessage struct {
	TaskID        string `json:"taskId"`
	SceneID       string `json:"sceneId"`
	PromptID      string `json:"promptId"`
	PromptTitle   string `json:"promptTitle"`
	PromptContent string `json:"promptContent"`
	ModelName     string `json:"modelName"`
	UserID        int64  `json:"userId"`
	Config        string `json:"config"`
	RetryCount    int    `json:"retryCount"`
}

type TaskQueryRequest struct {
	PageNum  int64  `json:"pageNum"`
	PageSize int64  `json:"pageSize"`
	Status   string `json:"status"`
}

type UpdateTestResultRatingRequest struct {
	ResultID   string `json:"resultId" binding:"required"`
	UserRating int    `json:"userRating" binding:"required,min=1,max=5"`
}
