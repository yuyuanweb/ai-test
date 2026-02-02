package com.yupi.template.service;

import com.mybatisflex.core.paginate.Page;
import com.yupi.template.model.dto.test.CreateBatchTestRequest;
import com.yupi.template.model.dto.test.TaskQueryRequest;
import com.yupi.template.model.entity.TestTask;
import com.yupi.template.model.entity.TestResult;

import java.util.List;

/**
 * 批量测试服务接口
 *
 * @author <a href="https://codefather.cn">编程导航学习圈</a>
 */
public interface BatchTestService {

    /**
     * 创建批量测试任务
     *
     * @param request 请求参数
     * @param userId 用户ID
     * @return 任务ID
     */
    String createBatchTestTask(CreateBatchTestRequest request, Long userId);

    /**
     * 获取任务详情
     *
     * @param taskId 任务ID
     * @param userId 用户ID
     * @return 任务信息
     */
    TestTask getTask(String taskId, Long userId);

    /**
     * 分页查询任务列表
     *
     * @param userId 用户ID
     * @param queryRequest 查询请求
     * @return 分页结果
     */
    Page<TestTask> listTasks(Long userId, TaskQueryRequest queryRequest);

    /**
     * 删除任务
     *
     * @param taskId 任务ID
     * @param userId 用户ID
     * @return 是否成功
     */
    boolean deleteTask(String taskId, Long userId);

    /**
     * 获取任务的测试结果
     *
     * @param taskId 任务ID
     * @param userId 用户ID
     * @return 测试结果列表
     */
    List<TestResult> getTaskResults(String taskId, Long userId);

    /**
     * 更新任务进度（原子自增 completedSubtasks，多 Worker 并发安全）
     *
     * @param taskId 任务ID
     * @param currentModel 当前完成子任务的模型名称（可选）
     * @param currentPrompt 当前完成子任务的提示词标题（可选）
     */
    void updateTaskProgress(String taskId, String currentModel, String currentPrompt);

    /**
     * 标记任务失败并原子自增 completedSubtasks（子任务执行异常时调用）
     *
     * @param taskId 任务ID
     */
    void markTaskFailed(String taskId);

    /**
     * 更新测试结果评分
     *
     * @param resultId 测试结果ID
     * @param userRating 用户评分(1-5)
     * @param userId 用户ID
     * @return 是否成功
     */
    boolean updateTestResultRating(String resultId, Integer userRating, Long userId);
}

