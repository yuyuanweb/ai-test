// Package vo 任务进度视图对象
// @author <a href="https://codefather.cn">编程导航学习圈</a>
package vo

type TaskProgressVO struct {
	TaskID            string `json:"taskId"`
	Percentage        int    `json:"percentage"`
	CompletedSubtasks int    `json:"completedSubtasks"`
	TotalSubtasks     int    `json:"totalSubtasks"`
	CurrentModel      string `json:"currentModel,omitempty"`
	CurrentPrompt     string `json:"currentPrompt,omitempty"`
	Status            string `json:"status"`
	Timestamp         int64  `json:"timestamp"`
}
