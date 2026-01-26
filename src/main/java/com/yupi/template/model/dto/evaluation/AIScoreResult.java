package com.yupi.template.model.dto.evaluation;

import java.io.Serializable;
import java.util.List;

/**
 * AI评分结果（存储格式）
 *
 * @author <a href="https://codefather.cn">编程导航学习圈</a>
 */
public record AIScoreResult(
        List<JudgeScore> judges,
        Double averageRating,
        Double consistency
) implements Serializable {
}
