package com.yupi.template.model.dto.scene;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.io.Serializable;

/**
 * 创建场景请求
 *
 * @author <a href="https://codefather.cn">编程导航学习圈</a>
 */
@Data
@Schema(description = "创建场景请求")
public class CreateSceneRequest implements Serializable {

    /**
     * 场景名称
     */
    @Schema(description = "场景名称", required = true)
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

    private static final long serialVersionUID = 1L;
}
