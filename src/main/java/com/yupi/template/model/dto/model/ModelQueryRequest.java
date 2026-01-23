package com.yupi.template.model.dto.model;

import com.yupi.template.common.PageRequest;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.io.Serializable;

/**
 * 模型查询请求
 *
 * @author <a href="https://codefather.cn">编程导航学习圈</a>
 */
@Data
@EqualsAndHashCode(callSuper = true)
@Schema(description = "模型查询请求")
public class ModelQueryRequest extends PageRequest implements Serializable {

    /**
     * 搜索关键词（模型名称或ID）
     */
    @Schema(description = "搜索关键词")
    private String searchText;

    /**
     * 提供商筛选
     */
    @Schema(description = "提供商")
    private String provider;

    /**
     * 是否只查询推荐模型
     */
    @Schema(description = "是否只查询推荐模型")
    private Boolean onlyRecommended;

    /**
     * 是否只查询国内模型
     */
    @Schema(description = "是否只查询国内模型")
    private Boolean onlyChina;

    private static final long serialVersionUID = 1L;
}

