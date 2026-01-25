package com.yupi.template.model.dto.scene;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.io.Serializable;

/**
 * 更新场景提示词请求
 *
 * @author <a href="https://codefather.cn">编程导航学习圈</a>
 */
@Data
@Schema(description = "更新场景提示词请求")
public class UpdateScenePromptRequest implements Serializable {

    /**
     * 提示词ID
     */
    @Schema(description = "提示词ID", required = true)
    private String id;

    /**
     * 提示词标题
     */
    @Schema(description = "提示词标题")
    private String title;

    /**
     * 提示词内容
     */
    @Schema(description = "提示词内容")
    private String content;

    /**
     * 难度: easy/medium/hard
     */
    @Schema(description = "难度")
    private String difficulty;

    /**
     * 期望输出(可选)
     */
    @Schema(description = "期望输出")
    private String expectedOutput;

    private static final long serialVersionUID = 1L;
}
