package com.yupi.template.model.dto.standard;

import com.google.gson.annotations.SerializedName;

/**
 * 标准Delta模型 - 包含转换后的reasoning_content字段
 */
public class StandardDelta {
    
    private String role;
    private String content;
    
    // 转换后的推理内容字段
    @SerializedName("reasoning_content")
    private String reasoningContent;

    // Constructors
    public StandardDelta() {}

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

    public String getReasoningContent() {
        return reasoningContent;
    }

    public void setReasoningContent(String reasoningContent) {
        this.reasoningContent = reasoningContent;
    }

    @Override
    public String toString() {
        return "StandardDelta{" +
                "role='" + role + '\'' +
                ", content='" + content + '\'' +
                ", reasoningContent='" + reasoningContent + '\'' +
                '}';
    }
}
