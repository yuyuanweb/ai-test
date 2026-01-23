package com.yupi.template.model.dto.conversation;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.io.Serializable;
import java.util.List;

/**
 * 创建对话请求
 *
 * @author <a href="https://codefather.cn">编程导航学习圈</a>
 */
@Data
@Schema(description = "创建对话请求")
public class CreateConversationRequest implements Serializable {

    /**
     * 对话标题
     */
    @Schema(description = "对话标题")
    private String title;

    /**
     * 对话类型: side_by_side/prompt_lab/code_mode
     */
    @Schema(description = "对话类型", example = "side_by_side")
    private String conversationType;

    /**
     * 是否启用代码预览模式
     */
    @Schema(description = "是否启用代码预览模式")
    private Boolean codePreviewEnabled;

    /**
     * 参与的模型列表
     */
    @Schema(description = "参与的模型列表")
    private List<String> models;

    private static final long serialVersionUID = 1L;
}

