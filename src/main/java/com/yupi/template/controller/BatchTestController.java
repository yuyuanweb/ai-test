package com.yupi.template.controller;

import com.mybatisflex.core.paginate.Page;
import com.yupi.template.common.BaseResponse;
import com.yupi.template.common.DeleteRequest;
import com.yupi.template.common.ResultUtils;
import com.yupi.template.exception.ErrorCode;
import com.yupi.template.exception.ThrowUtils;
import com.yupi.template.model.dto.test.CreateBatchTestRequest;
import com.yupi.template.model.dto.test.TaskQueryRequest;
import com.yupi.template.model.dto.test.UpdateTestResultRatingRequest;
import com.yupi.template.model.entity.TestResult;
import com.yupi.template.model.entity.TestTask;
import com.yupi.template.model.entity.User;
import com.yupi.template.service.BatchTestService;
import com.yupi.template.service.UserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Resource;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 批量测试接口
 *
 * @author <a href="https://codefather.cn">编程导航学习圈</a>
 */
@RestController
@RequestMapping("/batch-test")
@Slf4j
@Tag(name = "批量测试接口")
public class BatchTestController {

    @Resource
    private BatchTestService batchTestService;

    @Resource
    private UserService userService;

    /**
     * 创建批量测试任务
     */
    @PostMapping("/create")
    @Operation(summary = "创建批量测试任务")
    public BaseResponse<String> createBatchTestTask(
            @RequestBody CreateBatchTestRequest request,
            HttpServletRequest httpRequest
    ) {
        ThrowUtils.throwIf(request == null, ErrorCode.PARAMS_ERROR);
        User loginUser = userService.getLoginUser(httpRequest);
        
        String taskId = batchTestService.createBatchTestTask(request, loginUser.getId());
        return ResultUtils.success(taskId);
    }

    /**
     * 获取任务详情
     */
    @GetMapping("/task/get")
    @Operation(summary = "获取任务详情")
    public BaseResponse<TestTask> getTask(
            @RequestParam String taskId,
            HttpServletRequest httpRequest
    ) {
        ThrowUtils.throwIf(taskId == null || taskId.trim().isEmpty(), ErrorCode.PARAMS_ERROR);
        User loginUser = userService.getLoginUser(httpRequest);
        
        TestTask task = batchTestService.getTask(taskId, loginUser.getId());
        return ResultUtils.success(task);
    }

    /**
     * 分页查询任务列表
     */
    @PostMapping("/task/list/page")
    @Operation(summary = "分页查询任务列表")
    public BaseResponse<Page<TestTask>> listTasks(
            @RequestBody TaskQueryRequest queryRequest,
            HttpServletRequest httpRequest
    ) {
        ThrowUtils.throwIf(queryRequest == null, ErrorCode.PARAMS_ERROR);
        User loginUser = userService.getLoginUser(httpRequest);
        Page<TestTask> taskPage = batchTestService.listTasks(loginUser.getId(), queryRequest);
        return ResultUtils.success(taskPage);
    }

    /**
     * 删除任务
     */
    @PostMapping("/task/delete")
    @Operation(summary = "删除任务")
    public BaseResponse<Boolean> deleteTask(
            @RequestBody DeleteRequest deleteRequest,
            HttpServletRequest httpRequest
    ) {
        ThrowUtils.throwIf(deleteRequest == null || deleteRequest.getId() == null, ErrorCode.PARAMS_ERROR);
        User loginUser = userService.getLoginUser(httpRequest);
        
        boolean result = batchTestService.deleteTask(deleteRequest.getId().toString(), loginUser.getId());
        return ResultUtils.success(result);
    }

    /**
     * 获取任务的测试结果
     */
    @GetMapping("/result/list")
    @Operation(summary = "获取任务的测试结果")
    public BaseResponse<List<TestResult>> getTaskResults(
            @RequestParam String taskId,
            HttpServletRequest httpRequest
    ) {
        ThrowUtils.throwIf(taskId == null || taskId.trim().isEmpty(), ErrorCode.PARAMS_ERROR);
        User loginUser = userService.getLoginUser(httpRequest);
        
        List<TestResult> results = batchTestService.getTaskResults(taskId, loginUser.getId());
        return ResultUtils.success(results);
    }

    /**
     * 更新测试结果评分
     */
    @PostMapping("/result/rating")
    @Operation(summary = "更新测试结果评分")
    public BaseResponse<Boolean> updateTestResultRating(
            @RequestBody UpdateTestResultRatingRequest request,
            HttpServletRequest httpRequest
    ) {
        ThrowUtils.throwIf(request == null, ErrorCode.PARAMS_ERROR);
        ThrowUtils.throwIf(request.getResultId() == null || request.getResultId().trim().isEmpty(), 
                ErrorCode.PARAMS_ERROR, "测试结果ID不能为空");
        User loginUser = userService.getLoginUser(httpRequest);
        
        boolean result = batchTestService.updateTestResultRating(
                request.getResultId(), 
                request.getUserRating(), 
                loginUser.getId()
        );
        return ResultUtils.success(result);
    }
}
