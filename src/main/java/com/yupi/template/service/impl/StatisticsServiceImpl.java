package com.yupi.template.service.impl;

import cn.hutool.core.util.IdUtil;
import cn.hutool.json.JSONUtil;
import com.mybatisflex.core.query.QueryWrapper;
import com.yupi.template.constant.CacheConstant;
import com.yupi.template.mapper.ConversationMessageMapper;
import com.yupi.template.mapper.TestResultMapper;
import com.yupi.template.mapper.UserModelUsageMapper;
import com.yupi.template.model.entity.ConversationMessage;
import com.yupi.template.model.entity.TestResult;
import com.yupi.template.model.entity.UserModelUsage;
import com.yupi.template.model.entity.User;
import com.yupi.template.model.vo.BudgetStatusVO;
import com.yupi.template.model.vo.CostStatisticsVO;
import com.yupi.template.model.vo.PerformanceStatisticsVO;
import com.yupi.template.model.vo.RealtimeCostVO;
import com.yupi.template.model.vo.UsageStatisticsVO;
import com.yupi.template.service.BudgetService;
import com.yupi.template.service.StatisticsService;
import com.yupi.template.service.UserService;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 统计服务实现
 *
 * @author <a href="https://codefather.cn">编程导航学习圈</a>
 */
@Slf4j
@Service
public class StatisticsServiceImpl implements StatisticsService {

    @Resource
    private ConversationMessageMapper conversationMessageMapper;

    @Resource
    private TestResultMapper testResultMapper;

    @Resource
    private UserModelUsageMapper userModelUsageMapper;

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    @Resource
    private BudgetService budgetService;

    @Resource
    private UserService userService;

    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd");

    @Override
    public CostStatisticsVO getCostStatistics(Long userId, Integer days) {
        if (days == null || days <= 0) {
            days = 30;
        }

        // 尝试从缓存获取
        String cacheKey = CacheConstant.STATISTICS_COST_KEY_PREFIX + userId + ":" + days;
        try {
            String cachedValue = stringRedisTemplate.opsForValue().get(cacheKey);
            if (cachedValue != null) {
                log.debug("从缓存获取成本统计数据: userId={}, days={}", userId, days);
                return JSONUtil.toBean(cachedValue, CostStatisticsVO.class);
            }
        } catch (Exception e) {
            log.warn("读取成本统计缓存失败: {}", e.getMessage());
        }

        CostStatisticsVO result = new CostStatisticsVO();

        // 从 user_model_usage 表获取按模型分类的成本（高效查询）
        QueryWrapper usageWrapper = QueryWrapper.create()
                .eq("userId", userId)
                .eq("isDelete", 0);
        List<UserModelUsage> usageList = userModelUsageMapper.selectListByQuery(usageWrapper);

        // 计算总成本（从 user_model_usage 表）
        BigDecimal totalCost = usageList.stream()
                .map(UserModelUsage::getTotalCost)
                .filter(cost -> cost != null)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        result.setTotalCost(totalCost);

        // 从 Redis 获取今日、本月成本（包含所有调用：会话、图像、批量测试、AI评分、提示词优化）
        result.setTodayCost(budgetService.getTodayCost(userId));
        result.setMonthCost(budgetService.getMonthCost(userId));
        // 本周成本仍从数据库查询（作为参考值）
        result.setWeekCost(conversationMessageMapper.selectWeekCostByUserId(userId));

        // 按模型分类的成本（从 user_model_usage 表）
        List<CostStatisticsVO.ModelCostVO> modelCosts = new ArrayList<>();
        for (UserModelUsage usage : usageList) {
            if (usage.getModelName() != null && usage.getTotalCost() != null) {
                CostStatisticsVO.ModelCostVO modelCost = new CostStatisticsVO.ModelCostVO();
                modelCost.setModelName(usage.getModelName());
                modelCost.setCost(usage.getTotalCost());
                if (totalCost.compareTo(BigDecimal.ZERO) > 0) {
                    modelCost.setPercentage(usage.getTotalCost()
                            .multiply(new BigDecimal("100"))
                            .divide(totalCost, 2, RoundingMode.HALF_UP));
                } else {
                    modelCost.setPercentage(BigDecimal.ZERO);
                }
                modelCosts.add(modelCost);
            }
        }
        modelCosts.sort((a, b) -> b.getCost().compareTo(a.getCost()));
        result.setCostByModel(modelCosts);

        // 成本趋势（按天，需要从 conversation_message 表查询）
        LocalDate endDate = LocalDate.now();
        LocalDate startDate = endDate.minusDays(days - 1);

        QueryWrapper messageWrapper = QueryWrapper.create()
                .eq("userId", userId)
                .eq("isDelete", 0)
                .ge("createTime", startDate.atStartOfDay())
                .le("createTime", endDate.plusDays(1).atStartOfDay());
        List<ConversationMessage> recentMessages = conversationMessageMapper.selectListByQuery(messageWrapper);

        Map<String, BigDecimal> dailyCosts = new HashMap<>();
        for (ConversationMessage msg : recentMessages) {
            if (msg.getCreateTime() != null && msg.getCost() != null) {
                String dateStr = msg.getCreateTime().toLocalDate().format(DATE_FORMATTER);
                dailyCosts.merge(dateStr, msg.getCost(), BigDecimal::add);
            }
        }

        List<CostStatisticsVO.DailyCostVO> costTrend = new ArrayList<>();
        for (LocalDate date = startDate; !date.isAfter(endDate); date = date.plusDays(1)) {
            CostStatisticsVO.DailyCostVO dailyCost = new CostStatisticsVO.DailyCostVO();
            String dateStr = date.format(DATE_FORMATTER);
            dailyCost.setDate(dateStr);
            dailyCost.setCost(dailyCosts.getOrDefault(dateStr, BigDecimal.ZERO));
            costTrend.add(dailyCost);
        }
        result.setCostTrend(costTrend);

        // 写入缓存
        try {
            stringRedisTemplate.opsForValue().set(cacheKey, JSONUtil.toJsonStr(result),
                    Duration.ofMinutes(CacheConstant.STATISTICS_TTL_MINUTES));
            log.debug("成本统计数据已缓存: userId={}, days={}", userId, days);
        } catch (Exception e) {
            log.warn("写入成本统计缓存失败: {}", e.getMessage());
        }

        return result;
    }

    @Override
    public UsageStatisticsVO getUsageStatistics(Long userId, Integer days) {
        if (days == null || days <= 0) {
            days = 30;
        }

        // 尝试从缓存获取
        String cacheKey = CacheConstant.STATISTICS_USAGE_KEY_PREFIX + userId + ":" + days;
        try {
            String cachedValue = stringRedisTemplate.opsForValue().get(cacheKey);
            if (cachedValue != null) {
                log.debug("从缓存获取使用量统计数据: userId={}, days={}", userId, days);
                return JSONUtil.toBean(cachedValue, UsageStatisticsVO.class);
            }
        } catch (Exception e) {
            log.warn("读取使用量统计缓存失败: {}", e.getMessage());
        }

        UsageStatisticsVO result = new UsageStatisticsVO();

        // 从 user_model_usage 表获取总 Token 消耗（高效查询）
        QueryWrapper usageWrapper = QueryWrapper.create()
                .eq("userId", userId)
                .eq("isDelete", 0);
        List<UserModelUsage> usageList = userModelUsageMapper.selectListByQuery(usageWrapper);

        // 计算总 Token（从 user_model_usage 表）
        long totalTokens = usageList.stream()
                .mapToLong(u -> u.getTotalTokens() != null ? u.getTotalTokens() : 0L)
                .sum();
        result.setTotalTokens(totalTokens);

        // 查询API调用次数和今日统计（仍需要从 conversation_message 表）
        result.setTotalApiCalls(conversationMessageMapper.selectTotalApiCallsByUserId(userId));
        result.setTodayApiCalls(conversationMessageMapper.selectTodayApiCallsByUserId(userId));
        result.setTotalInputTokens(conversationMessageMapper.selectTotalInputTokensByUserId(userId));
        result.setTotalOutputTokens(conversationMessageMapper.selectTotalOutputTokensByUserId(userId));
        result.setTodayTokens(conversationMessageMapper.selectTodayTokensByUserId(userId));

        // 各模型使用频率（从 user_model_usage 表获取 Token 数据）
        // 调用次数仍需从 conversation_message 表统计
        QueryWrapper callCountWrapper = QueryWrapper.create()
                .eq("userId", userId)
                .eq("role", "assistant")
                .eq("isDelete", 0)
                .isNotNull("modelName");
        List<ConversationMessage> messages = conversationMessageMapper.selectListByQuery(callCountWrapper);

        Map<String, Long> callCountByModel = messages.stream()
                .collect(Collectors.groupingBy(ConversationMessage::getModelName, Collectors.counting()));

        long totalCalls = messages.size();
        List<UsageStatisticsVO.ModelUsageVO> usageByModel = new ArrayList<>();

        // 合并调用次数和 Token 数据
        for (UserModelUsage usage : usageList) {
            if (usage.getModelName() != null) {
                UsageStatisticsVO.ModelUsageVO modelUsage = new UsageStatisticsVO.ModelUsageVO();
                modelUsage.setModelName(usage.getModelName());
                modelUsage.setTokens(usage.getTotalTokens() != null ? usage.getTotalTokens() : 0L);
                modelUsage.setCallCount(callCountByModel.getOrDefault(usage.getModelName(), 0L));
                modelUsage.setPercentage(totalCalls > 0 
                        ? (double) modelUsage.getCallCount() * 100 / totalCalls 
                        : 0.0);
                usageByModel.add(modelUsage);
            }
        }
        usageByModel.sort((a, b) -> Long.compare(b.getCallCount(), a.getCallCount()));
        result.setUsageByModel(usageByModel);

        // 使用趋势（按天，需要从 conversation_message 表查询）
        LocalDate endDate = LocalDate.now();
        LocalDate startDate = endDate.minusDays(days - 1);

        QueryWrapper trendWrapper = QueryWrapper.create()
                .eq("userId", userId)
                .eq("role", "assistant")
                .eq("isDelete", 0)
                .ge("createTime", startDate.atStartOfDay())
                .le("createTime", endDate.plusDays(1).atStartOfDay());
        List<ConversationMessage> recentMessages = conversationMessageMapper.selectListByQuery(trendWrapper);

        Map<String, Long> dailyCalls = new HashMap<>();
        Map<String, Long> dailyTokens = new HashMap<>();
        for (ConversationMessage msg : recentMessages) {
            if (msg.getCreateTime() != null) {
                String dateStr = msg.getCreateTime().toLocalDate().format(DATE_FORMATTER);
                dailyCalls.merge(dateStr, 1L, Long::sum);
                long tokens = (msg.getInputTokens() != null ? msg.getInputTokens() : 0) +
                        (msg.getOutputTokens() != null ? msg.getOutputTokens() : 0);
                dailyTokens.merge(dateStr, tokens, Long::sum);
            }
        }

        List<UsageStatisticsVO.DailyUsageVO> usageTrend = new ArrayList<>();
        for (LocalDate date = startDate; !date.isAfter(endDate); date = date.plusDays(1)) {
            UsageStatisticsVO.DailyUsageVO dailyUsage = new UsageStatisticsVO.DailyUsageVO();
            String dateStr = date.format(DATE_FORMATTER);
            dailyUsage.setDate(dateStr);
            dailyUsage.setApiCalls(dailyCalls.getOrDefault(dateStr, 0L));
            dailyUsage.setTokens(dailyTokens.getOrDefault(dateStr, 0L));
            usageTrend.add(dailyUsage);
        }
        result.setUsageTrend(usageTrend);

        // 写入缓存
        try {
            stringRedisTemplate.opsForValue().set(cacheKey, JSONUtil.toJsonStr(result),
                    Duration.ofMinutes(CacheConstant.STATISTICS_TTL_MINUTES));
            log.debug("使用量统计数据已缓存: userId={}, days={}", userId, days);
        } catch (Exception e) {
            log.warn("写入使用量统计缓存失败: {}", e.getMessage());
        }

        return result;
    }

    @Override
    public PerformanceStatisticsVO getPerformanceStatistics(Long userId) {
        // 尝试从缓存获取
        String cacheKey = CacheConstant.STATISTICS_PERFORMANCE_KEY_PREFIX + userId;
        try {
            String cachedValue = stringRedisTemplate.opsForValue().get(cacheKey);
            if (cachedValue != null) {
                log.debug("从缓存获取性能统计数据: userId={}", userId);
                return JSONUtil.toBean(cachedValue, PerformanceStatisticsVO.class);
            }
        } catch (Exception e) {
            log.warn("读取性能统计缓存失败: {}", e.getMessage());
        }

        PerformanceStatisticsVO result = new PerformanceStatisticsVO();

        // 查询所有assistant消息
        QueryWrapper wrapper = QueryWrapper.create()
                .eq("userId", userId)
                .eq("role", "assistant")
                .eq("isDelete", 0)
                .isNotNull("responseTimeMs");
        List<ConversationMessage> messages = conversationMessageMapper.selectListByQuery(wrapper);

        if (messages.isEmpty()) {
            result.setAvgResponseTime(0.0);
            result.setMinResponseTime(0);
            result.setMaxResponseTime(0);
            result.setPerformanceByModel(new ArrayList<>());
            return result;
        }

        // 计算整体性能指标
        double avgResponseTime = messages.stream()
                .mapToInt(m -> m.getResponseTimeMs() != null ? m.getResponseTimeMs() : 0)
                .average()
                .orElse(0.0);
        int minResponseTime = messages.stream()
                .mapToInt(m -> m.getResponseTimeMs() != null ? m.getResponseTimeMs() : Integer.MAX_VALUE)
                .min()
                .orElse(0);
        int maxResponseTime = messages.stream()
                .mapToInt(m -> m.getResponseTimeMs() != null ? m.getResponseTimeMs() : 0)
                .max()
                .orElse(0);

        result.setAvgResponseTime(Math.round(avgResponseTime * 100.0) / 100.0);
        result.setMinResponseTime(minResponseTime);
        result.setMaxResponseTime(maxResponseTime);

        // 按模型分组统计
        Map<String, List<ConversationMessage>> messagesByModel = messages.stream()
                .filter(m -> m.getModelName() != null)
                .collect(Collectors.groupingBy(ConversationMessage::getModelName));

        List<PerformanceStatisticsVO.ModelPerformanceVO> performanceByModel = new ArrayList<>();
        for (Map.Entry<String, List<ConversationMessage>> entry : messagesByModel.entrySet()) {
            List<ConversationMessage> modelMessages = entry.getValue();
            PerformanceStatisticsVO.ModelPerformanceVO perf = new PerformanceStatisticsVO.ModelPerformanceVO();
            perf.setModelName(entry.getKey());
            perf.setCallCount((long) modelMessages.size());

            double modelAvgResponseTime = modelMessages.stream()
                    .mapToInt(m -> m.getResponseTimeMs() != null ? m.getResponseTimeMs() : 0)
                    .average()
                    .orElse(0.0);
            perf.setAvgResponseTime(Math.round(modelAvgResponseTime * 100.0) / 100.0);

            int modelMinResponseTime = modelMessages.stream()
                    .mapToInt(m -> m.getResponseTimeMs() != null ? m.getResponseTimeMs() : Integer.MAX_VALUE)
                    .min()
                    .orElse(0);
            perf.setMinResponseTime(modelMinResponseTime);

            int modelMaxResponseTime = modelMessages.stream()
                    .mapToInt(m -> m.getResponseTimeMs() != null ? m.getResponseTimeMs() : 0)
                    .max()
                    .orElse(0);
            perf.setMaxResponseTime(modelMaxResponseTime);

            double avgInputTokens = modelMessages.stream()
                    .mapToInt(m -> m.getInputTokens() != null ? m.getInputTokens() : 0)
                    .average()
                    .orElse(0.0);
            perf.setAvgInputTokens(Math.round(avgInputTokens * 100.0) / 100.0);

            double avgOutputTokens = modelMessages.stream()
                    .mapToInt(m -> m.getOutputTokens() != null ? m.getOutputTokens() : 0)
                    .average()
                    .orElse(0.0);
            perf.setAvgOutputTokens(Math.round(avgOutputTokens * 100.0) / 100.0);

            performanceByModel.add(perf);
        }
        performanceByModel.sort((a, b) -> Double.compare(a.getAvgResponseTime(), b.getAvgResponseTime()));
        result.setPerformanceByModel(performanceByModel);

        // 写入缓存
        try {
            stringRedisTemplate.opsForValue().set(cacheKey, JSONUtil.toJsonStr(result),
                    Duration.ofMinutes(CacheConstant.STATISTICS_TTL_MINUTES));
            log.debug("性能统计数据已缓存: userId={}", userId);
        } catch (Exception e) {
            log.warn("写入性能统计缓存失败: {}", e.getMessage());
        }

        return result;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public int refreshUserModelUsageData() {
        log.info("开始刷新用户模型使用统计数据...");

        // 用于存储聚合数据: Map<userId, Map<modelName, UsageData>>
        Map<Long, Map<String, UsageData>> aggregatedData = new HashMap<>();

        // 1. 从会话消息表聚合数据（只统计 assistant 消息）
        QueryWrapper messageWrapper = QueryWrapper.create()
                .eq("role", "assistant")
                .eq("isDelete", 0)
                .isNotNull("modelName");
        List<ConversationMessage> messages = conversationMessageMapper.selectListByQuery(messageWrapper);

        log.info("从会话消息表查询到 {} 条 assistant 消息", messages.size());

        for (ConversationMessage msg : messages) {
            if (msg.getUserId() == null || msg.getModelName() == null) {
                continue;
            }

            int inputTokens = msg.getInputTokens() != null ? msg.getInputTokens() : 0;
            int outputTokens = msg.getOutputTokens() != null ? msg.getOutputTokens() : 0;
            int totalTokens = inputTokens + outputTokens;
            BigDecimal cost = msg.getCost() != null ? msg.getCost() : BigDecimal.ZERO;

            aggregatedData
                    .computeIfAbsent(msg.getUserId(), k -> new HashMap<>())
                    .computeIfAbsent(msg.getModelName(), k -> new UsageData())
                    .add(totalTokens, cost);
        }

        // 2. 从测试结果表聚合数据
        QueryWrapper testResultWrapper = QueryWrapper.create()
                .eq("isDelete", 0)
                .isNotNull("modelName");
        List<TestResult> testResults = testResultMapper.selectListByQuery(testResultWrapper);

        log.info("从测试结果表查询到 {} 条记录", testResults.size());

        for (TestResult result : testResults) {
            if (result.getUserId() == null || result.getModelName() == null) {
                continue;
            }

            int inputTokens = result.getInputTokens() != null ? result.getInputTokens() : 0;
            int outputTokens = result.getOutputTokens() != null ? result.getOutputTokens() : 0;
            int totalTokens = inputTokens + outputTokens;
            BigDecimal cost = result.getCost() != null ? result.getCost() : BigDecimal.ZERO;

            aggregatedData
                    .computeIfAbsent(result.getUserId(), k -> new HashMap<>())
                    .computeIfAbsent(result.getModelName(), k -> new UsageData())
                    .add(totalTokens, cost);
        }

        // 3. 清空现有数据并插入新数据
        log.info("清空现有用户模型使用统计数据...");
        QueryWrapper deleteWrapper = QueryWrapper.create()
                .eq("isDelete", 0);
        userModelUsageMapper.deleteByQuery(deleteWrapper);

        // 4. 插入聚合后的数据
        int insertCount = 0;
        LocalDateTime now = LocalDateTime.now();

        for (Map.Entry<Long, Map<String, UsageData>> userEntry : aggregatedData.entrySet()) {
            Long userId = userEntry.getKey();
            for (Map.Entry<String, UsageData> modelEntry : userEntry.getValue().entrySet()) {
                String modelName = modelEntry.getKey();
                UsageData usageData = modelEntry.getValue();

                if (usageData.totalTokens > 0) {
                    UserModelUsage usage = UserModelUsage.builder()
                            .id(IdUtil.randomUUID())
                            .userId(userId)
                            .modelName(modelName)
                            .totalTokens(usageData.totalTokens)
                            .totalCost(usageData.totalCost)
                            .createTime(now)
                            .updateTime(now)
                            .isDelete(0)
                            .build();

                    userModelUsageMapper.insert(usage);
                    insertCount++;

                    log.debug("插入用户模型使用统计: userId={}, model={}, tokens={}, cost={}",
                            userId, modelName, usageData.totalTokens, usageData.totalCost);
                }
            }
        }

        log.info("刷新用户模型使用统计数据完成，共插入 {} 条记录", insertCount);
        return insertCount;
    }

    @Override
    public RealtimeCostVO getRealtimeCost(Long userId) {
        RealtimeCostVO.RealtimeCostVOBuilder builder = RealtimeCostVO.builder();

        User user = userService.getById(userId);
        if (user == null) {
            return builder
                    .todayCost(BigDecimal.ZERO)
                    .monthCost(BigDecimal.ZERO)
                    .todayTokens(0L)
                    .todayApiCalls(0L)
                    .avgCostPerCall(BigDecimal.ZERO)
                    .budgetStatus("normal")
                    .budgetMessage("预算充足")
                    .build();
        }

        BigDecimal dailyBudget = user.getDailyBudget();
        BigDecimal monthlyBudget = user.getMonthlyBudget();
        Integer alertThreshold = user.getBudgetAlertThreshold();
        if (alertThreshold == null) {
            alertThreshold = 80;
        }

        BigDecimal todayCost = budgetService.getTodayCost(userId);
        BigDecimal monthCost = budgetService.getMonthCost(userId);

        Long todayApiCalls = conversationMessageMapper.selectTodayApiCallsByUserId(userId);
        Long todayTokens = conversationMessageMapper.selectTodayTokensByUserId(userId);

        BigDecimal avgCostPerCall = BigDecimal.ZERO;
        if (todayApiCalls != null && todayApiCalls > 0 && todayCost != null) {
            avgCostPerCall = todayCost.divide(new BigDecimal(todayApiCalls), 6, RoundingMode.HALF_UP);
        }

        BigDecimal dailyUsagePercent = BigDecimal.ZERO;
        if (dailyBudget != null && dailyBudget.compareTo(BigDecimal.ZERO) > 0) {
            dailyUsagePercent = todayCost.multiply(new BigDecimal("100"))
                    .divide(dailyBudget, 2, RoundingMode.HALF_UP);
        }

        BigDecimal monthlyUsagePercent = BigDecimal.ZERO;
        if (monthlyBudget != null && monthlyBudget.compareTo(BigDecimal.ZERO) > 0) {
            monthlyUsagePercent = monthCost.multiply(new BigDecimal("100"))
                    .divide(monthlyBudget, 2, RoundingMode.HALF_UP);
        }

        BudgetStatusVO budgetStatus = budgetService.checkBudget(userId);

        return builder
                .todayCost(todayCost)
                .monthCost(monthCost)
                .todayTokens(todayTokens != null ? todayTokens : 0L)
                .todayApiCalls(todayApiCalls != null ? todayApiCalls : 0L)
                .avgCostPerCall(avgCostPerCall)
                .dailyBudget(dailyBudget)
                .monthlyBudget(monthlyBudget)
                .dailyUsagePercent(dailyUsagePercent)
                .monthlyUsagePercent(monthlyUsagePercent)
                .budgetStatus(budgetStatus.getStatus())
                .budgetMessage(budgetStatus.getMessage())
                .alertThreshold(alertThreshold)
                .build();
    }

    /**
     * 内部类：用于临时存储聚合数据
     */
    private static class UsageData {
        long totalTokens = 0;
        BigDecimal totalCost = BigDecimal.ZERO;

        void add(int tokens, BigDecimal cost) {
            this.totalTokens += tokens;
            if (cost != null) {
                this.totalCost = this.totalCost.add(cost);
            }
        }
    }
}
