package com.yupi.template.model.dto.standard;

import com.google.gson.annotations.SerializedName;

/**
 * 标准Choice模型
 */
public class StandardChoice {
    
    private Integer index;
    private StandardDelta delta;
    
    @SerializedName("finish_reason")
    private String finishReason;
    
    private Object logprobs;

    // Constructors
    public StandardChoice() {}

    // Getters and Setters
    public Integer getIndex() {
        return index;
    }

    public void setIndex(Integer index) {
        this.index = index;
    }

    public StandardDelta getDelta() {
        return delta;
    }

    public void setDelta(StandardDelta delta) {
        this.delta = delta;
    }

    public String getFinishReason() {
        return finishReason;
    }

    public void setFinishReason(String finishReason) {
        this.finishReason = finishReason;
    }

    public Object getLogprobs() {
        return logprobs;
    }

    public void setLogprobs(Object logprobs) {
        this.logprobs = logprobs;
    }

    @Override
    public String toString() {
        return "StandardChoice{" +
                "index=" + index +
                ", delta=" + delta +
                ", finishReason='" + finishReason + '\'' +
                ", logprobs=" + logprobs +
                '}';
    }
}
