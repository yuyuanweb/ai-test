package com.yupi.template.service;

import com.yupi.template.model.vo.BudgetStatusVO;

import java.math.BigDecimal;

/**
 * 预算服务接口
 *
 * @author <a href="https://codefather.cn">编程导航学习圈</a>
 */
public interface BudgetService {

    /**
     * 检查用户预算是否充足
     *
     * @param userId 用户ID
     * @return 预算状态
     */
    BudgetStatusVO checkBudget(Long userId);

    /**
     * 累加用户消耗（调用后执行）
     *
     * @param userId 用户ID
     * @param cost   本次消耗（USD）
     */
    void addCost(Long userId, BigDecimal cost);

    /**
     * 获取用户今日消耗（从Redis获取，用于实时展示）
     *
     * @param userId 用户ID
     * @return 今日消耗
     */
    BigDecimal getTodayCost(Long userId);

    /**
     * 获取用户本月消耗（从Redis获取，用于实时展示）
     *
     * @param userId 用户ID
     * @return 本月消耗
     */
    BigDecimal getMonthCost(Long userId);

    /**
     * 从数据库同步消耗数据到Redis
     *
     * @param userId 用户ID
     */
    void syncCostFromDB(Long userId);
}
