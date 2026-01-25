package com.yupi.template.controller;

import com.yupi.template.common.BaseResponse;
import com.yupi.template.common.ResultUtils;
import com.yupi.template.exception.ErrorCode;
import com.yupi.template.exception.ThrowUtils;
import com.yupi.template.model.entity.User;
import com.yupi.template.model.vo.ReportVO;
import com.yupi.template.service.ReportService;
import com.yupi.template.service.UserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Resource;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 报告接口
 *
 * @author <a href="https://codefather.cn">编程导航学习圈</a>
 */
@RestController
@RequestMapping("/report")
@Slf4j
@Tag(name = "报告接口")
public class ReportController {

    @Resource
    private ReportService reportService;

    @Resource
    private UserService userService;

    /**
     * 生成测试报告
     *
     * @param taskId 任务ID
     * @param httpRequest HTTP请求
     * @return 测试报告
     */
    @GetMapping("/generate")
    @Operation(summary = "生成测试报告")
    public BaseResponse<ReportVO> generateReport(
            @RequestParam String taskId,
            HttpServletRequest httpRequest
    ) {
        ThrowUtils.throwIf(taskId == null || taskId.trim().isEmpty(), ErrorCode.PARAMS_ERROR);
        User loginUser = userService.getLoginUser(httpRequest);

        ReportVO report = reportService.generateReport(taskId, loginUser.getId());
        return ResultUtils.success(report);
    }
}
