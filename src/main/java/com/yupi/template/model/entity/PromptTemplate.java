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
 * 提示词模板实体
 *
 * @author <a href="https://codefather.cn">编程导航学习圈</a>
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Table("prompt_template")
public class PromptTemplate implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 模板唯一标识
     */
    @Id
    private String id;

    /**
     * 用户ID(预设模板为NULL)
     */
    @Column("userId")
    private Long userId;

    /**
     * 模板名称
     */
    @Column("name")
    private String name;

    /**
     * 模板描述
     */
    @Column("description")
    private String description;

    /**
     * 策略类型: direct/cot/role_play/few_shot
     */
    @Column("strategy")
    private String strategy;

    /**
     * 模板内容(支持占位符)
     */
    @Column("content")
    private String content;

    /**
     * 变量列表(JSON数组)
     */
    @Column("variables")
    private String variables;

    /**
     * 分类
     */
    @Column("category")
    private String category;

    /**
     * 是否为预设模板(1-预设 0-自定义)
     */
    @Column("isPreset")
    private Integer isPreset;

    /**
     * 使用次数
     */
    @Column("usageCount")
    private Integer usageCount;

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
