package com.yupi.template.model.dto.conversation;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.io.Serializable;
import java.util.List;

/**
 * Side-by-Side 并排对比请求
 *
 * @author <a href="https://codefather.cn">编程导航学习圈</a>
 */
@Data
@Schema(description = "Side-by-Side并排对比请求")
public class SideBySideRequest implements Serializable {

    /**
     * 模型列表 (1-8个)
     */
    @Schema(description = "模型列表(1-8个)", example = "[\"openai/gpt-4o\", \"anthropic/claude-3.5-sonnet\"]")
    private List<String> models;

    /**
     * 用户提示词
     */
    @Schema(description = "用户提示词", example = "请帮我写一个快速排序算法")
    private String prompt;

    /**
     * 图片URL列表（可选，用于多模态）
     */
    @Schema(description = "图片URL列表（可选）", example = "[\"https://xxx.com/a.jpg\"]")
    private List<String> imageUrls;

    /**
     * 对话ID (多轮对话时传入)
     */
    @Schema(description = "对话ID，多轮对话时传入")
    private String conversationId;

    /**
     * 是否使用流式响应
     */
    @Schema(description = "是否使用流式响应", example = "true")
    private Boolean stream = true;

    /**
     * 是否启用联网搜索（OpenRouter :online）
     */
    @Schema(description = "是否启用联网搜索", example = "false")
    private Boolean webSearchEnabled = false;

    private static final long serialVersionUID = 1L;
}
