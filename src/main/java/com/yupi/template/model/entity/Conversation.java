package com.yupi.template.model.entity;

import com.mybatisflex.annotation.Column;
import com.mybatisflex.annotation.Id;
import com.mybatisflex.annotation.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 对话记录实体
 *
 * @author <a href="https://codefather.cn">编程导航学习圈</a>
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Table("conversation")
public class Conversation implements Serializable {

    /**
     * 对话唯一标识
     */
    @Id
    private String id;

    /**
     * 用户ID
     */
    @Column("userId")
    private Long userId;

    /**
     * 对话标题
     */
    @Column("title")
    private String title;

    /**
     * 对话类型: side_by_side/prompt_lab
     */
    @Column("conversationType")
    private String conversationType;

    /**
     * 是否启用代码预览模式（三栏布局）
     */
    @Column("codePreviewEnabled")
    private Boolean codePreviewEnabled;

    /**
     * 参与的模型列表 (JSON格式)
     */
    @Column("models")
    private String models;

    /**
     * 总Token消耗
     */
    @Column("totalTokens")
    private Integer totalTokens;

    /**
     * 总成本(USD)
     */
    @Column("totalCost")
    private BigDecimal totalCost;

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
    @Column("isDelete")
    private Integer isDelete;

    private static final long serialVersionUID = 1L;
}