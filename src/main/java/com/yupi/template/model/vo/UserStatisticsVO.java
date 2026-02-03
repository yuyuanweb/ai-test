package com.yupi.template.model.vo;

import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;

/**
 * 用户统计数据
 *
 * @author <a href="https://codefather.cn">编程导航学习圈</a>
 */
@Data
public class UserStatisticsVO implements Serializable {

    /**
     * 模型总数
     */
    private Long totalModels;

    /**
     * 总Tokens使用量
     */
    private Long totalTokens;

    /**
     * 总花费 (USD)
     */
    private BigDecimal totalCost;

    /**
     * 今日花费 (USD)
     */
    private BigDecimal todayCost;

    /**
     * 本月花费 (USD)
     */
    private BigDecimal monthCost;

    /**
     * 日预算限额(USD)
     */
    private BigDecimal dailyBudget;

    /**
     * 月预算限额(USD)
     */
    private BigDecimal monthlyBudget;

    /**
     * 预算预警阈值(百分比)
     */
    private Integer budgetAlertThreshold;

    /**
     * 今日预算使用百分比
     */
    private BigDecimal dailyBudgetUsagePercent;

    /**
     * 本月预算使用百分比
     */
    private BigDecimal monthlyBudgetUsagePercent;

    /**
     * 是否触发日预算预警
     */
    private Boolean dailyBudgetAlert;

    /**
     * 是否触发月预算预警
     */
    private Boolean monthlyBudgetAlert;

    private static final long serialVersionUID = 1L;
}
