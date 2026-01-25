package com.yupi.template.service;

import com.mybatisflex.core.paginate.Page;
import com.yupi.template.model.dto.model.ModelQueryRequest;
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
     * 更新模型使用统计
     *
     * @param modelName 模型名称
     * @param tokens 本次使用的Token数
     * @param cost 本次花费（美元）
     */
    void updateModelUsage(String modelName, int tokens, BigDecimal cost);
}

