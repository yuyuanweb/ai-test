// Package dto AI 评分相关 DTO（单评委/多评委结果、评委分项分数）
// @author <a href="https://codefather.cn">编程导航学习圈</a>
package dto

// ScoreBreakdown 分项分数（与评分提示词中 JSON 结构一致）
type ScoreBreakdown struct {
	Accuracy    int `json:"accuracy"`
	Relevance   int `json:"relevance"`
	Completeness int `json:"completeness"`
	Clarity     int `json:"clarity"`
	Creativity   int `json:"creativity"`
}

// EvaluationResult 单次 AI 评分结果（解析评委模型返回的 JSON）
type EvaluationResult struct {
	Scores    ScoreBreakdown `json:"scores"`
	TotalScore int           `json:"total_score"`
	Rating     int           `json:"rating"`
	Comment    string        `json:"comment"`
}

// JudgeScore 单个评委的评分结果
type JudgeScore struct {
	Model      string         `json:"model"`
	Scores     ScoreBreakdown `json:"scores"`
	TotalScore int            `json:"totalScore"`
	Rating     int            `json:"rating"`
	Comment    string         `json:"comment"`
}

// AIScoreResult 多评委交叉验证后的 AI 评分结果（对外返回/存储）
type AIScoreResult struct {
	Judges         []JudgeScore `json:"judges"`
	AverageRating  float64      `json:"averageRating"`
	Consistency    float64      `json:"consistency"`
}
