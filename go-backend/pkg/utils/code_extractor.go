package utils

import (
	"regexp"
	"strings"

	"ai-test-go/internal/model/dto"
	"github.com/sirupsen/logrus"
)

/**
 * 代码提取工具类
 *
 * @author <a href="https://codefather.cn">编程导航学习圈</a>
 */

var (
	// Markdown代码块正则表达式
	// 匹配：```language\ncode```
	codeBlockPattern = regexp.MustCompile("```(\\w*)\\n([\\s\\S]*?)```")
	
	// HTML标签检测正则表达式
	// 用于检测文本中是否包含HTML标签
	htmlTagPattern = regexp.MustCompile("(?i)<!DOCTYPE\\s+html|<html|<head|<body|<div|<span|<p|<h[1-6]|<script|<style")
)

// ExtractCodeBlocks 从文本中提取所有代码块
func ExtractCodeBlocks(text string) []dto.CodeBlock {
	codeBlocks := make([]dto.CodeBlock, 0)
	
	if text == "" {
		return codeBlocks
	}
	
	// 查找所有代码块
	matches := codeBlockPattern.FindAllStringSubmatchIndex(text, -1)
	
	for _, match := range matches {
		// match[0], match[1]: 整个匹配的开始和结束位置
		// match[2], match[3]: language的开始和结束位置
		// match[4], match[5]: code的开始和结束位置
		
		language := ""
		if match[2] >= 0 && match[3] >= 0 {
			language = text[match[2]:match[3]]
		}
		
		code := ""
		if match[4] >= 0 && match[5] >= 0 {
			code = text[match[4]:match[5]]
		}
		
		if language == "" {
			language = "text"
		}
		
		codeBlock := dto.CodeBlock{
			Language:   strings.ToLower(language),
			Code:       code,
			StartIndex: match[0],
			EndIndex:   match[1],
		}
		
		// 如果是HTML代码，进行安全处理
		if strings.ToLower(language) == "html" {
			codeBlock.SanitizedHTML = SanitizeHTML(code)
		}
		
		codeBlocks = append(codeBlocks, codeBlock)
		
		logrus.Debugf("提取到代码块: language=%s, length=%d", language, len(code))
	}
	
	// 如果没有提取到代码块，但文本中包含HTML标签，尝试提取HTML代码
	if len(codeBlocks) == 0 && htmlTagPattern.MatchString(text) {
		logrus.Info("未找到Markdown代码块，但检测到HTML标签，尝试提取HTML代码")
		
		htmlCode := extractHTMLFromText(text)
		if htmlCode != "" {
			htmlBlock := dto.CodeBlock{
				Language:      "html",
				Code:          htmlCode,
				SanitizedHTML: SanitizeHTML(htmlCode),
				StartIndex:    0,
				EndIndex:      len(text),
			}
			codeBlocks = append(codeBlocks, htmlBlock)
			
			logrus.Infof("提取到HTML代码块: length=%d", len(htmlCode))
		}
	}
	
	logrus.Infof("共提取到 %d 个代码块", len(codeBlocks))
	
	return codeBlocks
}

// extractHTMLFromText 从文本中提取HTML代码
// 如果文本中包含完整的HTML文档（从<!DOCTYPE或<html开始），提取整个HTML
// 否则，提取第一个包含HTML标签的代码段
func extractHTMLFromText(text string) string {
	if text == "" {
		return ""
	}
	
	// 尝试提取完整的HTML文档（从<!DOCTYPE或<html开始，到</html>结束）
	htmlDocPattern := regexp.MustCompile("(?is)(<!DOCTYPE\\s+html[\\s\\S]*?</html>)")
	if matches := htmlDocPattern.FindStringSubmatch(text); len(matches) > 1 {
		htmlDoc := matches[1]
		logrus.Debugf("提取到完整HTML文档: length=%d", len(htmlDoc))
		return htmlDoc
	}
	
	// 如果没有完整的HTML文档，尝试提取HTML片段（从第一个HTML标签开始，到最后一个标签结束）
	htmlFragmentPattern := regexp.MustCompile("(?is)(<[^>]+>[\\s\\S]*?</[^>]+>)")
	if htmlFragmentPattern.MatchString(text) {
		// 找到所有HTML标签，提取从第一个到最后一个之间的内容
		firstTagStart := strings.Index(text, "<")
		lastTagEnd := strings.LastIndex(text, ">")
		if firstTagStart >= 0 && lastTagEnd > firstTagStart {
			htmlFragment := text[firstTagStart : lastTagEnd+1]
			logrus.Debugf("提取到HTML片段: length=%d", len(htmlFragment))
			return htmlFragment
		}
	}
	
	// 如果都没有找到，返回包含HTML标签的整个文本（作为兜底方案）
	if htmlTagPattern.MatchString(text) {
		logrus.Debugf("使用整个文本作为HTML代码: length=%d", len(text))
		return text
	}
	
	return ""
}

// SanitizeHTML HTML代码预览处理
//
// 注意：此方法直接返回原始HTML代码，不进行清理
// 安全性由前端iframe的sandbox属性保证：
// - sandbox="allow-scripts" 允许JavaScript运行（支持交互功能）
// - 禁止访问父页面（防止窃取用户数据）
// - 禁止网络请求（防止CSRF攻击）
// - 禁止localStorage访问（防止数据泄露）
//
// 这样既保证了代码预览的完整功能（计算器、时钟等），又确保了安全性
func SanitizeHTML(html string) string {
	if html == "" {
		return ""
	}
	
	// 代码预览场景：直接返回原始HTML
	// iframe sandbox机制提供足够的安全隔离
	logrus.Debugf("HTML预览处理: 长度=%d", len(html))
	return html
}
