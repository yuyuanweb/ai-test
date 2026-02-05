package dto

/**
 * 代码块 DTO
 *
 * @author <a href="https://codefather.cn">编程导航学习圈</a>
 */
type CodeBlock struct {
	// 代码语言
	Language string `json:"language" binding:"required"`
	
	// 代码内容
	Code string `json:"code" binding:"required"`
	
	// 起始位置
	StartIndex int `json:"startIndex"`
	
	// 结束位置
	EndIndex int `json:"endIndex"`
	
	// 清理后的HTML（仅HTML代码块有值）
	SanitizedHTML string `json:"sanitizedHtml,omitempty"`
}
