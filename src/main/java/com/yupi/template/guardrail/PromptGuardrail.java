package com.yupi.template.guardrail;

import com.yupi.template.exception.BusinessException;
import com.yupi.template.exception.ErrorCode;

import java.util.List;
import java.util.regex.Pattern;

/**
 * Prompt 安全审查护轨：长度、敏感词、注入模式检测
 *
 * @author <a href="https://codefather.cn">编程导航学习圈</a>
 */
public final class PromptGuardrail {

    private static final int MAX_PROMPT_LENGTH = 4000;

    private static final List<String> SENSITIVE_WORDS = List.of(
            "忽略之前的指令", "ignore previous instructions", "ignore above", "ignore all",
            "破解", "hack", "绕过", "bypass", "越狱", "jailbreak",
            "无视", "disregard", "forget everything"
    );

    private static final List<Pattern> INJECTION_PATTERNS = List.of(
            Pattern.compile("(?i)ignore\\s+(?:previous|above|all)\\s+(?:instructions?|commands?|prompts?)"),
            Pattern.compile("(?i)(?:forget|disregard)\\s+(?:everything|all)\\s+(?:above|before)"),
            Pattern.compile("(?i)(?:pretend|act|behave)\\s+(?:as|like)\\s+(?:if|you\\s+are)"),
            Pattern.compile("(?i)system\\s*:\\s*you\\s+are"),
            Pattern.compile("(?i)new\\s+(?:instructions?|commands?|prompts?)\\s*:")
    );

    private PromptGuardrail() {
    }

    /**
     * 校验 prompt，不通过则抛出 BusinessException
     */
    public static void validate(String prompt) {
        if (prompt == null) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "输入内容不能为空");
        }
        String t = prompt.trim();
        if (t.isEmpty()) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "输入内容不能为空");
        }
        if (t.length() > MAX_PROMPT_LENGTH) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR,
                    "输入内容过长，请勿超过 " + MAX_PROMPT_LENGTH + " 字");
        }
        String lower = t.toLowerCase();
        for (String w : SENSITIVE_WORDS) {
            if (lower.contains(w.toLowerCase())) {
                throw new BusinessException(ErrorCode.PARAMS_ERROR, "输入包含不当内容，请修改后重试");
            }
        }
        for (Pattern p : INJECTION_PATTERNS) {
            if (p.matcher(t).find()) {
                throw new BusinessException(ErrorCode.PARAMS_ERROR, "检测到恶意输入，请求被拒绝");
            }
        }
    }
}
