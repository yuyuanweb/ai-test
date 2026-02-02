package com.yupi.template.model.dto.conversation;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.util.List;

/**
 * 基础对话请求
 *
 * @author <a href="https://codefather.cn">编程导航学习圈</a>
 */
@Data
@Schema(description = "基础对话请求")
public class ChatRequest implements Serializable {

    /**
     * 对话ID（可选，不传则创建新对话）
     */
    @Schema(description = "对话ID")
    private String conversationId;

    /**
     * 模型名称（如：openai/gpt-4o）
     */
    @Schema(description = "模型名称", required = true)
    private String model;

    /**
     * 用户消息
     */
    @Schema(description = "用户消息", required = true)
    private String message;

    /**
     * 图片URL列表（可选，用于多模态）
     */
    @Schema(description = "图片URL列表（可选）", example = "[\"https://xxx.com/a.jpg\"]")
    private List<String> imageUrls;

    /**
     * 是否启用联网搜索（OpenRouter :online）
     */
    @Schema(description = "是否启用联网搜索", example = "false")
    private Boolean webSearchEnabled = false;

    @Serial
    private static final long serialVersionUID = 1L;
}

