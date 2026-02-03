package com.yupi.template.model.vo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serial;
import java.io.Serializable;
import java.math.BigDecimal;

/**
 * 实时成本统计VO
 *
 * @author <a href="https://codefather.cn">编程导航学习圈</a>
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RealtimeCostVO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 今日已消耗(USD)
     */
    private BigDecimal todayCost;

    /**
     * 本月已消耗(USD)
     */
    private BigDecimal monthCost;

    /**
     * 今日Token消耗
     */
    private Long todayTokens;

    /**
     * 今日API调用次数
     */
    private Long todayApiCalls;

    /**
     * 平均每次调用成本(USD)
     */
    private BigDecimal avgCostPerCall;

    /**
     * 日预算限额(USD)
     */
    private BigDecimal dailyBudget;

    /**
     * 月预算限额(USD)
     */
    private BigDecimal monthlyBudget;

    /**
     * 日预算使用百分比
     */
    private BigDecimal dailyUsagePercent;

    /**
     * 月预算使用百分比
     */
    private BigDecimal monthlyUsagePercent;

    /**
     * 预算状态：normal-正常，warning-预警，exceeded-超出
     */
    private String budgetStatus;

    /**
     * 预算提示消息
     */
    private String budgetMessage;

    /**
     * 预警阈值(百分比)
     */
    private Integer alertThreshold;
}
