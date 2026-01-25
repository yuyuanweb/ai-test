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
 * 场景提示词实体
 *
 * @author <a href="https://codefather.cn">编程导航学习圈</a>
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Table("scene_prompt")
public class ScenePrompt implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 提示词唯一标识
     */
    @Id
    private String id;

    /**
     * 场景ID
     */
    @Column("sceneId")
    private String sceneId;

    /**
     * 用户ID
     */
    @Column("userId")
    private Long userId;

    /**
     * 提示词序号
     */
    @Column("promptIndex")
    private Integer promptIndex;

    /**
     * 提示词标题
     */
    @Column("title")
    private String title;

    /**
     * 提示词内容
     */
    @Column("content")
    private String content;

    /**
     * 难度: easy/medium/hard
     */
    @Column("difficulty")
    private String difficulty;

    /**
     * 标签数组(JSON格式)
     */
    @Column("tags")
    private String tags;

    /**
     * 期望输出(可选)
     */
    @Column("expectedOutput")
    private String expectedOutput;

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

