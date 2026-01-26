package com.yupi.template.service;

import com.yupi.template.model.dto.evaluation.AIScoreResult;
import com.yupi.template.model.dto.evaluation.EvaluationResult;

/**
 * AI评分服务接口
 *
 * @author <a href="https://codefather.cn">编程导航学习圈</a>
 */
public interface AIScoringService {

    /**
     * 对模型回答进行AI评分（单评委）
     *
     * @param question 问题
     * @param modelResponse 模型回答
     * @return 评分结果
     */
    EvaluationResult score(String question, String modelResponse);

    /**
     * 对模型回答进行多评委交叉验证评分
     *
     * @param question 问题
     * @param modelResponse 模型回答
     * @param testedModelName 被测试的模型名称（用于排除，避免自己评自己）
     * @return 多评委评分结果
     */
    AIScoreResult scoreWithMultipleJudges(String question, String modelResponse, String testedModelName);
}
