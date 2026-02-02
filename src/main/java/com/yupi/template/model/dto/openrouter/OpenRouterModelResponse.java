package com.yupi.template.model.dto.openrouter;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

import java.util.List;

/**
 * OpenRouter模型列表响应
 *
 * @author <a href="https://codefather.cn">编程导航学习圈</a>
 */
@Data
public class OpenRouterModelResponse {

    /**
     * 模型数据列表
     */
    private List<OpenRouterModel> data;

    /**
     * OpenRouter模型
     */
    @Data
    public static class OpenRouterModel {
        /**
         * 模型ID
         */
        private String id;

        /**
         * 模型名称
         */
        private String name;

        /**
         * 创建时间戳
         */
        private Long created;

        /**
         * 模型描述
         */
        private String description;

        /**
         * 上下文长度
         */
        @JsonProperty("context_length")
        private Integer contextLength;

        /**
         * 价格信息
         */
        private Pricing pricing;

        /**
         * 架构信息
         */
        private Architecture architecture;

        /**
         * 支持的参数列表（不同 provider 的并集）
         * 用于判断是否支持 tools / function calling 等能力
         */
        @JsonProperty("supported_parameters")
        private List<String> supportedParameters;
    }

    /**
     * 价格信息
     */
    @Data
    public static class Pricing {
        /**
         * 输入价格（每token，美元）
         */
        private String prompt;

        /**
         * 输出价格（每token，美元）
         */
        private String completion;
    }

    /**
     * 架构信息
     */
    @Data
    public static class Architecture {
        /**
         * 支持的输入模态
         */
        @JsonProperty("input_modalities")
        private List<String> inputModalities;

        /**
         * 支持的输出模态
         */
        @JsonProperty("output_modalities")
        private List<String> outputModalities;
    }
}

