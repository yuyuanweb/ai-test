package com.yupi.template.service.impl;

import cn.hutool.core.util.IdUtil;
import com.mybatisflex.core.query.QueryWrapper;
import com.mybatisflex.spring.service.impl.ServiceImpl;
import com.yupi.template.mapper.UserModelUsageMapper;
import com.yupi.template.model.entity.UserModelUsage;
import com.yupi.template.service.UserModelUsageService;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 用户-模型使用统计服务实现
 *
 * @author <a href="https://codefather.cn">编程导航学习圈</a>
 */
@Slf4j
@Service
public class UserModelUsageServiceImpl extends ServiceImpl<UserModelUsageMapper, UserModelUsage> implements UserModelUsageService {

    @Resource
    private UserModelUsageMapper userModelUsageMapper;

    @Override
    public void updateUserModelUsage(Long userId, String modelName, int tokens, BigDecimal cost) {
        if (userId == null || modelName == null || modelName.trim().isEmpty() || tokens <= 0) {
            return;
        }

        try {
            QueryWrapper queryWrapper = QueryWrapper.create()
                    .eq("userId", userId)
                    .eq("modelName", modelName)
                    .eq("isDelete", 0);
            UserModelUsage usage = userModelUsageMapper.selectOneByQuery(queryWrapper);

            if (usage == null) {
                // 创建新记录
                usage = UserModelUsage.builder()
                        .id(IdUtil.randomUUID())
                        .userId(userId)
                        .modelName(modelName)
                        .totalTokens((long) tokens)
                        .totalCost(cost != null ? cost : BigDecimal.ZERO)
                        .createTime(LocalDateTime.now())
                        .updateTime(LocalDateTime.now())
                        .isDelete(0)
                        .build();
                userModelUsageMapper.insert(usage);
            } else {
                // 更新现有记录
                Long currentTokens = usage.getTotalTokens() != null ? usage.getTotalTokens() : 0L;
                BigDecimal currentCost = usage.getTotalCost() != null ? usage.getTotalCost() : BigDecimal.ZERO;

                usage.setTotalTokens(currentTokens + tokens);
                if (cost != null) {
                    usage.setTotalCost(currentCost.add(cost));
                }
                usage.setUpdateTime(LocalDateTime.now());

                userModelUsageMapper.update(usage);
            }

            log.debug("更新用户-模型使用统计: userId={}, modelName={}, addTokens={}, addCost={}, totalTokens={}, totalCost={}",
                    userId, modelName, tokens, cost, usage.getTotalTokens(), usage.getTotalCost());
        } catch (Exception e) {
            log.error("更新用户-模型使用统计失败: userId={}, modelName={}", userId, modelName, e);
        }
    }

    @Override
    public UserModelUsage getUserModelUsage(Long userId, String modelName) {
        if (userId == null || modelName == null || modelName.trim().isEmpty()) {
            return null;
        }

        QueryWrapper queryWrapper = QueryWrapper.create()
                .eq("userId", userId)
                .eq("modelName", modelName)
                .eq("isDelete", 0);
        return userModelUsageMapper.selectOneByQuery(queryWrapper);
    }
}
