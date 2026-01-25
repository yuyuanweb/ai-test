package com.yupi.template.model.vo;

import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;

/**
 * 模型统计信息VO
 *
 * @author <a href="https://codefather.cn">编程导航学习圈</a>
 */
@Data
public class ModelStatisticsVO implements Serializable {

    /**
     * 模型名称
     */
    private String modelName;

    /**
     * 测试次数
     */
    private Integer testCount;

    /**
     * 平均响应时间(毫秒)
     */
    private Double avgResponseTimeMs;

    /**
     * 平均输入Token数
     */
    private Double avgInputTokens;

    /**
     * 平均输出Token数
     */
    private Double avgOutputTokens;

    /**
     * 总Token数
     */
    private Long totalTokens;

    /**
     * 总成本(USD)
     */
    private BigDecimal totalCost;

    /**
     * 平均成本(USD)
     */
    private BigDecimal avgCost;

    /**
     * 平均用户评分(1-5)
     */
    private Double avgUserRating;

    /**
     * 平均AI评分
     */
    private Double avgAiScore;

    private static final long serialVersionUID = 1L;
}
