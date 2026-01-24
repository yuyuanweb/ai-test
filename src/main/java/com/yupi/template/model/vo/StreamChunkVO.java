package com.yupi.template.model.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

/**
 * 流式响应数据块 VO
 *
 * @author <a href="https://codefather.cn">编程导航学习圈</a>
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "流式响应数据块")
public class StreamChunkVO implements Serializable {

    /**
     * 对话ID
     */
    @Schema(description = "对话ID")
    private String conversationId;

    /**
     * 模型名称
     */
    @Schema(description = "模型名称")
    private String modelName;

    /**
     * 变体索引（Prompt Lab专用）
     */
    @Schema(description = "变体索引")
    private Integer variantIndex;

    /**
     * 内容片段
     */
    @Schema(description = "内容片段")
    private String content;

    /**
     * 完整内容（累加后的）
     */
    @Schema(description = "完整内容")
    private String fullContent;

    /**
     * 输入Token数
     */
    @Schema(description = "输入Token数")
    private Integer inputTokens;

    /**
     * 输出Token数
     */
    @Schema(description = "输出Token数")
    private Integer outputTokens;

    /**
     * 总Token数
     */
    @Schema(description = "总Token数")
    private Integer totalTokens;

    /**
     * 已耗时（毫秒）
     */
    @Schema(description = "已耗时（毫秒）")
    private Long elapsedMs;

    /**
     * 响应时间（毫秒）
     */
    @Schema(description = "响应时间（毫秒）")
    private Integer responseTimeMs;

    /**
     * 成本（USD）
     */
    @Schema(description = "成本（USD）")
    private Double cost;

    /**
     * 是否完成
     */
    @Schema(description = "是否完成")
    private Boolean done;

    /**
     * 错误信息（仅在发生错误时有值）
     */
    @Schema(description = "错误信息")
    private String error;

    /**
     * 是否发生错误
     */
    @Schema(description = "是否发生错误")
    private Boolean hasError;

    /**
     * 思考过程（thinking模式）
     */
    @Schema(description = "思考过程")
    private String reasoning;

    /**
     * 是否有思考过程
     */
    @Schema(description = "是否有思考过程")
    private Boolean hasReasoning;

    /**
     * 思考时间（秒）
     */
    @Schema(description = "思考时间（秒）")
    private Integer thinkingTime;

    /**
     * 消息索引（用于评分）
     */
    @Schema(description = "消息索引")
    private Integer messageIndex;

    private static final long serialVersionUID = 1L;
}

