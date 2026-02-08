// Package dto AI评分相关DTO
// @author <a href="https://codefather.cn">编程导航学习圈</a>
package dto

// EvaluationResult 单次AI评分结果（LLM直接返回的JSON结构）
type EvaluationResult struct {
	Scores    ScoreDetail `json:"scores"`
	TotalScore int        `json:"total_score"`
	Rating     int        `json:"rating"`
	Comment    string     `json:"comment"`
}

// ScoreDetail 各维度分数
type ScoreDetail struct {
	Accuracy    int `json:"accuracy"`
	Relevance   int `json:"relevance"`
	Completeness int `json:"completeness"`
	Clarity     int `json:"clarity"`
	Creativity  int `json:"creativity"`
}

// JudgeScore 单个评委的评分结果
type JudgeScore struct {
	Model      string       `json:"model"`
	Scores     ScoreDetail  `json:"scores"`
	TotalScore int          `json:"totalScore"`
	Rating     int          `json:"rating"`
	Comment    string       `json:"comment"`
}

// AIScoreResult 多评委交叉验证后的评分结果（存储到 test_result.aiScore）
type AIScoreResult struct {
	Judges         []JudgeScore `json:"judges"`
	AverageRating  float64      `json:"averageRating"`
	Consistency    float64      `json:"consistency"`
}
