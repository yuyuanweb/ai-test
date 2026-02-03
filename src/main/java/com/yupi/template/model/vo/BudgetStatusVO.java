package com.yupi.template.model.vo;

import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;

/**
 * 预算状态
 *
 * @author <a href="https://codefather.cn">编程导航学习圈</a>
 */
@Data
public class BudgetStatusVO implements Serializable {

    /**
     * 是否可以继续使用
     */
    private Boolean canProceed;

    /**
     * 预算状态：normal-正常，warning-预警，exceeded-超出
     */
    private String status;

    /**
     * 提示消息
     */
    private String message;

    /**
     * 今日已消耗
     */
    private BigDecimal todayCost;

    /**
     * 本月已消耗
     */
    private BigDecimal monthCost;

    /**
     * 日预算限额
     */
    private BigDecimal dailyBudget;

    /**
     * 月预算限额
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

    private static final long serialVersionUID = 1L;

    /**
     * 创建正常状态
     */
    public static BudgetStatusVO normal(BigDecimal todayCost, BigDecimal monthCost,
                                        BigDecimal dailyBudget, BigDecimal monthlyBudget) {
        BudgetStatusVO vo = new BudgetStatusVO();
        vo.setCanProceed(true);
        vo.setStatus("normal");
        vo.setMessage("预算充足");
        vo.setTodayCost(todayCost);
        vo.setMonthCost(monthCost);
        vo.setDailyBudget(dailyBudget);
        vo.setMonthlyBudget(monthlyBudget);
        return vo;
    }

    /**
     * 创建预警状态
     */
    public static BudgetStatusVO warning(String message, BigDecimal todayCost, BigDecimal monthCost,
                                         BigDecimal dailyBudget, BigDecimal monthlyBudget) {
        BudgetStatusVO vo = new BudgetStatusVO();
        vo.setCanProceed(true);
        vo.setStatus("warning");
        vo.setMessage(message);
        vo.setTodayCost(todayCost);
        vo.setMonthCost(monthCost);
        vo.setDailyBudget(dailyBudget);
        vo.setMonthlyBudget(monthlyBudget);
        return vo;
    }

    /**
     * 创建超出状态
     */
    public static BudgetStatusVO exceeded(String message, BigDecimal todayCost, BigDecimal monthCost,
                                          BigDecimal dailyBudget, BigDecimal monthlyBudget) {
        BudgetStatusVO vo = new BudgetStatusVO();
        vo.setCanProceed(false);
        vo.setStatus("exceeded");
        vo.setMessage(message);
        vo.setTodayCost(todayCost);
        vo.setMonthCost(monthCost);
        vo.setDailyBudget(dailyBudget);
        vo.setMonthlyBudget(monthlyBudget);
        return vo;
    }
}
