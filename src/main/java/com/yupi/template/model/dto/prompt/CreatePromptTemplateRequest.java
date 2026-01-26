package com.yupi.template.model.dto.prompt;

import lombok.Data;

import java.io.Serializable;
import java.util.List;

/**
 * 创建提示词模板请求
 *
 * @author <a href="https://codefather.cn">编程导航学习圈</a>
 */
@Data
public class CreatePromptTemplateRequest implements Serializable {

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

    private static final long serialVersionUID = 1L;
}
