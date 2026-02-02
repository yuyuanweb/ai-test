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
}
