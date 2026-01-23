package com.yupi.template.model.dto.standard;

import com.google.gson.annotations.SerializedName;

/**
 * 标准Usage模型
 */
public class StandardUsage {
    
    @SerializedName("prompt_tokens")
    private Integer promptTokens;
    
    @SerializedName("completion_tokens")
    private Integer completionTokens;
    
    @SerializedName("total_tokens")
    private Integer totalTokens;
    
    private Double cost;
    
    @SerializedName("is_byok")
    private Boolean isByok;

    // Constructors
    public StandardUsage() {}

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

    @Override
    public String toString() {
        return "StandardUsage{" +
                "promptTokens=" + promptTokens +
                ", completionTokens=" + completionTokens +
                ", totalTokens=" + totalTokens +
                ", cost=" + cost +
                ", isByok=" + isByok +
                '}';
    }
}
