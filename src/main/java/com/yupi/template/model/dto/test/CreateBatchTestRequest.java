package com.yupi.template.model.dto.test;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.io.Serializable;
import java.util.List;

/**
 * 创建批量测试任务请求
 *
 * @author <a href="https://codefather.cn">编程导航学习圈</a>
 */
@Data
@Schema(description = "创建批量测试任务请求")
public class CreateBatchTestRequest implements Serializable {

    /**
     * 任务名称
     */
    @Schema(description = "任务名称")
    private String name;

    /**
     * 场景ID
     */
    @Schema(description = "场景ID", required = true)
    private String sceneId;

    /**
     * 测试的模型列表
     */
    @Schema(description = "测试的模型列表", required = true)
    private List<String> models;

    /**
     * 温度参数 (0.0-2.0)
     */
    @Schema(description = "温度参数", example = "0.7")
    private Float temperature;

    /**
     * Top P 参数 (0.0-1.0)
     */
    @Schema(description = "Top P 参数", example = "1.0")
    private Float topP;

    /**
     * 最大Token数
     */
    @Schema(description = "最大Token数", example = "4096")
    private Integer maxTokens;

    /**
     * Top K 参数
     */
    @Schema(description = "Top K 参数")
    private Integer topK;

    /**
     * Frequency Penalty 参数 (-2.0-2.0)
     */
    @Schema(description = "Frequency Penalty 参数", example = "0.0")
    private Float frequencyPenalty;

    /**
     * Presence Penalty 参数 (-2.0-2.0)
     */
    @Schema(description = "Presence Penalty 参数", example = "0.0")
    private Float presencePenalty;

    /**
     * 是否启用AI评分
     */
    @Schema(description = "是否启用AI评分", example = "false")
    private Boolean enableAiScoring;

    private static final long serialVersionUID = 1L;
}

