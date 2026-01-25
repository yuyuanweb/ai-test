package com.yupi.template.service;

import cn.hutool.json.JSONUtil;
import com.mybatisflex.core.paginate.Page;
import com.yupi.template.exception.BusinessException;
import com.yupi.template.exception.ErrorCode;
import com.yupi.template.model.dto.test.CreateBatchTestRequest;
import com.yupi.template.model.dto.test.TaskQueryRequest;
import com.yupi.template.model.entity.Scene;
import com.yupi.template.model.entity.ScenePrompt;
import com.yupi.template.model.entity.TestResult;
import com.yupi.template.model.entity.TestTask;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.*;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 批量测试服务测试
 *
 * @author <a href="https://codefather.cn">编程导航学习圈</a>
 */
@SpringBootTest
@ActiveProfiles("local")
@Slf4j
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class BatchTestServiceTest {

    @Resource
    private BatchTestService batchTestService;

    @Resource
    private com.yupi.template.mapper.SceneMapper sceneMapper;

    @Resource
    private com.yupi.template.mapper.ScenePromptMapper scenePromptMapper;

    private static final Long TEST_USER_ID = 1L;
    private static final Long OTHER_USER_ID = 2L;
    private static String testSceneId;
    private static String testTaskId;

    /**
     * 测试创建批量测试任务 - 成功场景
     */
    @Test
    @Order(1)
    @Transactional
    void testCreateBatchTestTask_Success() {
        log.info("=== 测试创建批量测试任务 - 成功场景 ===");

        CreateBatchTestRequest request = new CreateBatchTestRequest();
        request.setName("测试任务-编程能力评测");
        request.setSceneId(testSceneId);
        request.setModels(Arrays.asList("openai/gpt-4o", "anthropic/claude-3.5-sonnet"));

        String taskId = batchTestService.createBatchTestTask(request, TEST_USER_ID);

        assertNotNull(taskId, "任务ID不应为空");
        assertFalse(taskId.trim().isEmpty(), "任务ID不应为空字符串");
        log.info("创建任务成功: taskId={}", taskId);

        TestTask task = batchTestService.getTask(taskId, TEST_USER_ID);
        assertNotNull(task, "任务应存在");
        assertEquals("测试任务-编程能力评测", task.getName(), "任务名称应匹配");
        assertEquals(testSceneId, task.getSceneId(), "场景ID应匹配");
        assertEquals("pending", task.getStatus(), "初始状态应为pending");
        assertEquals(2, task.getTotalSubtasks(), "子任务总数应为2（2个模型×1个提示词）");
        assertEquals(0, task.getCompletedSubtasks(), "已完成子任务数应为0");
        assertNotNull(task.getCreateTime(), "创建时间不应为空");

        testTaskId = taskId;
        log.info("任务创建验证通过: {}", task);
    }

    /**
     * 测试创建批量测试任务 - 场景ID为空
     */
    @Test
    @Order(2)
    void testCreateBatchTestTask_EmptySceneId() {
        log.info("=== 测试创建批量测试任务 - 场景ID为空 ===");

        CreateBatchTestRequest request = new CreateBatchTestRequest();
        request.setName("测试任务");
        request.setSceneId("");
        request.setModels(Arrays.asList("openai/gpt-4o"));

        BusinessException exception = assertThrows(BusinessException.class, () -> {
            batchTestService.createBatchTestTask(request, TEST_USER_ID);
        }, "场景ID为空应抛出异常");

        assertEquals(ErrorCode.PARAMS_ERROR.getCode(), exception.getCode(), "错误码应匹配");
        assertTrue(exception.getMessage().contains("场景ID不能为空"), "错误信息应包含场景ID");
        log.info("参数校验测试通过: {}", exception.getMessage());
    }

    /**
     * 测试创建批量测试任务 - 模型列表为空
     */
    @Test
    @Order(3)
    void testCreateBatchTestTask_EmptyModels() {
        log.info("=== 测试创建批量测试任务 - 模型列表为空 ===");

        CreateBatchTestRequest request = new CreateBatchTestRequest();
        request.setName("测试任务");
        request.setSceneId(testSceneId);
        request.setModels(Arrays.asList());

        BusinessException exception = assertThrows(BusinessException.class, () -> {
            batchTestService.createBatchTestTask(request, TEST_USER_ID);
        }, "模型列表为空应抛出异常");

        assertEquals(ErrorCode.PARAMS_ERROR.getCode(), exception.getCode(), "错误码应匹配");
        assertTrue(exception.getMessage().contains("模型列表不能为空"), "错误信息应包含模型列表");
        log.info("参数校验测试通过: {}", exception.getMessage());
    }

    /**
     * 测试创建批量测试任务 - 场景不存在
     */
    @Test
    @Order(4)
    void testCreateBatchTestTask_SceneNotFound() {
        log.info("=== 测试创建批量测试任务 - 场景不存在 ===");

        CreateBatchTestRequest request = new CreateBatchTestRequest();
        request.setName("测试任务");
        request.setSceneId("non-existent-scene-id");
        request.setModels(Arrays.asList("openai/gpt-4o"));

        BusinessException exception = assertThrows(BusinessException.class, () -> {
            batchTestService.createBatchTestTask(request, TEST_USER_ID);
        }, "场景不存在应抛出异常");

        assertEquals(ErrorCode.NOT_FOUND_ERROR.getCode(), exception.getCode(), "错误码应匹配");
        assertTrue(exception.getMessage().contains("场景不存在"), "错误信息应包含场景不存在");
        log.info("场景不存在测试通过: {}", exception.getMessage());
    }

    /**
     * 测试获取任务详情 - 成功场景
     */
    @Test
    @Order(5)
    void testGetTask_Success() {
        log.info("=== 测试获取任务详情 - 成功场景 ===");

        if (testTaskId == null) {
            log.warn("跳过测试：testTaskId为空，需要先执行testCreateBatchTestTask_Success");
            return;
        }

        TestTask task = batchTestService.getTask(testTaskId, TEST_USER_ID);

        assertNotNull(task, "任务不应为空");
        assertEquals(testTaskId, task.getId(), "任务ID应匹配");
        assertEquals(TEST_USER_ID, task.getUserId(), "用户ID应匹配");
        log.info("获取任务详情成功: {}", task);
    }

    /**
     * 测试获取任务详情 - 任务不存在
     */
    @Test
    @Order(6)
    void testGetTask_NotFound() {
        log.info("=== 测试获取任务详情 - 任务不存在 ===");

        BusinessException exception = assertThrows(BusinessException.class, () -> {
            batchTestService.getTask("non-existent-task-id", TEST_USER_ID);
        }, "任务不存在应抛出异常");

        assertEquals(ErrorCode.NOT_FOUND_ERROR.getCode(), exception.getCode(), "错误码应匹配");
        assertTrue(exception.getMessage().contains("任务不存在"), "错误信息应包含任务不存在");
        log.info("任务不存在测试通过: {}", exception.getMessage());
    }

    /**
     * 测试获取任务详情 - 无权限
     */
    @Test
    @Order(7)
    void testGetTask_NoAuth() {
        log.info("=== 测试获取任务详情 - 无权限 ===");

        if (testTaskId == null) {
            log.warn("跳过测试：testTaskId为空");
            return;
        }

        BusinessException exception = assertThrows(BusinessException.class, () -> {
            batchTestService.getTask(testTaskId, OTHER_USER_ID);
        }, "无权限应抛出异常");

        assertEquals(ErrorCode.NO_AUTH_ERROR.getCode(), exception.getCode(), "错误码应匹配");
        assertTrue(exception.getMessage().contains("无权限"), "错误信息应包含无权限");
        log.info("无权限测试通过: {}", exception.getMessage());
    }

    /**
     * 测试分页查询任务列表 - 成功场景
     */
    @Test
    @Order(8)
    void testListTasks_Success() {
        log.info("=== 测试分页查询任务列表 - 成功场景 ===");

        TaskQueryRequest req = new TaskQueryRequest();
        req.setPageNum(1);
        req.setPageSize(10);
        Page<TestTask> page = batchTestService.listTasks(TEST_USER_ID, req);

        assertNotNull(page, "分页结果不应为空");
        assertNotNull(page.getRecords(), "任务列表不应为空");
        assertTrue(page.getTotalRow() >= 0, "总数应大于等于0");
        log.info("查询任务列表成功: total={}, records={}", page.getTotalRow(), page.getRecords().size());
    }

    /**
     * 测试分页查询任务列表 - 按状态筛选
     */
    @Test
    @Order(9)
    void testListTasks_WithStatus() {
        log.info("=== 测试分页查询任务列表 - 按状态筛选 ===");

        TaskQueryRequest req = new TaskQueryRequest();
        req.setPageNum(1);
        req.setPageSize(10);
        req.setStatus("pending");
        Page<TestTask> page = batchTestService.listTasks(TEST_USER_ID, req);

        assertNotNull(page, "分页结果不应为空");
        assertNotNull(page.getRecords(), "任务列表不应为空");
        page.getRecords().forEach(task -> {
            assertEquals("pending", task.getStatus(), "任务状态应为pending");
        });
        log.info("按状态筛选测试通过: total={}", page.getTotalRow());
    }

    /**
     * 测试删除任务 - 成功场景
     */
    @Test
    @Order(10)
    @Transactional
    void testDeleteTask_Success() {
        log.info("=== 测试删除任务 - 成功场景 ===");

        if (testTaskId == null) {
            log.warn("跳过测试：testTaskId为空");
            return;
        }

        boolean deleted = batchTestService.deleteTask(testTaskId, TEST_USER_ID);
        assertTrue(deleted, "删除应该成功");
        log.info("删除任务成功: taskId={}", testTaskId);

        BusinessException exception = assertThrows(BusinessException.class, () -> {
            batchTestService.getTask(testTaskId, TEST_USER_ID);
        }, "已删除的任务应查询不到");
        log.info("删除验证通过: {}", exception.getMessage());
    }

    /**
     * 测试获取任务结果 - 成功场景
     */
    @Test
    @Order(11)
    void testGetTaskResults_Success() {
        log.info("=== 测试获取任务结果 - 成功场景 ===");

        if (testTaskId == null) {
            log.warn("跳过测试：testTaskId为空");
            return;
        }

        List<TestResult> results = batchTestService.getTaskResults(testTaskId, TEST_USER_ID);

        assertNotNull(results, "结果列表不应为空");
        log.info("获取任务结果成功: taskId={}, resultCount={}", testTaskId, results.size());
    }

    /**
     * 初始化测试数据 - 创建测试场景
     */
    @BeforeEach
    @Transactional
    void setUp() {
        if (testSceneId != null) {
            return;
        }

        log.info("=== 初始化测试数据 ===");

        String sceneId = UUID.randomUUID().toString();
        Scene scene = Scene.builder()
                .id(sceneId)
                .userId(TEST_USER_ID)
                .name("测试场景-编程能力")
                .description("用于测试的编程能力场景")
                .category("编程")
                .isPreset(0)
                .isActive(1)
                .createTime(LocalDateTime.now())
                .updateTime(LocalDateTime.now())
                .isDelete(0)
                .build();

        sceneMapper.insert(scene);
        testSceneId = sceneId;
        log.info("创建测试场景: sceneId={}", sceneId);

        ScenePrompt prompt = ScenePrompt.builder()
                .id(UUID.randomUUID().toString())
                .sceneId(sceneId)
                .userId(TEST_USER_ID)
                .promptIndex(0)
                .title("测试提示词1")
                .content("请写一个Java函数，计算两个数的和")
                .difficulty("easy")
                .createTime(LocalDateTime.now())
                .updateTime(LocalDateTime.now())
                .isDelete(0)
                .build();

        scenePromptMapper.insert(prompt);
        log.info("创建测试提示词: promptId={}", prompt.getId());
    }

    /**
     * 清理测试数据
     */
    @AfterEach
    @Transactional
    void tearDown() {
        if (testSceneId != null && testTaskId == null) {
            log.info("=== 清理测试数据 ===");
            sceneMapper.deleteById(testSceneId);
            log.info("删除测试场景: sceneId={}", testSceneId);
        }
    }
}
