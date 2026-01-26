package com.yupi.template.model.dto.evaluation;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.io.Serializable;
import java.util.Map;

/**
 * AI评分结果
 *
 * @author <a href="https://codefather.cn">编程导航学习圈</a>
 */
public record EvaluationResult(
        Map<String, Integer> scores,
        @JsonProperty("total_score")
        Integer totalScore,
        Integer rating,
        String comment
) implements Serializable {
}
