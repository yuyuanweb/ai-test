package com.yupi.template.model.vo;

import lombok.Data;

import java.io.Serializable;
import java.util.List;

/**
 * 使用统计数据
 *
 * @author <a href="https://codefather.cn">编程导航学习圈</a>
 */
@Data
public class UsageStatisticsVO implements Serializable {

    /**
     * 总API调用次数
     */
    private Long totalApiCalls;

    /**
     * 今日API调用次数
     */
    private Long todayApiCalls;

    /**
     * 总Token消耗
     */
    private Long totalTokens;

    /**
     * 输入Token总数
     */
    private Long totalInputTokens;

    /**
     * 输出Token总数
     */
    private Long totalOutputTokens;

    /**
     * 今日Token消耗
     */
    private Long todayTokens;

    /**
     * 各模型使用频率
     */
    private List<ModelUsageVO> usageByModel;

    /**
     * API调用趋势（按天）
     */
    private List<DailyUsageVO> usageTrend;

    private static final long serialVersionUID = 1L;

    /**
     * 模型使用明细
     */
    @Data
    public static class ModelUsageVO implements Serializable {
        /**
         * 模型名称
         */
        private String modelName;

        /**
         * 调用次数
         */
        private Long callCount;

        /**
         * Token消耗
         */
        private Long tokens;

        /**
         * 占比（百分比）
         */
        private Double percentage;

        private static final long serialVersionUID = 1L;
    }

    /**
     * 每日使用统计
     */
    @Data
    public static class DailyUsageVO implements Serializable {
        /**
         * 日期（yyyy-MM-dd）
         */
        private String date;

        /**
         * API调用次数
         */
        private Long apiCalls;

        /**
         * Token消耗
         */
        private Long tokens;

        private static final long serialVersionUID = 1L;
    }
}
