package com.yupi.template.model.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.math.BigDecimal;

/**
 * 模型视图对象
 *
 * @author <a href="https://codefather.cn">编程导航学习圈</a>
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "模型信息")
public class ModelVO implements Serializable {

    /**
     * 模型ID (OpenRouter格式，如: openai/gpt-4o)
     */
    @Schema(description = "模型ID", example = "openai/gpt-4o")
    private String id;

    /**
     * 模型显示名称
     */
    @Schema(description = "模型显示名称", example = "GPT-4o")
    private String name;

    /**
     * 模型描述
     */
    @Schema(description = "模型描述")
    private String description;

    /**
     * 上下文长度 (tokens)
     */
    @Schema(description = "上下文长度(tokens)", example = "128000")
    private Integer contextLength;

    /**
     * 输入价格 (每百万tokens，美元)
     */
    @Schema(description = "输入价格(每百万tokens，美元)", example = "2.50")
    private BigDecimal inputPrice;

    /**
     * 输出价格 (每百万tokens，美元)
     */
    @Schema(description = "输出价格(每百万tokens，美元)", example = "10.00")
    private BigDecimal outputPrice;

    /**
     * 提供商 (如: OpenAI, Anthropic)
     */
    @Schema(description = "提供商", example = "OpenAI")
    private String provider;

    /**
     * 是否推荐
     */
    @Schema(description = "是否推荐", example = "true")
    private Boolean recommended;

    /**
     * 是否国内模型
     */
    @Schema(description = "是否国内模型", example = "true")
    private Boolean isChina;

    /**
     * 是否支持多模态(图片)
     */
    @Schema(description = "是否支持多模态(图片)", example = "true")
    private Boolean supportsMultimodal;

    /**
     * 是否支持图片生成
     */
    @Schema(description = "是否支持图片生成", example = "true")
    private Boolean supportsImageGen;

    /**
     * 是否支持工具/函数调用（用于开启联网搜索等）
     */
    @Schema(description = "是否支持工具/函数调用", example = "true")
    private Boolean supportsToolCalling;

    /**
     * 标签 (如: 代码, 文本生成, 多模态等)
     */
    @Schema(description = "能力标签", example = "[\"代码\", \"文本生成\"]")
    private String[] tags;

    /**
     * 累计使用Token数
     */
    @Schema(description = "累计使用Token数", example = "1000000")
    private Long totalTokens;

    /**
     * 累计花费（美元）
     */
    @Schema(description = "累计花费（美元）", example = "10.50")
    private BigDecimal totalCost;

    /**
     * 用户累计使用Token数
     */
    @Schema(description = "用户累计使用Token数", example = "50000")
    private Long userTotalTokens;

    /**
     * 用户累计花费（美元）
     */
    @Schema(description = "用户累计花费（美元）", example = "2.50")
    private BigDecimal userTotalCost;

    private static final long serialVersionUID = 1L;
}

