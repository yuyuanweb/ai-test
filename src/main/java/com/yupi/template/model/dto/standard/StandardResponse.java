package com.yupi.template.model.dto.standard;

import com.google.gson.annotations.SerializedName;

import java.util.List;

/**
 * 标准响应模型 - 转换后的格式
 */
public class StandardResponse {
    
    private String id;
    private String model;
    private String object;
    private Long created;
    
    @SerializedName("choices")
    private List<StandardChoice> choices;
    
    @SerializedName("system_fingerprint")
    private String systemFingerprint;
    
    private StandardUsage usage;

    // Constructors
    public StandardResponse() {}

    // Getters and Setters
    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
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

    public List<StandardChoice> getChoices() {
        return choices;
    }

    public void setChoices(List<StandardChoice> choices) {
        this.choices = choices;
    }

    public String getSystemFingerprint() {
        return systemFingerprint;
    }

    public void setSystemFingerprint(String systemFingerprint) {
        this.systemFingerprint = systemFingerprint;
    }

    public StandardUsage getUsage() {
        return usage;
    }

    public void setUsage(StandardUsage usage) {
        this.usage = usage;
    }

    @Override
    public String toString() {
        return "StandardResponse{" +
                "id='" + id + '\'' +
                ", model='" + model + '\'' +
                ", object='" + object + '\'' +
                ", created=" + created +
                ", choices=" + choices +
                ", systemFingerprint='" + systemFingerprint + '\'' +
                ", usage=" + usage +
                '}';
    }
}
