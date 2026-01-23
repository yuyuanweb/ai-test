package com.yupi.template.model.vo;

import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 评分视图对象
 *
 * @author <a href="https://codefather.cn">编程导航学习圈</a>
 */
@Data
public class RatingVO implements Serializable {

    /**
     * 评分唯一标识
     */
    private String id;

    /**
     * 对话ID
     */
    private String conversationId;

    /**
     * 消息序号
     */
    private Integer messageIndex;

    /**
     * 用户ID
     */
    private Long userId;

    /**
     * 评分类型
     */
    private String ratingType;

    /**
     * 获胜模型
     */
    private String winnerModel;

    /**
     * 失败模型
     */
    private String loserModel;

    /**
     * 获胜变体索引
     */
    private Integer winnerVariantIndex;

    /**
     * 失败变体索引
     */
    private Integer loserVariantIndex;

    /**
     * 创建时间
     */
    private LocalDateTime createTime;

    private static final long serialVersionUID = 1L;
}

