package com.yupi.template.service;

import com.yupi.template.model.entity.PromptTemplate;
import com.yupi.template.model.vo.PromptTemplateVO;

import java.util.List;

/**
 * 提示词模板服务接口
 *
 * @author <a href="https://codefather.cn">编程导航学习圈</a>
 */
public interface PromptTemplateService {

    /**
     * 获取所有模板（预设+用户自定义）
     *
     * @param userId 用户ID
     * @param strategy 策略类型（可选）
     * @return 模板列表
     */
    List<PromptTemplateVO> listTemplates(Long userId, String strategy);

    /**
     * 根据ID获取模板
     *
     * @param templateId 模板ID
     * @param userId 用户ID
     * @return 模板VO
     */
    PromptTemplateVO getTemplateById(String templateId, Long userId);

    /**
     * 创建模板
     *
     * @param template 模板实体
     * @param userId 用户ID
     * @return 模板ID
     */
    String createTemplate(PromptTemplate template, Long userId);

    /**
     * 更新模板
     *
     * @param template 模板实体
     * @param userId 用户ID
     * @return 是否成功
     */
    boolean updateTemplate(PromptTemplate template, Long userId);

    /**
     * 删除模板
     *
     * @param templateId 模板ID
     * @param userId 用户ID
     * @return 是否成功
     */
    boolean deleteTemplate(String templateId, Long userId);

    /**
     * 增加使用次数
     *
     * @param templateId 模板ID
     * @return 是否成功
     */
    boolean incrementUsageCount(String templateId);
}
