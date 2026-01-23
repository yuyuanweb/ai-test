package com.yupi.template.service;

import com.yupi.template.model.entity.Rating;
import com.yupi.template.model.vo.RatingVO;

/**
 * 评分服务接口
 *
 * @author <a href="https://codefather.cn">编程导航学习圈</a>
 */
public interface RatingService {

    /**
     * 添加或更新评分
     *
     * @param rating 评分实体
     * @return 是否成功
     */
    boolean saveOrUpdateRating(Rating rating);

    /**
     * 获取用户对某轮对话的评分
     *
     * @param conversationId 对话ID
     * @param messageIndex 消息序号
     * @param userId 用户ID
     * @return 评分VO
     */
    RatingVO getRating(String conversationId, Integer messageIndex, Long userId);

    /**
     * 删除评分
     *
     * @param conversationId 对话ID
     * @param messageIndex 消息序号
     * @param userId 用户ID
     * @return 是否成功
     */
    boolean deleteRating(String conversationId, Integer messageIndex, Long userId);
}

