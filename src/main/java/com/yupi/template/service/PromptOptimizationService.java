package com.yupi.template.service;

import com.yupi.template.model.vo.PromptOptimizationVO;

/**
 * 提示词优化服务接口
 *
 * @author <a href="https://codefather.cn">编程导航学习圈</a>
 */
public interface PromptOptimizationService {

    /**
     * 分析并优化提示词
     *
     * @param originalPrompt 原始提示词
     * @param aiResponse     AI回答（可选）
     * @param evaluationModel 评估模型（可选，默认使用GPT-4o或Claude 3.5）
     * @return 优化建议
     */
    PromptOptimizationVO optimizePrompt(String originalPrompt, String aiResponse, String evaluationModel);
}
