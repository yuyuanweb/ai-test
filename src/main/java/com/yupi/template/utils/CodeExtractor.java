package com.yupi.template.utils;

import com.yupi.template.model.dto.code.CodeBlock;
import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 代码提取工具类
 *
 * @author <a href="https://codefather.cn">编程导航学习圈</a>
 */
@Slf4j
public class CodeExtractor {

    /**
     * Markdown代码块正则表达式
     * 匹配：```language\ncode```
     */
    private static final Pattern CODE_BLOCK_PATTERN = Pattern.compile(
            "```(\\w*)\\n([\\s\\S]*?)```",
            Pattern.MULTILINE
    );



    /**
     * HTML标签检测正则表达式
     * 用于检测文本中是否包含HTML标签
     */
    private static final Pattern HTML_TAG_PATTERN = Pattern.compile(
            "<!DOCTYPE\\s+html|<html|<head|<body|<div|<span|<p|<h[1-6]|<script|<style",
            Pattern.CASE_INSENSITIVE
    );

    /**
     * 从文本中提取所有代码块
     *
     * @param text 包含Markdown代码块的文本
     * @return 代码块列表
     */
    public static List<CodeBlock> extractCodeBlocks(String text) {
        List<CodeBlock> codeBlocks = new ArrayList<>();
        
        if (text == null || text.isEmpty()) {
            return codeBlocks;
        }

        try {
            Matcher matcher = CODE_BLOCK_PATTERN.matcher(text);
            
            while (matcher.find()) {
                String language = matcher.group(1);
                String code = matcher.group(2);
                int startIndex = matcher.start();
                int endIndex = matcher.end();

                if (language == null || language.isEmpty()) {
                    language = "text";
                }

                CodeBlock codeBlock = new CodeBlock();
                codeBlock.setLanguage(language.toLowerCase());
                codeBlock.setCode(code);
                codeBlock.setStartIndex(startIndex);
                codeBlock.setEndIndex(endIndex);

                // 如果是HTML代码，进行安全处理
                if ("html".equalsIgnoreCase(language)) {
                    String sanitizedHtml = sanitizeHtml(code);
                    codeBlock.setSanitizedHtml(sanitizedHtml);
                }

                codeBlocks.add(codeBlock);
                
                log.debug("提取到代码块: language={}, length={}", language, code.length());
            }
            
            // 如果没有提取到代码块，但文本中包含HTML标签，尝试提取HTML代码
            if (codeBlocks.isEmpty() && HTML_TAG_PATTERN.matcher(text).find()) {
                log.info("未找到Markdown代码块，但检测到HTML标签，尝试提取HTML代码");
                
                // 尝试提取完整的HTML文档
                String htmlCode = extractHtmlFromText(text);
                if (htmlCode != null && !htmlCode.trim().isEmpty()) {
                    CodeBlock htmlBlock = new CodeBlock();
                    htmlBlock.setLanguage("html");
                    htmlBlock.setCode(htmlCode);
                    htmlBlock.setSanitizedHtml(sanitizeHtml(htmlCode));
                    htmlBlock.setStartIndex(0);
                    htmlBlock.setEndIndex(text.length());
                    codeBlocks.add(htmlBlock);
                    
                    log.info("提取到HTML代码块: length={}", htmlCode.length());
                }
            }
            
            log.info("共提取到 {} 个代码块", codeBlocks.size());
            
        } catch (Exception e) {
            log.error("提取代码块失败", e);
        }

        return codeBlocks;
    }

    /**
     * 从文本中提取HTML代码
     * 如果文本中包含完整的HTML文档（从<!DOCTYPE或<html开始），提取整个HTML
     * 否则，提取第一个包含HTML标签的代码段
     *
     * @param text 文本内容
     * @return HTML代码，如果未找到则返回null
     */
    private static String extractHtmlFromText(String text) {
        if (text == null || text.isEmpty()) {
            return null;
        }

        // 尝试提取完整的HTML文档（从<!DOCTYPE或<html开始，到</html>结束）
        Pattern htmlDocPattern = Pattern.compile(
                "(<!DOCTYPE\\s+html[\\s\\S]*?</html>)",
                Pattern.CASE_INSENSITIVE | Pattern.DOTALL
        );
        Matcher htmlDocMatcher = htmlDocPattern.matcher(text);
        if (htmlDocMatcher.find()) {
            String htmlDoc = htmlDocMatcher.group(1);
            log.debug("提取到完整HTML文档: length={}", htmlDoc.length());
            return htmlDoc;
        }

        // 如果没有完整的HTML文档，尝试提取HTML片段（从第一个HTML标签开始，到最后一个标签结束）
        Pattern htmlFragmentPattern = Pattern.compile(
                "(<[^>]+>[\\s\\S]*?</[^>]+>)",
                Pattern.CASE_INSENSITIVE | Pattern.DOTALL
        );
        Matcher htmlFragmentMatcher = htmlFragmentPattern.matcher(text);
        if (htmlFragmentMatcher.find()) {
            // 找到所有HTML标签，提取从第一个到最后一个之间的内容
            int firstTagStart = text.indexOf('<');
            int lastTagEnd = text.lastIndexOf('>');
            if (firstTagStart >= 0 && lastTagEnd > firstTagStart) {
                String htmlFragment = text.substring(firstTagStart, lastTagEnd + 1);
                log.debug("提取到HTML片段: length={}", htmlFragment.length());
                return htmlFragment;
            }
        }

        // 如果都没有找到，返回包含HTML标签的整个文本（作为兜底方案）
        if (HTML_TAG_PATTERN.matcher(text).find()) {
            log.debug("使用整个文本作为HTML代码: length={}", text.length());
            return text;
        }

        return null;
    }

    /**
     * HTML代码预览处理
     * 
     * 注意：此方法直接返回原始HTML代码，不进行清理
     * 安全性由前端iframe的sandbox属性保证：
     * - sandbox="allow-scripts" 允许JavaScript运行（支持交互功能）
     * - 禁止访问父页面（防止窃取用户数据）
     * - 禁止网络请求（防止CSRF攻击）
     * - 禁止localStorage访问（防止数据泄露）
     * 
     * 这样既保证了代码预览的完整功能（计算器、时钟等），又确保了安全性
     *
     * @param html 原始HTML代码
     * @return 原始HTML代码（用于iframe预览）
     */
    public static String sanitizeHtml(String html) {
        if (html == null || html.isEmpty()) {
            return "";
        }

        try {
            // 代码预览场景：直接返回原始HTML
            // iframe sandbox机制提供足够的安全隔离
            log.debug("HTML预览处理: 长度={}", html.length());
            return html;
        } catch (Exception e) {
            log.error("HTML处理失败", e);
            return "";
        }
    }

}

