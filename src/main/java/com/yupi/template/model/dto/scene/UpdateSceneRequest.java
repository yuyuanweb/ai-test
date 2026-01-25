package com.yupi.template.model.dto.scene;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.io.Serializable;

/**
 * 更新场景请求
 *
 * @author <a href="https://codefather.cn">编程导航学习圈</a>
 */
@Data
@Schema(description = "更新场景请求")
public class UpdateSceneRequest implements Serializable {

    /**
     * 场景ID
     */
    @Schema(description = "场景ID", required = true)
    private String id;

    /**
     * 场景名称
     */
    @Schema(description = "场景名称")
    private String name;

    /**
     * 场景描述
     */
    @Schema(description = "场景描述")
    private String description;

    /**
     * 分类:编程/数学/文案等
     */
    @Schema(description = "分类")
    private String category;

    /**
     * 是否启用(1-启用 0-禁用)
     */
    @Schema(description = "是否启用")
    private Integer isActive;

    private static final long serialVersionUID = 1L;
}
