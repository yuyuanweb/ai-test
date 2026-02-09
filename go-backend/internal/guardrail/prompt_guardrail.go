// Package guardrail Prompt 安全审查护轨：长度、敏感词、注入模式检测
// @author <a href="https://codefather.cn">编程导航学习圈</a>
package guardrail

import (
	"ai-test-go/pkg/common"
	"regexp"
	"strconv"
	"strings"
)

const MaxPromptLength = 4000

var sensitiveWords = []string{
	"忽略之前的指令", "ignore previous instructions", "ignore above", "ignore all",
	"破解", "hack", "绕过", "bypass", "越狱", "jailbreak",
	"无视", "disregard", "forget everything",
}

var injectionPatterns = []*regexp.Regexp{
	regexp.MustCompile(`(?i)ignore\s+(?:previous|above|all)\s+(?:instructions?|commands?|prompts?)`),
	regexp.MustCompile(`(?i)(?:forget|disregard)\s+(?:everything|all)\s+(?:above|before)`),
	regexp.MustCompile(`(?i)(?:pretend|act|behave)\s+(?:as|like)\s+(?:if|you\s+are)`),
	regexp.MustCompile(`(?i)system\s*:\s*you\s+are`),
	regexp.MustCompile(`(?i)new\s+(?:instructions?|commands?|prompts?)\s*:`),
}

func Validate(prompt string) error {
	if prompt == "" {
		return common.NewBusinessException(common.PARAMS_ERROR, "输入内容不能为空")
	}
	t := strings.TrimSpace(prompt)
	if t == "" {
		return common.NewBusinessException(common.PARAMS_ERROR, "输入内容不能为空")
	}
	if len([]rune(t)) > MaxPromptLength {
		return common.NewBusinessException(common.PARAMS_ERROR, "输入内容过长，请勿超过 "+strconv.Itoa(MaxPromptLength)+" 字")
	}
	lower := strings.ToLower(t)
	for _, w := range sensitiveWords {
		if strings.Contains(lower, strings.ToLower(w)) {
			return common.NewBusinessException(common.PARAMS_ERROR, "输入包含不当内容，请修改后重试")
		}
	}
	for _, p := range injectionPatterns {
		if p.MatchString(t) {
			return common.NewBusinessException(common.PARAMS_ERROR, "检测到恶意输入，请求被拒绝")
		}
	}
	return nil
}
