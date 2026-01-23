package com.yupi.template.model.dto.openrouter;

/**
 * OpenRouter Reasoning Detail模型
 */
public class OpenRouterReasoningDetail {
    
    private String type;
    private String text;
    private Integer index;
    private String format;

    // Constructors
    public OpenRouterReasoningDetail() {}

    // Getters and Setters
    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    public String getText() {
        return text;
    }

    public void setText(String text) {
        this.text = text;
    }

    public Integer getIndex() {
        return index;
    }

    public void setIndex(Integer index) {
        this.index = index;
    }

    public String getFormat() {
        return format;
    }

    public void setFormat(String format) {
        this.format = format;
    }

    @Override
    public String toString() {
        return "OpenRouterReasoningDetail{" +
                "type='" + type + '\'' +
                ", text='" + text + '\'' +
                ", index=" + index +
                ", format='" + format + '\'' +
                '}';
    }
}
