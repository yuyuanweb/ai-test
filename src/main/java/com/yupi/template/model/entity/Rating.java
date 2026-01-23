package com.yupi.template.model.entity;

import com.mybatisflex.annotation.Column;
import com.mybatisflex.annotation.Id;
import com.mybatisflex.annotation.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 用户评分实体
 *
 * @author <a href="https://codefather.cn">编程导航学习圈</a>
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Table("rating")
public class Rating implements Serializable {

    /**
     * 评分唯一标识
     */
    @Id
    private String id;

    /**
     * 对话ID
     */
    @Column("conversationId")
    private String conversationId;

    /**
     * 消息序号(对应某一轮对话)
     */
    @Column("messageIndex")
    private Integer messageIndex;

    /**
     * 用户ID
     */
    @Column("userId")
    private Long userId;

    /**
     * 评分类型: left_better/right_better/tie/both_bad/variant_N
     */
    @Column("ratingType")
    private String ratingType;

    /**
     * 获胜模型
     */
    @Column("winnerModel")
    private String winnerModel;

    /**
     * 失败模型
     */
    @Column("loserModel")
    private String loserModel;

    /**
     * 获胜变体索引(用于prompt_lab)
     */
    @Column("winnerVariantIndex")
    private Integer winnerVariantIndex;

    /**
     * 失败变体索引(用于prompt_lab)
     */
    @Column("loserVariantIndex")
    private Integer loserVariantIndex;

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

