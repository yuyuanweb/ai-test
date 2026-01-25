package com.yupi.template.model.vo;

import lombok.Data;

import java.io.Serializable;
import java.util.List;

/**
 * 测试报告VO
 *
 * @author <a href="https://codefather.cn">编程导航学习圈</a>
 */
@Data
public class ReportVO implements Serializable {

    /**
     * 任务ID
     */
    private String taskId;

    /**
     * 任务名称
     */
    private String taskName;

    /**
     * 报告摘要
     */
    private ReportSummaryVO summary;

    /**
     * 各模型统计信息
     */
    private List<ModelStatisticsVO> modelStatistics;

    /**
     * 雷达图数据
     */
    private RadarChartDataVO radarChart;

    /**
     * 柱状图数据
     */
    private BarChartDataVO barChart;

    /**
     * 详细测试结果列表
     */
    private List<TestResultVO> testResults;

    private static final long serialVersionUID = 1L;
}
