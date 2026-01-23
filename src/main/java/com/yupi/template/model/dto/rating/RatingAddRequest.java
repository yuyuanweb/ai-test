package com.yupi.template.model.dto.rating;

import lombok.Data;

import java.io.Serializable;

/**
 * 添加评分请求
 *
 * @author <a href="https://codefather.cn">编程导航学习圈</a>
 */
@Data
public class RatingAddRequest implements Serializable {

    /**
     * 对话ID
     */
    private String conversationId;

    /**
     * 消息序号
     */
    private Integer messageIndex;

    /**
     * 评分类型: left_better/right_better/tie/both_bad/variant_N
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
     * 获胜变体索引(用于prompt_lab)
     */
    private Integer winnerVariantIndex;

    /**
     * 失败变体索引(用于prompt_lab)
     */
    private Integer loserVariantIndex;

    private static final long serialVersionUID = 1L;
}

