package com.yupi.template.service.impl;

import com.yupi.template.constant.CacheConstant;
import com.yupi.template.mapper.ConversationMessageMapper;
import com.yupi.template.model.entity.User;
import com.yupi.template.model.vo.BudgetStatusVO;
import com.yupi.template.service.BudgetService;
import com.yupi.template.service.UserService;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;

/**
 * 预算服务实现
 *
 * @author <a href="https://codefather.cn">编程导航学习圈</a>
 */
@Slf4j
@Service
public class BudgetServiceImpl implements BudgetService {

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    @Resource
    private UserService userService;

    @Resource
    private ConversationMessageMapper conversationMessageMapper;

    private static final DateTimeFormatter YEAR_MONTH_FORMATTER = DateTimeFormatter.ofPattern("yyyyMM");

    @Override
    public BudgetStatusVO checkBudget(Long userId) {
        User user = userService.getById(userId);
        if (user == null) {
            return BudgetStatusVO.normal(BigDecimal.ZERO, BigDecimal.ZERO, null, null);
        }

        BigDecimal dailyBudget = user.getDailyBudget();
        BigDecimal monthlyBudget = user.getMonthlyBudget();
        Integer alertThreshold = user.getBudgetAlertThreshold();
        if (alertThreshold == null) {
            alertThreshold = 80;
        }

        BigDecimal todayCost = getTodayCost(userId);
        BigDecimal monthCost = getMonthCost(userId);

        BudgetStatusVO status;

        // 检查日预算
        if (dailyBudget != null && dailyBudget.compareTo(BigDecimal.ZERO) > 0) {
            BigDecimal dailyUsagePercent = todayCost.multiply(new BigDecimal("100"))
                    .divide(dailyBudget, 2, RoundingMode.HALF_UP);

            if (todayCost.compareTo(dailyBudget) >= 0) {
                status = BudgetStatusVO.exceeded(
                        String.format("今日预算已用完（%.2f / %.2f USD）", todayCost, dailyBudget),
                        todayCost, monthCost, dailyBudget, monthlyBudget);
                status.setDailyUsagePercent(dailyUsagePercent);
                return status;
            }

            if (dailyUsagePercent.compareTo(new BigDecimal(alertThreshold)) >= 0) {
                status = BudgetStatusVO.warning(
                        String.format("今日预算已使用 %.0f%%（%.2f / %.2f USD）", dailyUsagePercent, todayCost, dailyBudget),
                        todayCost, monthCost, dailyBudget, monthlyBudget);
                status.setDailyUsagePercent(dailyUsagePercent);
                checkMonthlyBudget(status, monthCost, monthlyBudget, alertThreshold);
                return status;
            }
        }

        // 检查月预算
        if (monthlyBudget != null && monthlyBudget.compareTo(BigDecimal.ZERO) > 0) {
            BigDecimal monthlyUsagePercent = monthCost.multiply(new BigDecimal("100"))
                    .divide(monthlyBudget, 2, RoundingMode.HALF_UP);

            if (monthCost.compareTo(monthlyBudget) >= 0) {
                status = BudgetStatusVO.exceeded(
                        String.format("本月预算已用完（%.2f / %.2f USD）", monthCost, monthlyBudget),
                        todayCost, monthCost, dailyBudget, monthlyBudget);
                status.setMonthlyUsagePercent(monthlyUsagePercent);
                return status;
            }

            if (monthlyUsagePercent.compareTo(new BigDecimal(alertThreshold)) >= 0) {
                status = BudgetStatusVO.warning(
                        String.format("本月预算已使用 %.0f%%（%.2f / %.2f USD）", monthlyUsagePercent, monthCost, monthlyBudget),
                        todayCost, monthCost, dailyBudget, monthlyBudget);
                status.setMonthlyUsagePercent(monthlyUsagePercent);
                return status;
            }
        }

        // 正常状态
        status = BudgetStatusVO.normal(todayCost, monthCost, dailyBudget, monthlyBudget);
        calculateUsagePercent(status, todayCost, monthCost, dailyBudget, monthlyBudget);
        return status;
    }

    @Override
    public void addCost(Long userId, BigDecimal cost) {
        if (cost == null || cost.compareTo(BigDecimal.ZERO) <= 0) {
            return;
        }

        String dailyKey = getDailyKey(userId);
        String monthlyKey = getMonthlyKey(userId);

        try {
            // 累加日消耗
            Double dailyCost = stringRedisTemplate.opsForValue().increment(dailyKey, cost.doubleValue());
            if (dailyCost != null && dailyCost.equals(cost.doubleValue())) {
                stringRedisTemplate.expire(dailyKey, Duration.ofHours(CacheConstant.USER_DAILY_COST_TTL_HOURS));
            }

            // 累加月消耗
            Double monthlyCost = stringRedisTemplate.opsForValue().increment(monthlyKey, cost.doubleValue());
            if (monthlyCost != null && monthlyCost.equals(cost.doubleValue())) {
                stringRedisTemplate.expire(monthlyKey, Duration.ofDays(CacheConstant.USER_MONTHLY_COST_TTL_DAYS));
            }

            log.debug("用户 {} 消耗累加成功，本次: {}, 今日: {}, 本月: {}", userId, cost, dailyCost, monthlyCost);
        } catch (Exception e) {
            log.error("用户 {} 消耗累加失败: {}", userId, e.getMessage(), e);
        }
    }

    @Override
    public BigDecimal getTodayCost(Long userId) {
        String dailyKey = getDailyKey(userId);
        String value = stringRedisTemplate.opsForValue().get(dailyKey);
        if (value == null) {
            syncCostFromDB(userId);
            value = stringRedisTemplate.opsForValue().get(dailyKey);
        }
        return value != null ? new BigDecimal(value) : BigDecimal.ZERO;
    }

    @Override
    public BigDecimal getMonthCost(Long userId) {
        String monthlyKey = getMonthlyKey(userId);
        String value = stringRedisTemplate.opsForValue().get(monthlyKey);
        if (value == null) {
            syncCostFromDB(userId);
            value = stringRedisTemplate.opsForValue().get(monthlyKey);
        }
        return value != null ? new BigDecimal(value) : BigDecimal.ZERO;
    }

    @Override
    public void syncCostFromDB(Long userId) {
        try {
            BigDecimal todayCost = conversationMessageMapper.selectTodayCostByUserId(userId);
            BigDecimal monthCost = conversationMessageMapper.selectMonthCostByUserId(userId);

            String dailyKey = getDailyKey(userId);
            String monthlyKey = getMonthlyKey(userId);

            if (todayCost != null && todayCost.compareTo(BigDecimal.ZERO) > 0) {
                stringRedisTemplate.opsForValue().set(dailyKey, todayCost.toString(),
                        Duration.ofHours(CacheConstant.USER_DAILY_COST_TTL_HOURS));
            }

            if (monthCost != null && monthCost.compareTo(BigDecimal.ZERO) > 0) {
                stringRedisTemplate.opsForValue().set(monthlyKey, monthCost.toString(),
                        Duration.ofDays(CacheConstant.USER_MONTHLY_COST_TTL_DAYS));
            }

            log.debug("用户 {} 消耗数据同步成功，今日: {}, 本月: {}", userId, todayCost, monthCost);
        } catch (Exception e) {
            log.error("用户 {} 消耗数据同步失败: {}", userId, e.getMessage(), e);
        }
    }

    private String getDailyKey(Long userId) {
        return CacheConstant.USER_DAILY_COST_KEY_PREFIX + userId;
    }

    private String getMonthlyKey(Long userId) {
        String yearMonth = LocalDate.now().format(YEAR_MONTH_FORMATTER);
        return CacheConstant.USER_MONTHLY_COST_KEY_PREFIX + userId + ":" + yearMonth;
    }

    private void checkMonthlyBudget(BudgetStatusVO status, BigDecimal monthCost,
                                    BigDecimal monthlyBudget, Integer alertThreshold) {
        if (monthlyBudget != null && monthlyBudget.compareTo(BigDecimal.ZERO) > 0) {
            BigDecimal monthlyUsagePercent = monthCost.multiply(new BigDecimal("100"))
                    .divide(monthlyBudget, 2, RoundingMode.HALF_UP);
            status.setMonthlyUsagePercent(monthlyUsagePercent);
        }
    }

    private void calculateUsagePercent(BudgetStatusVO status, BigDecimal todayCost, BigDecimal monthCost,
                                       BigDecimal dailyBudget, BigDecimal monthlyBudget) {
        if (dailyBudget != null && dailyBudget.compareTo(BigDecimal.ZERO) > 0) {
            status.setDailyUsagePercent(todayCost.multiply(new BigDecimal("100"))
                    .divide(dailyBudget, 2, RoundingMode.HALF_UP));
        } else {
            status.setDailyUsagePercent(BigDecimal.ZERO);
        }

        if (monthlyBudget != null && monthlyBudget.compareTo(BigDecimal.ZERO) > 0) {
            status.setMonthlyUsagePercent(monthCost.multiply(new BigDecimal("100"))
                    .divide(monthlyBudget, 2, RoundingMode.HALF_UP));
        } else {
            status.setMonthlyUsagePercent(BigDecimal.ZERO);
        }
    }
}
