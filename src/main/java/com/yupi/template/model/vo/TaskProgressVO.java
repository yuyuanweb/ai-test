package com.yupi.template.model.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

/**
 * 任务进度VO
 *
 * @author <a href="https://codefather.cn">编程导航学习圈</a>
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "任务进度")
public class TaskProgressVO implements Serializable {

    /**
     * 任务ID
     */
    @Schema(description = "任务ID")
    private String taskId;

    /**
     * 完成百分比
     */
    @Schema(description = "完成百分比")
    private Integer percentage;

    /**
     * 已完成子任务数
     */
    @Schema(description = "已完成子任务数")
    private Integer completedSubtasks;

    /**
     * 总子任务数
     */
    @Schema(description = "总子任务数")
    private Integer totalSubtasks;

    /**
     * 当前测试的模型名称
     */
    @Schema(description = "当前测试的模型名称")
    private String currentModel;

    /**
     * 当前测试的提示词标题
     */
    @Schema(description = "当前测试的提示词标题")
    private String currentPrompt;

    /**
     * 任务状态: pending/running/completed/failed/cancelled
     */
    @Schema(description = "任务状态")
    private String status;

    /**
     * 时间戳
     */
    @Schema(description = "时间戳")
    private Long timestamp;

    private static final long serialVersionUID = 1L;
}

