package com.yupi.template.model.vo;

import lombok.Data;

import java.io.Serializable;
import java.util.List;

/**
 * 性能统计数据
 *
 * @author <a href="https://codefather.cn">编程导航学习圈</a>
 */
@Data
public class PerformanceStatisticsVO implements Serializable {

    /**
     * 平均响应时间（毫秒）
     */
    private Double avgResponseTime;

    /**
     * 最快响应时间（毫秒）
     */
    private Integer minResponseTime;

    /**
     * 最慢响应时间（毫秒）
     */
    private Integer maxResponseTime;

    /**
     * 各模型性能统计
     */
    private List<ModelPerformanceVO> performanceByModel;

    private static final long serialVersionUID = 1L;

    /**
     * 模型性能明细
     */
    @Data
    public static class ModelPerformanceVO implements Serializable {
        /**
         * 模型名称
         */
        private String modelName;

        /**
         * 调用次数
         */
        private Long callCount;

        /**
         * 平均响应时间（毫秒）
         */
        private Double avgResponseTime;

        /**
         * 最小响应时间（毫秒）
         */
        private Integer minResponseTime;

        /**
         * 最大响应时间（毫秒）
         */
        private Integer maxResponseTime;

        /**
         * 平均输入Token数
         */
        private Double avgInputTokens;

        /**
         * 平均输出Token数
         */
        private Double avgOutputTokens;

        private static final long serialVersionUID = 1L;
    }
}
