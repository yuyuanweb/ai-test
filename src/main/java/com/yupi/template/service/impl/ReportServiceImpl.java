package com.yupi.template.service.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.collection.CollUtil;
import cn.hutool.json.JSONUtil;
import com.mybatisflex.core.query.QueryWrapper;
import com.yupi.template.exception.BusinessException;
import com.yupi.template.exception.ErrorCode;
import com.yupi.template.mapper.TestResultMapper;
import com.yupi.template.mapper.TestTaskMapper;
import com.yupi.template.model.entity.TestResult;
import com.yupi.template.model.entity.TestTask;
import com.yupi.template.model.vo.*;
import com.yupi.template.service.ReportService;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 报告服务实现
 *
 * @author <a href="https://codefather.cn">编程导航学习圈</a>
 */
@Service
@Slf4j
public class ReportServiceImpl implements ReportService {

    @Resource
    private TestTaskMapper testTaskMapper;

    @Resource
    private TestResultMapper testResultMapper;

    @Override
    public ReportVO generateReport(String taskId, Long userId) {
        TestTask task = testTaskMapper.selectOneById(taskId);
        if (task == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND_ERROR, "任务不存在");
        }

        if (!task.getUserId().equals(userId)) {
            throw new BusinessException(ErrorCode.NO_AUTH_ERROR, "无权限查看该任务报告");
        }

        QueryWrapper queryWrapper = QueryWrapper.create()
                .eq("taskId", taskId)
                .orderBy("createTime", true);

        List<TestResult> testResults = testResultMapper.selectListByQuery(queryWrapper);
        if (CollUtil.isEmpty(testResults)) {
            throw new BusinessException(ErrorCode.NOT_FOUND_ERROR, "该任务暂无测试结果");
        }

        ReportVO report = new ReportVO();
        report.setTaskId(taskId);
        report.setTaskName(task.getName());

        ReportSummaryVO summary = calculateSummary(testResults);
        report.setSummary(summary);

        List<ModelStatisticsVO> modelStatistics = calculateModelStatistics(testResults);
        report.setModelStatistics(modelStatistics);

        RadarChartDataVO radarChart = generateRadarChartData(testResults, modelStatistics);
        report.setRadarChart(radarChart);

        BarChartDataVO barChart = generateBarChartData(modelStatistics);
        report.setBarChart(barChart);

        List<TestResultVO> testResultVOList = testResults.stream()
                .map(this::convertToTestResultVO)
                .collect(Collectors.toList());
        report.setTestResults(testResultVOList);

        return report;
    }

    /**
     * 计算报告摘要
     */
    private ReportSummaryVO calculateSummary(List<TestResult> testResults) {
        ReportSummaryVO summary = new ReportSummaryVO();

        BigDecimal totalCost = testResults.stream()
                .filter(result -> result.getCost() != null)
                .map(TestResult::getCost)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        summary.setTotalCost(totalCost);

        OptionalDouble avgResponseTime = testResults.stream()
                .filter(result -> result.getResponseTimeMs() != null)
                .mapToInt(TestResult::getResponseTimeMs)
                .average();
        summary.setAvgResponseTimeMs(avgResponseTime.isPresent() ? avgResponseTime.getAsDouble() : null);

        Long totalTokens = testResults.stream()
                .filter(result -> result.getInputTokens() != null && result.getOutputTokens() != null)
                .mapToLong(result -> (long) result.getInputTokens() + result.getOutputTokens())
                .sum();
        summary.setTotalTokens(totalTokens);

        summary.setTotalResults(testResults.size());

        Set<String> modelNames = testResults.stream()
                .map(TestResult::getModelName)
                .collect(Collectors.toSet());
        summary.setModelCount(modelNames.size());

        return summary;
    }

    /**
     * 计算各模型统计信息
     */
    private List<ModelStatisticsVO> calculateModelStatistics(List<TestResult> testResults) {
        Map<String, List<TestResult>> groupedByModel = testResults.stream()
                .collect(Collectors.groupingBy(TestResult::getModelName));

        List<ModelStatisticsVO> statisticsList = new ArrayList<>();

        for (Map.Entry<String, List<TestResult>> entry : groupedByModel.entrySet()) {
            String modelName = entry.getKey();
            List<TestResult> modelResults = entry.getValue();

            ModelStatisticsVO statistics = new ModelStatisticsVO();
            statistics.setModelName(modelName);
            statistics.setTestCount(modelResults.size());

            OptionalDouble avgResponseTime = modelResults.stream()
                    .filter(result -> result.getResponseTimeMs() != null)
                    .mapToInt(TestResult::getResponseTimeMs)
                    .average();
            statistics.setAvgResponseTimeMs(avgResponseTime.isPresent() ? avgResponseTime.getAsDouble() : null);

            OptionalDouble avgInputTokens = modelResults.stream()
                    .filter(result -> result.getInputTokens() != null)
                    .mapToInt(TestResult::getInputTokens)
                    .average();
            statistics.setAvgInputTokens(avgInputTokens.isPresent() ? avgInputTokens.getAsDouble() : null);

            OptionalDouble avgOutputTokens = modelResults.stream()
                    .filter(result -> result.getOutputTokens() != null)
                    .mapToInt(TestResult::getOutputTokens)
                    .average();
            statistics.setAvgOutputTokens(avgOutputTokens.isPresent() ? avgOutputTokens.getAsDouble() : null);

            Long totalTokens = modelResults.stream()
                    .filter(result -> result.getInputTokens() != null && result.getOutputTokens() != null)
                    .mapToLong(result -> (long) result.getInputTokens() + result.getOutputTokens())
                    .sum();
            statistics.setTotalTokens(totalTokens);

            BigDecimal totalCost = modelResults.stream()
                    .filter(result -> result.getCost() != null)
                    .map(TestResult::getCost)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);
            statistics.setTotalCost(totalCost);

            if (statistics.getTestCount() > 0 && totalCost != null) {
                BigDecimal avgCost = totalCost.divide(
                        BigDecimal.valueOf(statistics.getTestCount()),
                        6,
                        RoundingMode.HALF_UP
                );
                statistics.setAvgCost(avgCost);
            }

            OptionalDouble avgUserRating = modelResults.stream()
                    .filter(result -> result.getUserRating() != null)
                    .mapToInt(TestResult::getUserRating)
                    .average();
            statistics.setAvgUserRating(avgUserRating.isPresent() ? avgUserRating.getAsDouble() : null);

            OptionalDouble avgAiScore = modelResults.stream()
                    .filter(result -> result.getAiScore() != null && !result.getAiScore().trim().isEmpty())
                    .mapToDouble(result -> {
                        try {
                            Map<String, Object> aiScoreMap = JSONUtil.toBean(result.getAiScore(), Map.class);
                            Object totalObj = aiScoreMap.get("total");
                            if (totalObj instanceof Number) {
                                return ((Number) totalObj).doubleValue();
                            }
                        } catch (Exception e) {
                            log.warn("解析AI评分失败: {}", result.getAiScore(), e);
                        }
                        return 0.0;
                    })
                    .filter(score -> score > 0)
                    .average();
            statistics.setAvgAiScore(avgAiScore.isPresent() ? avgAiScore.getAsDouble() : null);

            statisticsList.add(statistics);
        }

        return statisticsList;
    }

    /**
     * 生成雷达图数据
     */
    private RadarChartDataVO generateRadarChartData(List<TestResult> testResults, List<ModelStatisticsVO> modelStatistics) {
        RadarChartDataVO radarChart = new RadarChartDataVO();

        List<String> dimensions = Arrays.asList("准确性", "完整性", "速度", "成本效率", "用户满意度");
        radarChart.setDimensions(dimensions);

        List<RadarSeriesVO> seriesList = new ArrayList<>();

        for (ModelStatisticsVO statistics : modelStatistics) {
            RadarSeriesVO series = new RadarSeriesVO();
            series.setModelName(statistics.getModelName());

            List<Double> values = new ArrayList<>();

            double accuracy = statistics.getAvgAiScore() != null ? statistics.getAvgAiScore() : 0.0;
            values.add(normalizeScore(accuracy, 0.0, 100.0));

            double completeness = calculateCompleteness(testResults, statistics.getModelName());
            values.add(normalizeScore(completeness, 0.0, 100.0));

            double speed = statistics.getAvgResponseTimeMs() != null
                    ? normalizeSpeed(statistics.getAvgResponseTimeMs())
                    : 0.0;
            values.add(speed);

            double costEfficiency = statistics.getAvgCost() != null
                    ? normalizeCostEfficiency(statistics.getAvgCost())
                    : 0.0;
            values.add(costEfficiency);

            double userSatisfaction = statistics.getAvgUserRating() != null
                    ? normalizeScore(statistics.getAvgUserRating(), 1.0, 5.0)
                    : 0.0;
            values.add(userSatisfaction);

            series.setValues(values);
            seriesList.add(series);
        }

        radarChart.setSeries(seriesList);
        return radarChart;
    }

    /**
     * 计算完整性（基于输出文本长度和Token数）
     */
    private double calculateCompleteness(List<TestResult> testResults, String modelName) {
        List<TestResult> modelResults = testResults.stream()
                .filter(result -> modelName.equals(result.getModelName()))
                .collect(Collectors.toList());

        if (CollUtil.isEmpty(modelResults)) {
            return 0.0;
        }

        double avgOutputLength = modelResults.stream()
                .filter(result -> result.getOutputText() != null)
                .mapToInt(result -> result.getOutputText().length())
                .average()
                .orElse(0.0);

        double avgOutputTokens = modelResults.stream()
                .filter(result -> result.getOutputTokens() != null)
                .mapToInt(TestResult::getOutputTokens)
                .average()
                .orElse(0.0);

        double completeness = (avgOutputLength / 1000.0 + avgOutputTokens / 100.0) / 2.0;
        return Math.min(completeness, 100.0);
    }

    /**
     * 标准化分数到0-100范围
     */
    private double normalizeScore(double value, double min, double max) {
        if (max == min) {
            return 0.0;
        }
        double normalized = ((value - min) / (max - min)) * 100.0;
        return Math.max(0.0, Math.min(100.0, normalized));
    }

    /**
     * 标准化速度（响应时间越短，分数越高）
     */
    private double normalizeSpeed(double responseTimeMs) {
        if (responseTimeMs <= 0) {
            return 0.0;
        }
        double normalized = 10000.0 / responseTimeMs;
        return Math.min(100.0, normalized);
    }

    /**
     * 标准化成本效率（成本越低，分数越高）
     */
    private double normalizeCostEfficiency(BigDecimal avgCost) {
        if (avgCost == null || avgCost.compareTo(BigDecimal.ZERO) <= 0) {
            return 0.0;
        }
        double costValue = avgCost.doubleValue();
        double normalized = 1.0 / (costValue * 100.0 + 0.01);
        return Math.min(100.0, normalized * 100.0);
    }

    /**
     * 生成柱状图数据
     */
    private BarChartDataVO generateBarChartData(List<ModelStatisticsVO> modelStatistics) {
        BarChartDataVO barChart = new BarChartDataVO();

        List<String> categories = modelStatistics.stream()
                .map(ModelStatisticsVO::getModelName)
                .collect(Collectors.toList());
        barChart.setCategories(categories);

        List<BarSeriesVO> seriesList = new ArrayList<>();

        BarSeriesVO responseTimeSeries = new BarSeriesVO();
        responseTimeSeries.setName("平均响应时间");
        responseTimeSeries.setUnit("ms");
        List<Double> responseTimeData = modelStatistics.stream()
                .map(statistics -> statistics.getAvgResponseTimeMs() != null
                        ? statistics.getAvgResponseTimeMs()
                        : 0.0)
                .collect(Collectors.toList());
        responseTimeSeries.setData(responseTimeData);
        seriesList.add(responseTimeSeries);

        BarSeriesVO totalTokensSeries = new BarSeriesVO();
        totalTokensSeries.setName("总Token消耗");
        totalTokensSeries.setUnit("tokens");
        List<Double> totalTokensData = modelStatistics.stream()
                .map(statistics -> statistics.getTotalTokens() != null
                        ? statistics.getTotalTokens().doubleValue()
                        : 0.0)
                .collect(Collectors.toList());
        totalTokensSeries.setData(totalTokensData);
        seriesList.add(totalTokensSeries);

        BarSeriesVO totalCostSeries = new BarSeriesVO();
        totalCostSeries.setName("总成本");
        totalCostSeries.setUnit("USD");
        List<Double> totalCostData = modelStatistics.stream()
                .map(statistics -> statistics.getTotalCost() != null
                        ? statistics.getTotalCost().doubleValue()
                        : 0.0)
                .collect(Collectors.toList());
        totalCostSeries.setData(totalCostData);
        seriesList.add(totalCostSeries);

        barChart.setSeries(seriesList);
        return barChart;
    }

    /**
     * 转换为TestResultVO
     */
    private TestResultVO convertToTestResultVO(TestResult testResult) {
        TestResultVO vo = new TestResultVO();
        BeanUtil.copyProperties(testResult, vo);
        return vo;
    }
}
