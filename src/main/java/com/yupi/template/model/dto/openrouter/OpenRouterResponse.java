package com.yupi.template.model.dto.openrouter;

import com.google.gson.annotations.SerializedName;

import java.util.List;

/**
 * OpenRouter API响应模型
 */
public class OpenRouterResponse {
    
    private String id;
    private String provider;
    private String model;
    private String object;
    private Long created;
    
    @SerializedName("choices")
    private List<OpenRouterChoice> choices;
    
    @SerializedName("system_fingerprint")
    private String systemFingerprint;
    
    private OpenRouterUsage usage;

    // Constructors
    public OpenRouterResponse() {}

    // Getters and Setters
    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getProvider() {
        return provider;
    }

    public void setProvider(String provider) {
        this.provider = provider;
    }

    public String getModel() {
        return model;
    }

    public void setModel(String model) {
        this.model = model;
    }

    public String getObject() {
        return object;
    }

    public void setObject(String object) {
        this.object = object;
    }

    public Long getCreated() {
        return created;
    }

    public void setCreated(Long created) {
        this.created = created;
    }

    public List<OpenRouterChoice> getChoices() {
        return choices;
    }

    public void setChoices(List<OpenRouterChoice> choices) {
        this.choices = choices;
    }

    public String getSystemFingerprint() {
        return systemFingerprint;
    }

    public void setSystemFingerprint(String systemFingerprint) {
        this.systemFingerprint = systemFingerprint;
    }

    public OpenRouterUsage getUsage() {
        return usage;
    }

    public void setUsage(OpenRouterUsage usage) {
        this.usage = usage;
    }

    @Override
    public String toString() {
        return "OpenRouterResponse{" +
                "id='" + id + '\'' +
                ", provider='" + provider + '\'' +
                ", model='" + model + '\'' +
                ", object='" + object + '\'' +
                ", created=" + created +
                ", choices=" + choices +
                ", systemFingerprint='" + systemFingerprint + '\'' +
                ", usage=" + usage +
                '}';
    }
}
