package com.yupi.template.model.vo;

import lombok.Data;

import java.io.Serializable;
import java.util.List;

/**
 * 提示词模板视图对象
 *
 * @author <a href="https://codefather.cn">编程导航学习圈</a>
 */
@Data
public class PromptTemplateVO implements Serializable {

    /**
     * 模板ID
     */
    private String id;

    /**
     * 模板名称
     */
    private String name;

    /**
     * 模板描述
     */
    private String description;

    /**
     * 策略类型: direct/cot/role_play/few_shot
     */
    private String strategy;

    /**
     * 策略类型显示名称
     */
    private String strategyName;

    /**
     * 模板内容
     */
    private String content;

    /**
     * 变量列表
     */
    private List<String> variables;

    /**
     * 分类
     */
    private String category;

    /**
     * 是否为预设模板
     */
    private Boolean isPreset;

    /**
     * 使用次数
     */
    private Integer usageCount;

    /**
     * 是否启用
     */
    private Boolean isActive;

    /**
     * 创建时间
     */
    private String createTime;

    private static final long serialVersionUID = 1L;
}
