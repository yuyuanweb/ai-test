package com.yupi.template.model.vo;

import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;
import java.util.List;

/**
 * 成本统计数据
 *
 * @author <a href="https://codefather.cn">编程导航学习圈</a>
 */
@Data
public class CostStatisticsVO implements Serializable {

    /**
     * 总成本（USD）
     */
    private BigDecimal totalCost;

    /**
     * 今日成本（USD）
     */
    private BigDecimal todayCost;

    /**
     * 本周成本（USD）
     */
    private BigDecimal weekCost;

    /**
     * 本月成本（USD）
     */
    private BigDecimal monthCost;

    /**
     * 按模型分类的成本
     */
    private List<ModelCostVO> costByModel;

    /**
     * 成本趋势（按天）
     */
    private List<DailyCostVO> costTrend;

    private static final long serialVersionUID = 1L;

    /**
     * 模型成本明细
     */
    @Data
    public static class ModelCostVO implements Serializable {
        /**
         * 模型名称
         */
        private String modelName;

        /**
         * 成本（USD）
         */
        private BigDecimal cost;

        /**
         * 占比（百分比）
         */
        private BigDecimal percentage;

        private static final long serialVersionUID = 1L;
    }

    /**
     * 每日成本
     */
    @Data
    public static class DailyCostVO implements Serializable {
        /**
         * 日期（yyyy-MM-dd）
         */
        private String date;

        /**
         * 成本（USD）
         */
        private BigDecimal cost;

        private static final long serialVersionUID = 1L;
    }
}
