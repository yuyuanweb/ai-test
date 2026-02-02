package com.yupi.template.model.dto.image;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.util.List;

/**
 * 图片生成请求
 *
 * @author <a href="https://codefather.cn">编程导航学习圈</a>
 */
@Data
@Schema(description = "图片生成请求参数")
public class GenerateImageRequest implements Serializable {

    /**
     * 模型名称（必须为支持多模态的模型，匿名模式下可为空）
     */
    @Schema(description = "模型名称（OpenRouter 模型 ID，匿名模式下可为空）",
            example = "openai/gpt-4.1-mini")
    private String model;

    /**
     * 是否匿名模式（Battle 页面使用）
     */
    @Schema(description = "是否匿名模式（Battle 页面使用）", example = "false")
    private Boolean isAnonymous;

    /**
     * 生成图片的提示词
     */
    @Schema(description = "图片生成提示词", requiredMode = Schema.RequiredMode.REQUIRED,
            example = "一只坐在月亮上的可爱猫咪，赛博朋克风格")
    @NotBlank(message = "提示词不能为空")
    private String prompt;

    /**
     * 参考图片 URL 列表（可选，用于图生图）
     */
    @Schema(description = "参考图片 URL 列表（可选）",
            example = "[\"https://example.com/a.png\", \"https://example.com/b.png\"]")
    private List<String> referenceImageUrls;

    /**
     * 生成图片数量
     */
    @Schema(description = "生成图片数量（1-4）", example = "1")
    @NotNull(message = "生成数量不能为空")
    @Min(value = 1, message = "生成数量至少为 1")
    @Max(value = 4, message = "生成数量最多为 4 张")
    private Integer count;

    /**
     * 对话ID（可选，如果提供则保存到会话中）
     */
    @Schema(description = "对话ID（可选）", example = "conversation-123")
    private String conversationId;

    /**
     * 模型列表（可选，用于创建会话时指定模型）
     */
    @Schema(description = "模型列表（可选）", example = "[\"openai/gpt-4o\"]")
    private List<String> models;

    /**
     * 会话类型（可选，用于创建会话时指定类型）
     */
    @Schema(description = "会话类型（side_by_side / prompt_lab）", example = "prompt_lab")
    private String conversationType;

    /**
     * 变体索引（可选，用于提示词对比页面标识变体）
     */
    @Schema(description = "变体索引（0, 1, 2...）", example = "0")
    private Integer variantIndex;

    /**
     * 消息索引（可选，用于多变体时共享同一个消息索引）
     */
    @Schema(description = "消息索引", example = "0")
    private Integer messageIndex;

    /**
     * 思考内容（可选，流式生成时传入）
     */
    @Schema(description = "思考内容", hidden = true)
    private String reasoning;

    @Serial
    private static final long serialVersionUID = 1L;
}

