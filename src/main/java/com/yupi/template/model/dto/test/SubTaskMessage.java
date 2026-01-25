package com.yupi.template.model.dto.test;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

/**
 * 子任务消息（用于RabbitMQ）
 *
 * @author <a href="https://codefather.cn">编程导航学习圈</a>
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SubTaskMessage implements Serializable {

    /**
     * 任务ID
     */
    private String taskId;

    /**
     * 场景ID
     */
    private String sceneId;

    /**
     * 提示词ID
     */
    private String promptId;

    /**
     * 提示词标题
     */
    private String promptTitle;

    /**
     * 提示词内容
     */
    private String promptContent;

    /**
     * 模型名称
     */
    private String modelName;

    /**
     * 用户ID
     */
    private Long userId;

    private static final long serialVersionUID = 1L;
}

