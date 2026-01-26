package com.yupi.template.controller;

import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONUtil;
import com.yupi.template.common.BaseResponse;
import com.yupi.template.common.DeleteRequest;
import com.yupi.template.common.ResultUtils;
import com.yupi.template.exception.ErrorCode;
import com.yupi.template.exception.ThrowUtils;
import com.yupi.template.model.dto.prompt.CreatePromptTemplateRequest;
import com.yupi.template.model.dto.prompt.UpdatePromptTemplateRequest;
import com.yupi.template.model.entity.PromptTemplate;
import com.yupi.template.model.entity.User;
import com.yupi.template.model.vo.PromptTemplateVO;
import com.yupi.template.service.PromptTemplateService;
import com.yupi.template.service.UserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Resource;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 提示词模板接口
 *
 * @author <a href="https://codefather.cn">编程导航学习圈</a>
 */
@RestController
@RequestMapping("/prompt/template")
@Slf4j
@Tag(name = "提示词模板接口")
public class PromptTemplateController {

    @Resource
    private PromptTemplateService promptTemplateService;

    @Resource
    private UserService userService;

    /**
     * 获取模板列表
     */
    @GetMapping("/list")
    @Operation(summary = "获取模板列表")
    public BaseResponse<List<PromptTemplateVO>> listTemplates(
            @RequestParam(required = false) String strategy,
            HttpServletRequest httpRequest
    ) {
        User loginUser = userService.getLoginUser(httpRequest);
        List<PromptTemplateVO> templates = promptTemplateService.listTemplates(loginUser.getId(), strategy);
        return ResultUtils.success(templates);
    }

    /**
     * 根据ID获取模板
     */
    @GetMapping("/get")
    @Operation(summary = "根据ID获取模板")
    public BaseResponse<PromptTemplateVO> getTemplate(
            @RequestParam String templateId,
            HttpServletRequest httpRequest
    ) {
        ThrowUtils.throwIf(StrUtil.isBlank(templateId), ErrorCode.PARAMS_ERROR);
        User loginUser = userService.getLoginUser(httpRequest);
        PromptTemplateVO template = promptTemplateService.getTemplateById(templateId, loginUser.getId());
        return ResultUtils.success(template);
    }

    /**
     * 创建模板
     */
    @PostMapping("/create")
    @Operation(summary = "创建模板")
    public BaseResponse<String> createTemplate(
            @RequestBody CreatePromptTemplateRequest request,
            HttpServletRequest httpRequest
    ) {
        ThrowUtils.throwIf(request == null, ErrorCode.PARAMS_ERROR);
        User loginUser = userService.getLoginUser(httpRequest);

        PromptTemplate template = new PromptTemplate();
        template.setName(request.getName());
        template.setDescription(request.getDescription());
        template.setStrategy(request.getStrategy());
        template.setContent(request.getContent());
        template.setCategory(request.getCategory());
        if (request.getVariables() != null && !request.getVariables().isEmpty()) {
            template.setVariables(JSONUtil.toJsonStr(request.getVariables()));
        }

        String templateId = promptTemplateService.createTemplate(template, loginUser.getId());
        return ResultUtils.success(templateId);
    }

    /**
     * 更新模板
     */
    @PostMapping("/update")
    @Operation(summary = "更新模板")
    public BaseResponse<Boolean> updateTemplate(
            @RequestBody UpdatePromptTemplateRequest request,
            HttpServletRequest httpRequest
    ) {
        ThrowUtils.throwIf(request == null || StrUtil.isBlank(request.getId()), ErrorCode.PARAMS_ERROR);
        User loginUser = userService.getLoginUser(httpRequest);

        PromptTemplate template = new PromptTemplate();
        template.setId(request.getId());
        template.setName(request.getName());
        template.setDescription(request.getDescription());
        template.setStrategy(request.getStrategy());
        template.setContent(request.getContent());
        template.setCategory(request.getCategory());
        if (request.getIsActive() != null) {
            template.setIsActive(request.getIsActive() ? 1 : 0);
        }
        if (request.getVariables() != null && !request.getVariables().isEmpty()) {
            template.setVariables(JSONUtil.toJsonStr(request.getVariables()));
        }

        boolean result = promptTemplateService.updateTemplate(template, loginUser.getId());
        return ResultUtils.success(result);
    }

    /**
     * 删除模板
     */
    @PostMapping("/delete")
    @Operation(summary = "删除模板")
    public BaseResponse<Boolean> deleteTemplate(
            @RequestBody DeleteRequest deleteRequest,
            HttpServletRequest httpRequest
    ) {
        ThrowUtils.throwIf(deleteRequest == null || deleteRequest.getId() == null, ErrorCode.PARAMS_ERROR);
        User loginUser = userService.getLoginUser(httpRequest);
        boolean result = promptTemplateService.deleteTemplate(deleteRequest.getId().toString(), loginUser.getId());
        return ResultUtils.success(result);
    }

    /**
     * 增加使用次数
     */
    @PostMapping("/increment-usage")
    @Operation(summary = "增加使用次数")
    public BaseResponse<Boolean> incrementUsage(
            @RequestParam String templateId
    ) {
        ThrowUtils.throwIf(StrUtil.isBlank(templateId), ErrorCode.PARAMS_ERROR);
        boolean result = promptTemplateService.incrementUsageCount(templateId);
        return ResultUtils.success(result);
    }
}
