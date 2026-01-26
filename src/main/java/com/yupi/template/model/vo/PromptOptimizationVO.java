package com.yupi.template.model.vo;

import lombok.Data;

import java.io.Serializable;
import java.util.List;

/**
 * 提示词优化结果视图对象
 *
 * @author <a href="https://codefather.cn">编程导航学习圈</a>
 */
@Data
public class PromptOptimizationVO implements Serializable {

    /**
     * 当前提示词存在的问题列表
     */
    private List<String> issues;

    /**
     * 优化后的完整提示词
     */
    private String optimizedPrompt;

    /**
     * 改进点列表，说明优化带来的提升
     */
    private List<String> improvements;

    private static final long serialVersionUID = 1L;
}
