// Package service AI 评分服务接口定义
// @author <a href="https://codefather.cn">编程导航学习圈</a>
package service

import (
	"ai-test-go/internal/model/dto"
)

// AIScoringService AI 评分服务：单评委评分、多评委交叉验证
type AIScoringService interface {
	// Score 对模型回答进行 AI 评分（单评委）
	// question 问题，modelResponse 模型回答，userID 用于统计模型使用量
	Score(question, modelResponse string, userID int64) (*dto.EvaluationResult, error)

	// ScoreWithMultipleJudges 对模型回答进行多评委交叉验证评分
	// testedModelName 被测试的模型名称，用于排除该模型避免自己评自己
	ScoreWithMultipleJudges(question, modelResponse, testedModelName string, userID int64) (*dto.AIScoreResult, error)
}
