package com.yupi.template.model.dto.openrouter;

import com.google.gson.annotations.SerializedName;

import java.util.List;

/**
 * OpenRouter Delta模型 - 包含DeepSeek R1的reasoning字段
 */
public class OpenRouterDelta {
    
    private String role;
    private String content;
    
    // DeepSeek R1特有的推理字段
    private String reasoning;
    
    @SerializedName("reasoning_details")
    private List<OpenRouterReasoningDetail> reasoningDetails;

    // Constructors
    public OpenRouterDelta() {}

    // Getters and Setters
    public String getRole() {
        return role;
    }

    public void setRole(String role) {
        this.role = role;
    }

    public String getContent() {
        return content;
    }

    public void setContent(String content) {
        this.content = content;
    }

    public String getReasoning() {
        return reasoning;
    }

    public void setReasoning(String reasoning) {
        this.reasoning = reasoning;
    }

    public List<OpenRouterReasoningDetail> getReasoningDetails() {
        return reasoningDetails;
    }

    public void setReasoningDetails(List<OpenRouterReasoningDetail> reasoningDetails) {
        this.reasoningDetails = reasoningDetails;
    }

    @Override
    public String toString() {
        return "OpenRouterDelta{" +
                "role='" + role + '\'' +
                ", content='" + content + '\'' +
                ", reasoning='" + reasoning + '\'' +
                ", reasoningDetails=" + reasoningDetails +
                '}';
    }
}
