package com.yupi.template.mapper;

import com.mybatisflex.core.BaseMapper;
import com.yupi.template.model.entity.UserModelUsage;
import org.apache.ibatis.annotations.Mapper;

/**
 * 用户-模型使用统计 Mapper
 *
 * @author <a href="https://codefather.cn">编程导航学习圈</a>
 */
@Mapper
public interface UserModelUsageMapper extends BaseMapper<UserModelUsage> {
}
