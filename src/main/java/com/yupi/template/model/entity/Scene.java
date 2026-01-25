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
 * 场景实体
 *
 * @author <a href="https://codefather.cn">编程导航学习圈</a>
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Table("scene")
public class Scene implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 场景唯一标识
     */
    @Id
    private String id;

    /**
     * 创建用户ID(预设场景为NULL)
     */
    @Column("userId")
    private Long userId;

    /**
     * 场景名称
     */
    @Column("name")
    private String name;

    /**
     * 场景描述
     */
    @Column("description")
    private String description;

    /**
     * 分类:编程/数学/文案等
     */
    @Column("category")
    private String category;

    /**
     * 是否为预设场景(1-预设 0-自定义)
     */
    @Column("isPreset")
    private Integer isPreset;

    /**
     * 是否启用(1-启用 0-禁用)
     */
    @Column("isActive")
    private Integer isActive;

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

