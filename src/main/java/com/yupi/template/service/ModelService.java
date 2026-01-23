package com.yupi.template.service;

import com.mybatisflex.core.paginate.Page;
import com.yupi.template.model.dto.model.ModelQueryRequest;
import com.yupi.template.model.vo.ModelVO;

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
     * @return 模型分页列表
     */
    Page<ModelVO> listModels(ModelQueryRequest queryRequest);

    /**
     * 获取所有模型列表（国内优先）
     *
     * @return 模型列表
     */
    List<ModelVO> getAllModels();
}

