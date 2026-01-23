package com.yupi.template.model.dto.openrouter;

import com.google.gson.annotations.SerializedName;

/**
 * OpenRouter Usage模型
 */
public class OpenRouterUsage {
    
    @SerializedName("prompt_tokens")
    private Integer promptTokens;
    
    @SerializedName("completion_tokens")
    private Integer completionTokens;
    
    @SerializedName("total_tokens")
    private Integer totalTokens;
    
    private Double cost;
    
    @SerializedName("is_byok")
    private Boolean isByok;
    
    @SerializedName("prompt_tokens_details")
    private OpenRouterTokensDetails promptTokensDetails;
    
    @SerializedName("cost_details")
    private OpenRouterCostDetails costDetails;
    
    @SerializedName("completion_tokens_details")
    private OpenRouterCompletionTokensDetails completionTokensDetails;

    // Constructors
    public OpenRouterUsage() {}

    // Getters and Setters
    public Integer getPromptTokens() {
        return promptTokens;
    }

    public void setPromptTokens(Integer promptTokens) {
        this.promptTokens = promptTokens;
    }

    public Integer getCompletionTokens() {
        return completionTokens;
    }

    public void setCompletionTokens(Integer completionTokens) {
        this.completionTokens = completionTokens;
    }

    public Integer getTotalTokens() {
        return totalTokens;
    }

    public void setTotalTokens(Integer totalTokens) {
        this.totalTokens = totalTokens;
    }

    public Double getCost() {
        return cost;
    }

    public void setCost(Double cost) {
        this.cost = cost;
    }

    public Boolean getIsByok() {
        return isByok;
    }

    public void setIsByok(Boolean isByok) {
        this.isByok = isByok;
    }

    public OpenRouterTokensDetails getPromptTokensDetails() {
        return promptTokensDetails;
    }

    public void setPromptTokensDetails(OpenRouterTokensDetails promptTokensDetails) {
        this.promptTokensDetails = promptTokensDetails;
    }

    public OpenRouterCostDetails getCostDetails() {
        return costDetails;
    }

    public void setCostDetails(OpenRouterCostDetails costDetails) {
        this.costDetails = costDetails;
    }

    public OpenRouterCompletionTokensDetails getCompletionTokensDetails() {
        return completionTokensDetails;
    }

    public void setCompletionTokensDetails(OpenRouterCompletionTokensDetails completionTokensDetails) {
        this.completionTokensDetails = completionTokensDetails;
    }

    @Override
    public String toString() {
        return "OpenRouterUsage{" +
                "promptTokens=" + promptTokens +
                ", completionTokens=" + completionTokens +
                ", totalTokens=" + totalTokens +
                ", cost=" + cost +
                ", isByok=" + isByok +
                ", promptTokensDetails=" + promptTokensDetails +
                ", costDetails=" + costDetails +
                ", completionTokensDetails=" + completionTokensDetails +
                '}';
    }
    
    // 内部类定义
    public static class OpenRouterTokensDetails {
        @SerializedName("cached_tokens")
        private Integer cachedTokens;
        
        @SerializedName("audio_tokens")
        private Integer audioTokens;
        
        @SerializedName("video_tokens")
        private Integer videoTokens;

        // Getters and Setters
        public Integer getCachedTokens() {
            return cachedTokens;
        }

        public void setCachedTokens(Integer cachedTokens) {
            this.cachedTokens = cachedTokens;
        }

        public Integer getAudioTokens() {
            return audioTokens;
        }

        public void setAudioTokens(Integer audioTokens) {
            this.audioTokens = audioTokens;
        }

        public Integer getVideoTokens() {
            return videoTokens;
        }

        public void setVideoTokens(Integer videoTokens) {
            this.videoTokens = videoTokens;
        }
    }
    
    public static class OpenRouterCostDetails {
        @SerializedName("upstream_inference_cost")
        private Double upstreamInferenceCost;
        
        @SerializedName("upstream_inference_prompt_cost")
        private Double upstreamInferencePromptCost;
        
        @SerializedName("upstream_inference_completions_cost")
        private Double upstreamInferenceCompletionsCost;

        // Getters and Setters
        public Double getUpstreamInferenceCost() {
            return upstreamInferenceCost;
        }

        public void setUpstreamInferenceCost(Double upstreamInferenceCost) {
            this.upstreamInferenceCost = upstreamInferenceCost;
        }

        public Double getUpstreamInferencePromptCost() {
            return upstreamInferencePromptCost;
        }

        public void setUpstreamInferencePromptCost(Double upstreamInferencePromptCost) {
            this.upstreamInferencePromptCost = upstreamInferencePromptCost;
        }

        public Double getUpstreamInferenceCompletionsCost() {
            return upstreamInferenceCompletionsCost;
        }

        public void setUpstreamInferenceCompletionsCost(Double upstreamInferenceCompletionsCost) {
            this.upstreamInferenceCompletionsCost = upstreamInferenceCompletionsCost;
        }
    }
    
    public static class OpenRouterCompletionTokensDetails {
        @SerializedName("reasoning_tokens")
        private Integer reasoningTokens;
        
        @SerializedName("image_tokens")
        private Integer imageTokens;

        // Getters and Setters
        public Integer getReasoningTokens() {
            return reasoningTokens;
        }

        public void setReasoningTokens(Integer reasoningTokens) {
            this.reasoningTokens = reasoningTokens;
        }

        public Integer getImageTokens() {
            return imageTokens;
        }

        public void setImageTokens(Integer imageTokens) {
            this.imageTokens = imageTokens;
        }
    }
}
