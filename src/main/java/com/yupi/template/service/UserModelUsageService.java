package com.yupi.template.service;

import com.yupi.template.model.entity.UserModelUsage;

import java.math.BigDecimal;

/**
 * 用户-模型使用统计服务
 *
 * @author <a href="https://codefather.cn">编程导航学习圈</a>
 */
public interface UserModelUsageService {

    /**
     * 更新用户-模型使用统计
     *
     * @param userId    用户ID
     * @param modelName 模型名称
     * @param tokens    Token数
     * @param cost      花费
     */
    void updateUserModelUsage(Long userId, String modelName, int tokens, BigDecimal cost);

    /**
     * 获取用户-模型使用统计
     *
     * @param userId    用户ID
     * @param modelName 模型名称
     * @return 使用统计
     */
    UserModelUsage getUserModelUsage(Long userId, String modelName);
}
