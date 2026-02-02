package com.yupi.template.service.impl;

import cn.hutool.json.JSONUtil;
import com.mybatisflex.core.paginate.Page;
import com.mybatisflex.core.query.QueryWrapper;
import com.yupi.template.constant.RabbitMQConstant;
import com.yupi.template.exception.BusinessException;
import com.yupi.template.exception.ErrorCode;
import com.yupi.template.mapper.SceneMapper;
import com.yupi.template.mapper.ScenePromptMapper;
import com.yupi.template.mapper.TestResultMapper;
import com.yupi.template.mapper.TestTaskMapper;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import com.yupi.template.model.dto.test.CreateBatchTestRequest;
import com.yupi.template.model.dto.test.SubTaskMessage;
import com.yupi.template.model.dto.test.TaskQueryRequest;
import com.yupi.template.model.entity.Scene;
import com.yupi.template.model.entity.ScenePrompt;
import com.yupi.template.model.entity.TestResult;
import com.yupi.template.model.entity.TestTask;
import java.util.stream.Collectors;
import com.yupi.template.service.BatchTestService;
import com.yupi.template.service.ProgressNotificationService;
import com.yupi.template.model.vo.TaskProgressVO;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.core.MessageDeliveryMode;
import org.springframework.amqp.core.MessagePostProcessor;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;


/**
 * 批量测试服务实现
 *
 * @author <a href="https://codefather.cn">编程导航学习圈</a>
 */
@Service
@Slf4j
public class BatchTestServiceImpl implements BatchTestService {

    @Resource
    private TestTaskMapper testTaskMapper;

    @Resource
    private TestResultMapper testResultMapper;

    @Resource
    private SceneMapper sceneMapper;

    @Resource
    private ScenePromptMapper scenePromptMapper;

    @Resource
    private RabbitTemplate rabbitTemplate;

    @Resource
    private ProgressNotificationService progressNotificationService;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public String createBatchTestTask(CreateBatchTestRequest request, Long userId) {
        if (request.getSceneId() == null || request.getSceneId().trim().isEmpty()) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "场景ID不能为空");
        }

        if (request.getModels() == null || request.getModels().isEmpty()) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "模型列表不能为空");
        }

        Scene scene = sceneMapper.selectOneById(request.getSceneId());
        if (scene == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND_ERROR, "场景不存在");
        }

        if (scene.getIsPreset() == 0 && !scene.getUserId().equals(userId)) {
            throw new BusinessException(ErrorCode.NO_AUTH_ERROR, "无权限使用该场景");
        }

        List<ScenePrompt> prompts = scenePromptMapper.selectListByQuery(
                QueryWrapper.create()
                        .eq("sceneId", request.getSceneId())
                        .orderBy("promptIndex", true)
        );

        if (prompts.isEmpty()) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "场景中没有提示词");
        }

        String taskId = UUID.randomUUID().toString();
        int totalSubtasks = request.getModels().size() * prompts.size();

        // 构建配置参数JSON
        Map<String, Object> configMap = new java.util.HashMap<>();
        if (request.getTemperature() != null) {
            configMap.put("temperature", request.getTemperature());
        }
        if (request.getTopP() != null) {
            configMap.put("topP", request.getTopP());
        }
        if (request.getMaxTokens() != null) {
            configMap.put("maxTokens", request.getMaxTokens());
        }
        if (request.getTopK() != null) {
            configMap.put("topK", request.getTopK());
        }
        if (request.getFrequencyPenalty() != null) {
            configMap.put("frequencyPenalty", request.getFrequencyPenalty());
        }
        if (request.getPresencePenalty() != null) {
            configMap.put("presencePenalty", request.getPresencePenalty());
        }
        if (request.getEnableAiScoring() != null) {
            configMap.put("enableAiScoring", request.getEnableAiScoring());
        }
        String configJson = configMap.isEmpty() ? null : JSONUtil.toJsonStr(configMap);

        TestTask testTask = TestTask.builder()
                .id(taskId)
                .userId(userId)
                .name(request.getName())
                .sceneId(request.getSceneId())
                .models(JSONUtil.toJsonStr(request.getModels()))
                .config(configJson)
                .status("pending")
                .totalSubtasks(totalSubtasks)
                .completedSubtasks(0)
                .createTime(LocalDateTime.now())
                .updateTime(LocalDateTime.now())
                .isDelete(0)
                .build();

        boolean result = testTaskMapper.insert(testTask) > 0;
        if (!result) {
            throw new BusinessException(ErrorCode.OPERATION_ERROR, "创建测试任务失败");
        }

        log.info("创建批量测试任务: taskId={}, sceneId={}, models={}, prompts={}, totalSubtasks={}",
                taskId, request.getSceneId(), request.getModels().size(), prompts.size(), totalSubtasks);
        
        // 记录所有提示词ID，便于排查
        List<String> promptIds = prompts.stream().map(ScenePrompt::getId).collect(java.util.stream.Collectors.toList());
        log.info("任务提示词列表: taskId={}, promptIds={}", taskId, promptIds);

        // 准备子任务消息列表
        List<SubTaskMessage> subTaskMessages = new ArrayList<>();
        for (String modelName : request.getModels()) {
            for (ScenePrompt prompt : prompts) {
                SubTaskMessage subTask = SubTaskMessage.builder()
                        .taskId(taskId)
                        .sceneId(request.getSceneId())
                        .promptId(prompt.getId())
                        .promptTitle(prompt.getTitle())
                        .promptContent(prompt.getContent())
                        .modelName(modelName)
                        .userId(userId)
                        .build();
                subTaskMessages.add(subTask);
            }
        }

        int priority = totalSubtasks <= RabbitMQConstant.TOTAL_SUBTASKS_THRESHOLD_HIGH
                ? RabbitMQConstant.PRIORITY_HIGH
                : (totalSubtasks <= RabbitMQConstant.TOTAL_SUBTASKS_THRESHOLD_NORMAL
                ? RabbitMQConstant.PRIORITY_NORMAL
                : RabbitMQConstant.PRIORITY_LOW);
        MessagePostProcessor postProcessor = msg -> {
            msg.getMessageProperties().setPriority(priority);
            msg.getMessageProperties().setDeliveryMode(MessageDeliveryMode.PERSISTENT);
            return msg;
        };

        // 在事务提交后再发送消息到队列，确保任务记录已提交
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                int subTaskCount = 0;
                for (SubTaskMessage subTask : subTaskMessages) {
                    rabbitTemplate.convertAndSend(
                            RabbitMQConstant.TEST_EXCHANGE,
                            RabbitMQConstant.TEST_ROUTING_KEY,
                            subTask,
                            postProcessor
                    );
                    subTaskCount++;
                    log.debug("发送子任务到队列: taskId={}, model={}, prompt={}, priority={}, subTaskCount={}/{}",
                            taskId, subTask.getModelName(), subTask.getPromptTitle(), priority, subTaskCount, subTaskMessages.size());
                }
                log.info("子任务发送完成: taskId={}, 已发送={}, 预期={}, priority={}", taskId, subTaskCount, subTaskMessages.size(), priority);
            }
        });

        // 推送初始进度
        TaskProgressVO initialProgress = TaskProgressVO.builder()
                .taskId(taskId)
                .percentage(0)
                .completedSubtasks(0)
                .totalSubtasks(totalSubtasks)
                .status("pending")
                .timestamp(System.currentTimeMillis())
                .build();
        progressNotificationService.sendProgress(taskId, initialProgress);

        return taskId;
    }

    @Override
    public TestTask getTask(String taskId, Long userId) {
        TestTask task = testTaskMapper.selectOneById(taskId);
        if (task == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND_ERROR, "任务不存在");
        }

        if (!task.getUserId().equals(userId)) {
            throw new BusinessException(ErrorCode.NO_AUTH_ERROR, "无权限查看");
        }

        return task;
    }

    @Override
    public Page<TestTask> listTasks(Long userId, TaskQueryRequest queryRequest) {
        QueryWrapper queryWrapper = QueryWrapper.create()
                .from(TestTask.class)
                .eq("test_task.userId", userId);

        if (queryRequest.getCategory() != null && !queryRequest.getCategory().trim().isEmpty()) {
            QueryWrapper sceneWrapper = QueryWrapper.create()
                    .from(Scene.class)
                    .eq("category", queryRequest.getCategory().trim());
            List<Scene> scenes = sceneMapper.selectListByQuery(sceneWrapper);
            if (scenes != null && !scenes.isEmpty()) {
                List<String> sceneIds = scenes.stream()
                        .map(Scene::getId)
                        .collect(Collectors.toList());
                queryWrapper.in("test_task.sceneId", sceneIds);
            } else {
                queryWrapper.eq("1", "0");
            }
        }

        if (queryRequest.getStatus() != null && !queryRequest.getStatus().trim().isEmpty()) {
            queryWrapper.eq("test_task.status", queryRequest.getStatus().trim());
        }

        if (queryRequest.getKeyword() != null && !queryRequest.getKeyword().trim().isEmpty()) {
            queryWrapper.like("test_task.name", queryRequest.getKeyword().trim());
        }

        if (queryRequest.getStartTime() != null && !queryRequest.getStartTime().trim().isEmpty()) {
            try {
                LocalDateTime start = LocalDateTime.parse(queryRequest.getStartTime().trim(), 
                        DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
                queryWrapper.ge("test_task.createTime", start);
            } catch (Exception e) {
                log.warn("解析开始时间失败: {}", queryRequest.getStartTime(), e);
            }
        }

        if (queryRequest.getEndTime() != null && !queryRequest.getEndTime().trim().isEmpty()) {
            try {
                LocalDateTime end = LocalDateTime.parse(queryRequest.getEndTime().trim(), 
                        DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
                queryWrapper.le("test_task.createTime", end);
            } catch (Exception e) {
                log.warn("解析结束时间失败: {}", queryRequest.getEndTime(), e);
            }
        }

        queryWrapper.orderBy("test_task.createTime", false);

        int pageNum = queryRequest.getPageNum();
        int pageSize = queryRequest.getPageSize();
        return testTaskMapper.paginate(new Page<>(pageNum, pageSize), queryWrapper);
    }

    @Override
    public boolean deleteTask(String taskId, Long userId) {
        TestTask task = testTaskMapper.selectOneById(taskId);
        if (task == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND_ERROR, "任务不存在");
        }

        if (!task.getUserId().equals(userId)) {
            throw new BusinessException(ErrorCode.NO_AUTH_ERROR, "无权限删除");
        }

        return testTaskMapper.deleteById(taskId) > 0;
    }

    @Override
    public List<TestResult> getTaskResults(String taskId, Long userId) {
        TestTask task = testTaskMapper.selectOneById(taskId);
        if (task == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND_ERROR, "任务不存在");
        }

        if (!task.getUserId().equals(userId)) {
            throw new BusinessException(ErrorCode.NO_AUTH_ERROR, "无权限查看");
        }

        QueryWrapper queryWrapper = QueryWrapper.create()
                .eq("taskId", taskId)
                .orderBy("createTime", true);

        return testResultMapper.selectListByQuery(queryWrapper);
    }

    @Override
    public void updateTaskProgress(String taskId, String currentModel, String currentPrompt) {
        int rows = testTaskMapper.incrementCompletedSubtasks(taskId);
        if (rows <= 0) {
            log.debug("更新任务进度跳过: 任务已取消或已失败, taskId={}", taskId);
            return;
        }
        TestTask task = testTaskMapper.selectOneById(taskId);
        if (task == null) {
            return;
        }
        int percentage = task.getTotalSubtasks() > 0
                ? (int) ((double) task.getCompletedSubtasks() / task.getTotalSubtasks() * 100)
                : 0;
        TaskProgressVO progress = TaskProgressVO.builder()
                .taskId(taskId)
                .percentage(percentage)
                .completedSubtasks(task.getCompletedSubtasks())
                .totalSubtasks(task.getTotalSubtasks())
                .currentModel(currentModel)
                .currentPrompt(currentPrompt)
                .status(task.getStatus())
                .timestamp(System.currentTimeMillis())
                .build();
        progressNotificationService.sendProgress(taskId, progress);
    }

    @Override
    public void markTaskFailed(String taskId) {
        int rows = testTaskMapper.incrementCompletedSubtasksAndFail(taskId);
        if (rows <= 0) {
            log.debug("标记任务失败跳过: 任务已非 pending/running, taskId={}", taskId);
            return;
        }
        TaskProgressVO progress = TaskProgressVO.builder()
                .taskId(taskId)
                .status("failed")
                .timestamp(System.currentTimeMillis())
                .build();
        progressNotificationService.sendProgress(taskId, progress);
    }

    @Override
    public boolean updateTestResultRating(String resultId, Integer userRating, Long userId) {
        if (resultId == null || resultId.trim().isEmpty()) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "测试结果ID不能为空");
        }

        if (userRating != null && (userRating < 1 || userRating > 5)) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "评分必须在1-5之间");
        }

        TestResult testResult = testResultMapper.selectOneById(resultId);
        if (testResult == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND_ERROR, "测试结果不存在");
        }

        if (!testResult.getUserId().equals(userId)) {
            throw new BusinessException(ErrorCode.NO_AUTH_ERROR, "无权限修改该测试结果");
        }

        testResult.setUserRating(userRating);
        testResult.setUpdateTime(LocalDateTime.now());

        boolean result = testResultMapper.update(testResult) > 0;
        if (result) {
            log.info("更新测试结果评分: resultId={}, userId={}, rating={}", resultId, userId, userRating);
        }
        return result;
    }
}

