package com.yupi.template.model.entity;

import com.mybatisflex.annotation.Column;
import com.mybatisflex.annotation.Id;
import com.mybatisflex.annotation.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 批量测试任务实体
 *
 * @author <a href="https://codefather.cn">编程导航学习圈</a>
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Table("test_task")
public class TestTask implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 任务唯一标识
     */
    @Id
    private String id;

    /**
     * 用户ID
     */
    @Column("userId")
    private Long userId;

    /**
     * 任务名称
     */
    @Column("name")
    private String name;

    /**
     * 场景ID
     */
    @Column("sceneId")
    private String sceneId;

    /**
     * 测试的模型列表(JSON格式)
     */
    @Column("models")
    private String models;

    /**
     * 任务配置参数(JSON格式，包含temperature、topP等)
     */
    @Column("config")
    private String config;

    /**
     * 状态: pending/running/completed/failed/cancelled
     */
    @Column("status")
    private String status;

    /**
     * 子任务总数
     */
    @Column("totalSubtasks")
    private Integer totalSubtasks;

    /**
     * 已完成子任务数
     */
    @Column("completedSubtasks")
    private Integer completedSubtasks;

    /**
     * 开始时间
     */
    @Column("startedAt")
    private LocalDateTime startedAt;

    /**
     * 完成时间
     */
    @Column("completedAt")
    private LocalDateTime completedAt;

    /**
     * 创建时间
     */
    @Column("createTime")
    private LocalDateTime createTime;

    /**
     * 更新时间
     */
    @Column("updateTime")
    private LocalDateTime updateTime;

    /**
     * 是否删除
     */
    @Column(value = "isDelete", isLogicDelete = true)
    private Integer isDelete;
}

