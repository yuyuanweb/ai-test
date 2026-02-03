package com.yupi.template.constant;

/**
 * 缓存常量
 *
 * @author <a href="https://codefather.cn">编程导航学习圈</a>
 */
public interface CacheConstant {

    /**
     * 模型价格缓存名称
     */
    String MODEL_PRICING_CACHE_NAME = "modelPricing";

    /**
     * 模型价格缓存 Key 前缀，完整 Key 为 model:pricing:{modelName}
     */
    String MODEL_PRICING_KEY_PREFIX = "model:pricing:";

    /**
     * 模型价格缓存过期时间（小时）
     */
    int MODEL_PRICING_TTL_HOURS = 24;

    /**
     * 用户日消耗缓存 Key 前缀，完整 Key 为 user:cost:daily:{userId}
     */
    String USER_DAILY_COST_KEY_PREFIX = "user:cost:daily:";

    /**
     * 用户月消耗缓存 Key 前缀，完整 Key 为 user:cost:monthly:{userId}:{yearMonth}
     */
    String USER_MONTHLY_COST_KEY_PREFIX = "user:cost:monthly:";

    /**
     * 用户日消耗缓存过期时间（小时），设置为25小时确保当天有效
     */
    int USER_DAILY_COST_TTL_HOURS = 25;

    /**
     * 用户月消耗缓存过期时间（天），设置为32天确保当月有效
     */
    int USER_MONTHLY_COST_TTL_DAYS = 32;

    /**
     * 成本统计缓存 Key 前缀，完整 Key 为 statistics:cost:{userId}:{days}
     */
    String STATISTICS_COST_KEY_PREFIX = "statistics:cost:";

    /**
     * 使用量统计缓存 Key 前缀，完整 Key 为 statistics:usage:{userId}:{days}
     */
    String STATISTICS_USAGE_KEY_PREFIX = "statistics:usage:";

    /**
     * 性能统计缓存 Key 前缀，完整 Key 为 statistics:performance:{userId}
     */
    String STATISTICS_PERFORMANCE_KEY_PREFIX = "statistics:performance:";

    /**
     * 统计数据缓存过期时间（分钟）
     */
    int STATISTICS_TTL_MINUTES = 5;
}
