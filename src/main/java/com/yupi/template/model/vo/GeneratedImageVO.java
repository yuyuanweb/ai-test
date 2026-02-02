package com.yupi.template.model.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;

/**
 * 生成图片结果 VO
 *
 * @author <a href="https://codefather.cn">编程导航学习圈</a>
 */
@Data
@Builder
@Schema(description = "生成图片结果")
public class GeneratedImageVO implements Serializable {

    /**
     * 图片访问地址
     */
    @Schema(description = "图片访问地址")
    private String url;

    /**
     * 使用的模型名称
     */
    @Schema(description = "模型名称（OpenRouter 模型 ID）")
    private String modelName;

    /**
     * 第几张图片（从 0 开始）
     */
    @Schema(description = "生成序号（从 0 开始）")
    private Integer index;

    /**
     * 本次调用的输入 Token 数量
     */
    @Schema(description = "输入 Token 数")
    private Integer inputTokens;

    /**
     * 本次调用的输出 Token 数量
     */
    @Schema(description = "输出 Token 数")
    private Integer outputTokens;

    /**
     * 本次调用消耗的总 Token 数
     */
    @Schema(description = "总 Token 数")
    private Integer totalTokens;

    /**
     * 本次调用消耗的费用（USD）
     */
    @Schema(description = "本次调用费用（USD）")
    private Double cost;

    /**
     * 会话ID（如果保存到会话）
     */
    @Schema(description = "会话ID（如果保存到会话）")
    private String conversationId;

    /**
     * 消息索引（用于提示词对比页面多变体共享）
     */
    @Schema(description = "消息索引")
    private Integer messageIndex;

    @Serial
    private static final long serialVersionUID = 1L;
}

