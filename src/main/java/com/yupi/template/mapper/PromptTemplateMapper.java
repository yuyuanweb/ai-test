package com.yupi.template.mapper;

import com.mybatisflex.core.BaseMapper;
import com.yupi.template.model.entity.PromptTemplate;
import org.apache.ibatis.annotations.Mapper;

/**
 * 提示词模板 Mapper 接口
 *
 * @author <a href="https://codefather.cn">编程导航学习圈</a>
 */
@Mapper
public interface PromptTemplateMapper extends BaseMapper<PromptTemplate> {
}
