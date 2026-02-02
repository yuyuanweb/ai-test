package com.yupi.template.service;

import com.mybatisflex.core.paginate.Page;
import com.yupi.template.model.dto.model.ModelQueryRequest;
import com.yupi.template.model.entity.Model;
import com.yupi.template.model.vo.ModelPricingVO;
import com.yupi.template.model.vo.ModelVO;

import java.math.BigDecimal;
import java.util.List;

/**
 * 模型服务接口
 *
 * @author <a href="https://codefather.cn">编程导航学习圈</a>
 */
public interface ModelService {

    /**
     * 分页查询模型列表（支持搜索）
     *
     * @param queryRequest 查询请求
     * @param userId 用户ID（用于查询用户维度的统计数据）
     * @return 模型分页列表
     */
    Page<ModelVO> listModels(ModelQueryRequest queryRequest, Long userId);

    /**
     * 获取所有模型列表（国内优先）
     *
     * @param userId 用户ID（用于查询用户维度的统计数据）
     * @return 模型列表
     */
    List<ModelVO> getAllModels(Long userId);

    /**
     * 获取模型价格（带 Redis 缓存，Key: model:pricing:{modelName}，24h 过期）
     * 用于成本计算，避免重复查库。
     *
     * @param modelName 模型名称（OpenRouter ID）
     * @return 价格信息，不存在时返回 null
     */
    ModelPricingVO getModelPricing(String modelName);

    /**
     * 清除模型价格缓存（如同步 OpenRouter 后需使旧缓存失效）
     *
     * @param modelName 模型名称
     */
    void evictModelPricingCache(String modelName);

    /**
     * 更新模型使用统计
     *
     * @param modelName 模型名称
     * @param tokens 本次使用的Token数
     * @param cost 本次花费（美元）
     */
    void updateModelUsage(String modelName, int tokens, BigDecimal cost);

    /**
     * 根据模型 ID 查询模型信息
     *
     * @param modelId 模型 ID（OpenRouter 模型 ID）
     * @return 模型信息，未找到时返回 null
     */
    Model getModelById(String modelId);
}

