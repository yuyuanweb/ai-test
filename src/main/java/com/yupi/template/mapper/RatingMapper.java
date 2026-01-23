package com.yupi.template.mapper;

import com.mybatisflex.core.BaseMapper;
import com.yupi.template.model.entity.Rating;
import org.apache.ibatis.annotations.Mapper;

/**
 * 用户评分Mapper
 *
 * @author <a href="https://codefather.cn">编程导航学习圈</a>
 */
@Mapper
public interface RatingMapper extends BaseMapper<Rating> {
}

