package com.yupi.template.model.dto.prompt;

import lombok.Data;

import java.io.Serializable;

/**
 * 提示词优化请求
 *
 * @author <a href="https://codefather.cn">编程导航学习圈</a>
 */
@Data
public class PromptOptimizationRequest implements Serializable {

    /**
     * 原始提示词
     */
    private String originalPrompt;

    /**
     * AI回答（可选，用于更精准的分析）
     */
    private String aiResponse;

    /**
     * 评估模型（可选，默认使用GPT-4o或Claude 3.5）
     */
    private String evaluationModel;

    private static final long serialVersionUID = 1L;
}
