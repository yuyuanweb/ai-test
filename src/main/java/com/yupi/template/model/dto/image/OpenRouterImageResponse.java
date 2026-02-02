package com.yupi.template.model.dto.image;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

import java.io.Serializable;
import java.util.List;

/**
 * OpenRouter 图片生成响应
 *
 * @author <a href="https://codefather.cn">编程导航学习圈</a>
 */
@Data
public class OpenRouterImageResponse implements Serializable {

    private String id;

    private Long created;

    private String model;

    private List<OpenRouterImageResponse.OpenRouterChoice> choices;

    private OpenRouterImageResponse.OpenRouterUsage usage;

    /**
     * OpenRouter 选择项
     */
    @Data
    public static class OpenRouterChoice implements Serializable {

        @JsonProperty("finish_reason")
        private String finishReason;

        private OpenRouterImageResponse.OpenRouterMessage message;
    }

    /**
     * OpenRouter 消息
     */
    @Data
    public static class OpenRouterMessage implements Serializable {

        private String role;

        /**
         * 对图片生成场景，文本说明
         */
        private String content;

        /**
         * 图片列表
         */
        private List<OpenRouterImageResponse.OpenRouterImagePart> images;
    }

    /**
     * OpenRouter 图片部分
     */
    @Data
    public static class OpenRouterImagePart implements Serializable {

        private String type;

        @JsonProperty("image_url")
        private OpenRouterImageUrl imageUrl;
    }

    /**
     * OpenRouter 图片 URL
     */
    @Data
    public static class OpenRouterImageUrl implements Serializable {

        private String url;
    }

    /**
     * OpenRouter 使用统计
     */
    @Data
    public static class OpenRouterUsage implements Serializable {

        @JsonProperty("prompt_tokens")
        private Integer promptTokens;

        @JsonProperty("completion_tokens")
        private Integer completionTokens;

        @JsonProperty("total_tokens")
        private Integer totalTokens;

        /**
         * OpenRouter 可能返回的费用（credits / usd）
         */
        private Double cost;
    }
}
