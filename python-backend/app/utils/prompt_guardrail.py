"""
Prompt 安全审查护轨：长度、敏感词、注入模式检测
@author <a href="https://codefather.cn">编程导航学习圈</a>
"""
import re
from typing import List, Pattern

from app.core.errors import BusinessException, ErrorCode

MAX_PROMPT_LENGTH = 4000

SENSITIVE_WORDS: List[str] = [
    "忽略之前的指令", "ignore previous instructions", "ignore above", "ignore all",
    "破解", "hack", "绕过", "bypass", "越狱", "jailbreak",
    "无视", "disregard", "forget everything"
]

INJECTION_PATTERNS: List[Pattern] = [
    re.compile(r"(?i)ignore\s+(?:previous|above|all)\s+(?:instructions?|commands?|prompts?)"),
    re.compile(r"(?i)(?:forget|disregard)\s+(?:everything|all)\s+(?:above|before)"),
    re.compile(r"(?i)(?:pretend|act|behave)\s+(?:as|like)\s+(?:if|you\s+are)"),
    re.compile(r"(?i)system\s*:\s*you\s+are"),
    re.compile(r"(?i)new\s+(?:instructions?|commands?|prompts?)\s*:")
]


def validate(prompt: str) -> None:
    """
    校验 prompt，不通过则抛出 BusinessException

    Args:
        prompt: 用户输入的提示词

    Raises:
        BusinessException: 校验不通过时
    """
    if prompt is None:
        raise BusinessException(ErrorCode.PARAMS_ERROR, "输入内容不能为空")
    text = prompt.strip()
    if not text:
        raise BusinessException(ErrorCode.PARAMS_ERROR, "输入内容不能为空")
    if len(text) > MAX_PROMPT_LENGTH:
        raise BusinessException(
            ErrorCode.PARAMS_ERROR,
            f"输入内容过长，请勿超过 {MAX_PROMPT_LENGTH} 字"
        )
    lower = text.lower()
    for word in SENSITIVE_WORDS:
        if word.lower() in lower:
            raise BusinessException(ErrorCode.PARAMS_ERROR, "输入包含不当内容，请修改后重试")
    for pattern in INJECTION_PATTERNS:
        if pattern.search(text):
            raise BusinessException(ErrorCode.PARAMS_ERROR, "检测到恶意输入，请求被拒绝")
