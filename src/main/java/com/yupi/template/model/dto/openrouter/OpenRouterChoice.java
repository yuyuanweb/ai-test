package com.yupi.template.model.dto.openrouter;

import com.google.gson.annotations.SerializedName;

/**
 * OpenRouter Choice模型
 */
public class OpenRouterChoice {
    
    private Integer index;
    private OpenRouterDelta delta;
    
    @SerializedName("finish_reason")
    private String finishReason;
    
    @SerializedName("native_finish_reason")
    private String nativeFinishReason;
    
    private Object logprobs;

    // Constructors
    public OpenRouterChoice() {}

    // Getters and Setters
    public Integer getIndex() {
        return index;
    }

    public void setIndex(Integer index) {
        this.index = index;
    }

    public OpenRouterDelta getDelta() {
        return delta;
    }

    public void setDelta(OpenRouterDelta delta) {
        this.delta = delta;
    }

    public String getFinishReason() {
        return finishReason;
    }

    public void setFinishReason(String finishReason) {
        this.finishReason = finishReason;
    }

    public String getNativeFinishReason() {
        return nativeFinishReason;
    }

    public void setNativeFinishReason(String nativeFinishReason) {
        this.nativeFinishReason = nativeFinishReason;
    }

    public Object getLogprobs() {
        return logprobs;
    }

    public void setLogprobs(Object logprobs) {
        this.logprobs = logprobs;
    }

    @Override
    public String toString() {
        return "OpenRouterChoice{" +
                "index=" + index +
                ", delta=" + delta +
                ", finishReason='" + finishReason + '\'' +
                ", nativeFinishReason='" + nativeFinishReason + '\'' +
                ", logprobs=" + logprobs +
                '}';
    }
}
