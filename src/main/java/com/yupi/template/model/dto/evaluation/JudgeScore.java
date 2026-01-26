package com.yupi.template.model.dto.evaluation;

import java.io.Serializable;
import java.util.Map;

/**
 * 评委评分结果
 *
 * @author <a href="https://codefather.cn">编程导航学习圈</a>
 */
public record JudgeScore(
        String model,
        Map<String, Integer> scores,
        Integer totalScore,
        Integer rating,
        String comment
) implements Serializable {
}
