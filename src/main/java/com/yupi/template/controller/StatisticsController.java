package com.yupi.template.controller;

import com.yupi.template.common.BaseResponse;
import com.yupi.template.common.ResultUtils;
import com.yupi.template.model.entity.User;
import com.yupi.template.model.vo.CostStatisticsVO;
import com.yupi.template.model.vo.PerformanceStatisticsVO;
import com.yupi.template.model.vo.RealtimeCostVO;
import com.yupi.template.model.vo.UsageStatisticsVO;
import com.yupi.template.service.BudgetService;
import com.yupi.template.service.StatisticsService;
import com.yupi.template.service.UserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Resource;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 数据统计控制器
 *
 * @author <a href="https://codefather.cn">编程导航学习圈</a>
 */
@Slf4j
@RestController
@RequestMapping("/statistics")
@Tag(name = "数据统计接口")
public class StatisticsController {

    @Resource
    private StatisticsService statisticsService;

    @Resource
    private UserService userService;

    @Resource
    private BudgetService budgetService;

    /**
     * 获取成本统计数据
     *
     * @param days    统计天数（用于趋势图，默认30天）
     * @param request HTTP请求
     * @return 成本统计数据
     */
    @GetMapping("/cost")
    @Operation(summary = "获取成本统计")
    public BaseResponse<CostStatisticsVO> getCostStatistics(
            @RequestParam(required = false, defaultValue = "30") Integer days,
            HttpServletRequest request) {
        User loginUser = userService.getLoginUser(request);
        CostStatisticsVO statistics = statisticsService.getCostStatistics(loginUser.getId(), days);
        return ResultUtils.success(statistics);
    }

    /**
     * 获取使用统计数据
     *
     * @param days    统计天数（用于趋势图，默认30天）
     * @param request HTTP请求
     * @return 使用统计数据
     */
    @GetMapping("/usage")
    @Operation(summary = "获取使用统计")
    public BaseResponse<UsageStatisticsVO> getUsageStatistics(
            @RequestParam(required = false, defaultValue = "30") Integer days,
            HttpServletRequest request) {
        User loginUser = userService.getLoginUser(request);
        UsageStatisticsVO statistics = statisticsService.getUsageStatistics(loginUser.getId(), days);
        return ResultUtils.success(statistics);
    }

    /**
     * 获取性能统计数据
     *
     * @param request HTTP请求
     * @return 性能统计数据
     */
    @GetMapping("/performance")
    @Operation(summary = "获取性能统计")
    public BaseResponse<PerformanceStatisticsVO> getPerformanceStatistics(HttpServletRequest request) {
        User loginUser = userService.getLoginUser(request);
        PerformanceStatisticsVO statistics = statisticsService.getPerformanceStatistics(loginUser.getId());
        return ResultUtils.success(statistics);
    }

    /**
     * 获取实时成本监控数据
     *
     * @param request HTTP请求
     * @return 实时成本数据
     */
    @GetMapping("/realtime")
    @Operation(summary = "获取实时成本监控")
    public BaseResponse<RealtimeCostVO> getRealtimeCost(HttpServletRequest request) {
        User loginUser = userService.getLoginUser(request);
        RealtimeCostVO realtimeCost = statisticsService.getRealtimeCost(loginUser.getId());
        return ResultUtils.success(realtimeCost);
    }
}
