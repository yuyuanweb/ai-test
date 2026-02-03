package com.yupi.template.controller;

import com.yupi.template.common.BaseResponse;
import com.yupi.template.common.ResultUtils;
import com.yupi.template.exception.ErrorCode;
import com.yupi.template.exception.ThrowUtils;
import com.yupi.template.model.dto.prompt.PromptOptimizationRequest;
import com.yupi.template.model.entity.User;
import com.yupi.template.model.vo.PromptOptimizationVO;
import com.yupi.template.service.PromptOptimizationService;
import com.yupi.template.service.UserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Resource;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 提示词优化接口
 *
 * @author <a href="https://codefather.cn">编程导航学习圈</a>
 */
@RestController
@RequestMapping("/prompt/optimization")
@Slf4j
@Tag(name = "提示词优化接口")
public class PromptOptimizationController {

    @Resource
    private PromptOptimizationService promptOptimizationService;

    @Resource
    private UserService userService;

    /**
     * 分析并优化提示词
     *
     * @param request 优化请求
     * @param httpRequest HTTP请求
     * @return 优化建议
     */
    @PostMapping("/analyze")
    @Operation(summary = "分析并优化提示词")
    public BaseResponse<PromptOptimizationVO> optimizePrompt(
            @RequestBody PromptOptimizationRequest request,
            HttpServletRequest httpRequest
    ) {
        ThrowUtils.throwIf(request == null, ErrorCode.PARAMS_ERROR);
        User loginUser = userService.getLoginUser(httpRequest);
        log.info("提示词优化请求: user={}, promptLength={}, hasResponse={}",
                loginUser.getId(),
                request.getOriginalPrompt() != null ? request.getOriginalPrompt().length() : 0,
                request.getAiResponse() != null && !request.getAiResponse().trim().isEmpty());

        PromptOptimizationVO result = promptOptimizationService.optimizePrompt(
                request.getOriginalPrompt(),
                request.getAiResponse(),
                request.getEvaluationModel(),
                loginUser.getId()
        );

        return ResultUtils.success(result);
    }
}
