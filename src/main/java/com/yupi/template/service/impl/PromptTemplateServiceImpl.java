package com.yupi.template.service.impl;

import cn.hutool.core.util.IdUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONUtil;
import com.mybatisflex.core.query.QueryWrapper;
import com.mybatisflex.spring.service.impl.ServiceImpl;
import com.yupi.template.exception.BusinessException;
import com.yupi.template.exception.ErrorCode;
import com.yupi.template.mapper.PromptTemplateMapper;
import com.yupi.template.model.entity.PromptTemplate;
import com.yupi.template.model.vo.PromptTemplateVO;
import com.yupi.template.service.PromptTemplateService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 提示词模板服务实现
 *
 * @author <a href="https://codefather.cn">编程导航学习圈</a>
 */
@Service
@Slf4j
public class PromptTemplateServiceImpl extends ServiceImpl<PromptTemplateMapper, PromptTemplate> implements PromptTemplateService {

    private static final Map<String, String> STRATEGY_NAME_MAP = new HashMap<>();

    static {
        STRATEGY_NAME_MAP.put("direct", "直接提问");
        STRATEGY_NAME_MAP.put("cot", "CoT (思维链)");
        STRATEGY_NAME_MAP.put("role_play", "角色扮演");
        STRATEGY_NAME_MAP.put("few_shot", "Few-shot (示例学习)");
    }

    @Override
    public List<PromptTemplateVO> listTemplates(Long userId, String strategy) {
        QueryWrapper queryWrapper = QueryWrapper.create()
                .where("isDelete = 0")
                .and("isActive = 1");

        if (StrUtil.isNotBlank(strategy)) {
            queryWrapper.and("strategy = ?", strategy);
        }

        queryWrapper.and("(isPreset = 1 OR userId = ?)", userId)
                .orderBy("isPreset", false)
                .orderBy("usageCount", false)
                .orderBy("createTime", false);

        List<PromptTemplate> templates = this.mapper.selectListByQuery(queryWrapper);
        return templates.stream()
                .map(this::convertToVO)
                .collect(Collectors.toList());
    }

    @Override
    public PromptTemplateVO getTemplateById(String templateId, Long userId) {
        if (StrUtil.isBlank(templateId)) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "模板ID不能为空");
        }

        PromptTemplate template = this.getById(templateId);
        if (template == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND_ERROR, "模板不存在");
        }

        if (template.getIsPreset() == 0 && !template.getUserId().equals(userId)) {
            throw new BusinessException(ErrorCode.NO_AUTH_ERROR, "无权限访问");
        }

        return convertToVO(template);
    }

    @Override
    public String createTemplate(PromptTemplate template, Long userId) {
        if (StrUtil.isBlank(template.getName())) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "模板名称不能为空");
        }
        if (StrUtil.isBlank(template.getStrategy())) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "策略类型不能为空");
        }
        if (StrUtil.isBlank(template.getContent())) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "模板内容不能为空");
        }

        String templateId = IdUtil.randomUUID();
        template.setId(templateId);
        template.setUserId(userId);
        template.setIsPreset(0);
        template.setUsageCount(0);
        template.setIsActive(1);
        template.setCreateTime(LocalDateTime.now());
        template.setUpdateTime(LocalDateTime.now());
        template.setIsDelete(0);

        boolean result = this.save(template);
        if (!result) {
            throw new BusinessException(ErrorCode.OPERATION_ERROR, "创建模板失败");
        }

        return templateId;
    }

    @Override
    public boolean updateTemplate(PromptTemplate template, Long userId) {
        if (StrUtil.isBlank(template.getId())) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "模板ID不能为空");
        }

        PromptTemplate existingTemplate = this.getById(template.getId());
        if (existingTemplate == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND_ERROR, "模板不存在");
        }

        if (existingTemplate.getIsPreset() == 1) {
            throw new BusinessException(ErrorCode.NO_AUTH_ERROR, "预设模板不能修改");
        }

        if (!existingTemplate.getUserId().equals(userId)) {
            throw new BusinessException(ErrorCode.NO_AUTH_ERROR, "无权限操作");
        }

        template.setUpdateTime(LocalDateTime.now());
        return this.updateById(template);
    }

    @Override
    public boolean deleteTemplate(String templateId, Long userId) {
        if (StrUtil.isBlank(templateId)) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "模板ID不能为空");
        }

        PromptTemplate template = this.getById(templateId);
        if (template == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND_ERROR, "模板不存在");
        }

        if (template.getIsPreset() == 1) {
            throw new BusinessException(ErrorCode.NO_AUTH_ERROR, "预设模板不能删除");
        }

        if (!template.getUserId().equals(userId)) {
            throw new BusinessException(ErrorCode.NO_AUTH_ERROR, "无权限操作");
        }

        return this.removeById(templateId);
    }

    @Override
    public boolean incrementUsageCount(String templateId) {
        PromptTemplate template = this.getById(templateId);
        if (template == null) {
            return false;
        }

        template.setUsageCount((template.getUsageCount() == null ? 0 : template.getUsageCount()) + 1);
        template.setUpdateTime(LocalDateTime.now());
        return this.updateById(template);
    }

    private PromptTemplateVO convertToVO(PromptTemplate template) {
        PromptTemplateVO vo = new PromptTemplateVO();
        vo.setId(template.getId());
        vo.setName(template.getName());
        vo.setDescription(template.getDescription());
        vo.setStrategy(template.getStrategy());
        vo.setStrategyName(STRATEGY_NAME_MAP.getOrDefault(template.getStrategy(), template.getStrategy()));
        vo.setContent(template.getContent());
        vo.setCategory(template.getCategory());
        vo.setIsPreset(template.getIsPreset() == 1);
        vo.setUsageCount(template.getUsageCount());
        vo.setIsActive(template.getIsActive() == 1);

        if (template.getCreateTime() != null) {
            vo.setCreateTime(template.getCreateTime().toString());
        }

        if (StrUtil.isNotBlank(template.getVariables())) {
            try {
                List<String> variables = JSONUtil.toList(template.getVariables(), String.class);
                vo.setVariables(variables);
            } catch (Exception e) {
                log.warn("解析模板变量失败: {}", template.getVariables(), e);
                vo.setVariables(new ArrayList<>());
            }
        } else {
            vo.setVariables(new ArrayList<>());
        }

        return vo;
    }
}
