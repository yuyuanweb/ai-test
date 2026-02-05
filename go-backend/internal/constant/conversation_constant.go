// Package constant 对话相关常量
// @author <a href="https://codefather.cn">编程导航学习圈</a>
package constant

const (
	MIN_MODELS_COUNT                 = 1
	MAX_MODELS_COUNT                 = 8
	MIN_PROMPT_VARIANTS_COUNT        = 2
	MAX_PROMPT_VARIANTS_COUNT        = 5
	MAX_TITLE_LENGTH                 = 30
	DEFAULT_TEMPERATURE              = 0.7
	DEFAULT_INPUT_PRICE_PER_MILLION  = 0.15
	DEFAULT_OUTPUT_PRICE_PER_MILLION = 0.60
	TOKENS_PER_MILLION               = 1000000.0
)

const CODE_MODE_SYSTEM_PROMPT = `你是一个专业的前端开发专家。用户会向你描述想要创建的网站或应用，你需要生成完整的HTML代码。

代码要求：
1. 生成完整的HTML网页代码（包含HTML、CSS和JavaScript）
2. 将HTML、CSS和JavaScript都写在同一个HTML文件中
3. CSS写在<style>标签内，JavaScript写在<script>标签内
4. 代码要完整可运行，可以直接在浏览器中打开
5. 使用现代化的CSS样式，界面要美观、专业
6. 确保代码有良好的注释

回复格式：
- 你可以先简要说明设计思路或实现要点
- 然后使用Markdown代码块输出完整的HTML代码
- 代码块格式：
` + "```html\n<!DOCTYPE html>\n<html>\n...\n</html>\n```" + `
- 代码后可以补充使用说明或功能说明

注意：虽然可以添加文字说明，但核心重点是生成可运行的HTML代码。`
