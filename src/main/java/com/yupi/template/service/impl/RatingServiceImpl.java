package com.yupi.template.service.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.util.IdUtil;
import com.mybatisflex.core.query.QueryWrapper;
import com.yupi.template.mapper.RatingMapper;
import com.yupi.template.model.entity.Rating;
import com.yupi.template.model.vo.RatingVO;
import com.yupi.template.service.RatingService;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 评分服务实现
 *
 * @author <a href="https://codefather.cn">编程导航学习圈</a>
 */
@Service
public class RatingServiceImpl implements RatingService {

    @Resource
    private RatingMapper ratingMapper;

    @Override
    public boolean saveOrUpdateRating(Rating rating) {
        // 查询是否已经存在评分
        QueryWrapper queryWrapper = QueryWrapper.create()
                .from(Rating.class)
                .where("conversationId = ? AND messageIndex = ? AND userId = ? AND isDelete = 0",
                        rating.getConversationId(), rating.getMessageIndex(), rating.getUserId());

        Rating existingRating = ratingMapper.selectOneByQuery(queryWrapper);

        if (existingRating != null) {
            // 更新已有评分
            existingRating.setRatingType(rating.getRatingType());
            existingRating.setWinnerModel(rating.getWinnerModel());
            existingRating.setLoserModel(rating.getLoserModel());
            existingRating.setWinnerVariantIndex(rating.getWinnerVariantIndex());
            existingRating.setLoserVariantIndex(rating.getLoserVariantIndex());
            existingRating.setUpdateTime(LocalDateTime.now());
            return ratingMapper.update(existingRating) > 0;
        } else {
            // 新增评分
            rating.setId(IdUtil.getSnowflakeNextIdStr());
            rating.setCreateTime(LocalDateTime.now());
            rating.setUpdateTime(LocalDateTime.now());
            rating.setIsDelete(0);
            return ratingMapper.insert(rating) > 0;
        }
    }

    @Override
    public RatingVO getRating(String conversationId, Integer messageIndex, Long userId) {
        QueryWrapper queryWrapper = QueryWrapper.create()
                .from(Rating.class)
                .where("conversationId = ? AND messageIndex = ? AND userId = ? AND isDelete = 0",
                        conversationId, messageIndex, userId);

        Rating rating = ratingMapper.selectOneByQuery(queryWrapper);
        if (rating == null) {
            return null;
        }

        RatingVO ratingVO = new RatingVO();
        BeanUtil.copyProperties(rating, ratingVO);
        return ratingVO;
    }

    @Override
    public List<RatingVO> getRatingsByConversationId(String conversationId, Long userId) {
        QueryWrapper queryWrapper = QueryWrapper.create()
                .from(Rating.class)
                .where("conversationId = ? AND userId = ? AND isDelete = 0",
                        conversationId, userId)
                .orderBy(Rating::getMessageIndex, true);

        List<Rating> ratings = ratingMapper.selectListByQuery(queryWrapper);
        return ratings.stream()
                .map(rating -> {
                    RatingVO ratingVO = new RatingVO();
                    BeanUtil.copyProperties(rating, ratingVO);
                    return ratingVO;
                })
                .collect(Collectors.toList());
    }

    @Override
    public boolean deleteRating(String conversationId, Integer messageIndex, Long userId) {
        QueryWrapper queryWrapper = QueryWrapper.create()
                .from(Rating.class)
                .where("conversationId = ? AND messageIndex = ? AND userId = ? AND isDelete = 0",
                        conversationId, messageIndex, userId);

        Rating rating = ratingMapper.selectOneByQuery(queryWrapper);
        if (rating == null) {
            return false;
        }

        rating.setIsDelete(1);
        rating.setUpdateTime(LocalDateTime.now());
        return ratingMapper.update(rating) > 0;
    }
}

