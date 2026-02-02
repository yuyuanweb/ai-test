package com.yupi.template.model.dto.conversation;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.io.Serializable;
import java.util.List;

/**
 * Code Mode 提示词实验请求（代码模式下的多提示词对比）
 *
 * @author <a href="https://codefather.cn">编程导航学习圈</a>
 */
@Data
@Schema(description = "Code Mode提示词实验请求")
public class CodeModePromptLabRequest implements Serializable {

    /**
     * 模型名称
     */
    @Schema(description = "模型名称", example = "openai/gpt-4o")
    private String model;

    /**
     * 提示词变体列表 (2-5个)
     */
    @Schema(description = "提示词变体列表(2-5个)", example = "[\"创建一个计算器\", \"实现一个科学计算器\"]")
    private List<String> promptVariants;

    /**
     * 变体图片URL列表（可选，与promptVariants一一对应）
     */
    @Schema(description = "变体图片URL列表（可选，与promptVariants一一对应）",
            example = "[[\"https://xxx.com/a.jpg\"], []]")
    private List<List<String>> variantImageUrls;

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

