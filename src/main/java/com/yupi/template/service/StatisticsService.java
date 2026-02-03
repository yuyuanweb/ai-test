package com.yupi.template.service;

import com.yupi.template.model.vo.CostStatisticsVO;
import com.yupi.template.model.vo.PerformanceStatisticsVO;
import com.yupi.template.model.vo.RealtimeCostVO;
import com.yupi.template.model.vo.UsageStatisticsVO;

/**
 * 统计服务接口
 *
 * @author <a href="https://codefather.cn">编程导航学习圈</a>
 */
public interface StatisticsService {

    /**
     * 获取成本统计数据
     *
     * @param userId 用户ID
     * @param days   统计天数（用于趋势图，默认30天）
     * @return 成本统计数据
     */
    CostStatisticsVO getCostStatistics(Long userId, Integer days);

    /**
     * 获取使用统计数据
     *
     * @param userId 用户ID
     * @param days   统计天数（用于趋势图，默认30天）
     * @return 使用统计数据
     */
    UsageStatisticsVO getUsageStatistics(Long userId, Integer days);

    /**
     * 获取性能统计数据
     *
     * @param userId 用户ID
     * @return 性能统计数据
     */
    PerformanceStatisticsVO getPerformanceStatistics(Long userId);

    /**
     * 刷新用户模型使用统计数据（从历史会话消息和测试结果中聚合）
     *
     * @return 刷新的记录数
     */
    int refreshUserModelUsageData();

    /**
     * 获取实时成本监控数据
     *
     * @param userId 用户ID
     * @return 实时成本数据
     */
    RealtimeCostVO getRealtimeCost(Long userId);
}
