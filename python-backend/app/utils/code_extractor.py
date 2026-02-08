"""
代码提取工具类
@author <a href="https://codefather.cn">编程导航学习圈</a>
"""
import re
from typing import List, Optional
from dataclasses import dataclass
from loguru import logger


@dataclass
class CodeBlock:
    """代码块 DTO"""
    language: str
    code: str
    start_index: int = 0
    end_index: int = 0
    sanitized_html: Optional[str] = None


CODE_BLOCK_PATTERN = re.compile(
    r"```(\w*)\n([\s\S]*?)```",
    re.MULTILINE
)

HTML_TAG_PATTERN = re.compile(
    r"<!DOCTYPE\s+html|<html|<head|<body|<div|<span|<p|<h[1-6]|<script|<style",
    re.IGNORECASE
)

HTML_DOC_PATTERN = re.compile(
    r"(<!DOCTYPE\s+html[\s\S]*?</html>)",
    re.IGNORECASE | re.DOTALL
)

HTML_FRAGMENT_PATTERN = re.compile(
    r"(<[^>]+>[\s\S]*?</[^>]+>)",
    re.IGNORECASE | re.DOTALL
)


def extract_code_blocks(text: str) -> List[dict]:
    """
    从文本中提取所有代码块

    Args:
        text: 包含 Markdown 代码块的文本

    Returns:
        代码块列表，每个元素为 dict，包含 language, code, startIndex, endIndex, sanitizedHtml
    """
    result: List[dict] = []

    if not text or not text.strip():
        return result

    try:
        for match in CODE_BLOCK_PATTERN.finditer(text):
            language = match.group(1) or "text"
            code = match.group(2)
            start_index = match.start()
            end_index = match.end()

            block_dict = {
                "language": language.lower(),
                "code": code,
                "startIndex": start_index,
                "endIndex": end_index,
            }

            if language.lower() == "html":
                sanitized = _sanitize_html(code)
                block_dict["sanitizedHtml"] = sanitized

            result.append(block_dict)
            logger.debug("提取到代码块: language={}, length={}", language, len(code))

        if not result and HTML_TAG_PATTERN.search(text):
            logger.info("未找到Markdown代码块，但检测到HTML标签，尝试提取HTML代码")
            html_code = _extract_html_from_text(text)
            if html_code and html_code.strip():
                sanitized = _sanitize_html(html_code)
                result.append({
                    "language": "html",
                    "code": html_code,
                    "startIndex": 0,
                    "endIndex": len(text),
                    "sanitizedHtml": sanitized
                })
                logger.info("提取到HTML代码块: length={}", len(html_code))

        logger.info("共提取到 {} 个代码块", len(result))

    except Exception as e:
        logger.error("提取代码块失败", exc_info=True)

    return result


def _extract_html_from_text(text: str) -> Optional[str]:
    """
    从文本中提取 HTML 代码
    如果包含完整 HTML 文档则提取整个文档，否则提取包含 HTML 标签的代码段
    """
    if not text or not text.strip():
        return None

    html_doc_match = HTML_DOC_PATTERN.search(text)
    if html_doc_match:
        logger.debug("提取到完整HTML文档: length={}", len(html_doc_match.group(1)))
        return html_doc_match.group(1)

    html_frag_match = HTML_FRAGMENT_PATTERN.search(text)
    if html_frag_match:
        first_tag = text.find("<")
        last_tag = text.rfind(">")
        if first_tag >= 0 and last_tag > first_tag:
            html_fragment = text[first_tag:last_tag + 1]
            logger.debug("提取到HTML片段: length={}", len(html_fragment))
            return html_fragment

    if HTML_TAG_PATTERN.search(text):
        logger.debug("使用整个文本作为HTML代码: length={}", len(text))
        return text

    return None


def _sanitize_html(html: str) -> str:
    """
    HTML 代码预览处理

    注意：此方法直接返回原始 HTML 代码，不进行清理。
    安全性由前端 iframe 的 sandbox 属性保证：
    - sandbox="allow-scripts" 允许 JavaScript 运行（支持交互功能）
    - 禁止访问父页面（防止窃取用户数据）
    - 禁止网络请求（防止 CSRF 攻击）
    - 禁止 localStorage 访问（防止数据泄露）
    """
    if not html or not html.strip():
        return ""

    try:
        logger.debug("HTML预览处理: 长度={}", len(html))
        return html
    except Exception as e:
        logger.error("HTML处理失败", exc_info=True)
        return ""
