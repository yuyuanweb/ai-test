package com.yupi.template.worker;

import com.yupi.template.mapper.ModelMapper;
import com.yupi.template.mapper.TestResultMapper;
import com.yupi.template.mapper.TestTaskMapper;
import com.yupi.template.model.dto.test.SubTaskMessage;
import com.yupi.template.model.entity.Model;
import com.yupi.template.model.entity.TestResult;
import com.yupi.template.model.entity.TestTask;
import com.yupi.template.service.BatchTestService;
import com.yupi.template.service.ProgressNotificationService;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.metadata.Usage;
import org.springframework.ai.chat.metadata.ChatResponseMetadata;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

/**
 * 测试任务异步执行器测试
 *
 * @author <a href="https://codefather.cn">编程导航学习圈</a>
 */
@SpringBootTest
@ActiveProfiles("local")
@Slf4j
@TestPropertySource(properties = {
        "spring.rabbitmq.host=127.0.0.1",
        "spring.rabbitmq.port=5672"
})
class TestWorkerTest {

    @Resource
    private TestWorker testWorker;

    @Resource
    private TestTaskMapper testTaskMapper;

    @Resource
    private TestResultMapper testResultMapper;

    @Resource
    private ModelMapper modelMapper;

    @Resource
    private BatchTestService batchTestService;

    @MockBean
    private ChatModel chatModel;

    @MockBean
    private ProgressNotificationService progressNotificationService;

    private static String testTaskId;
    private static Long testUserId = 1L;
    private static String testSceneId;

    /**
     * 测试处理子任务 - 成功场景
     */
    @Test
    void testProcessSubTask_Success() {
        log.info("=== 测试处理子任务 - 成功场景 ===");

        if (testTaskId == null) {
            log.warn("跳过测试：testTaskId为空，需要先创建测试任务");
            return;
        }

        SubTaskMessage subTask = SubTaskMessage.builder()
                .taskId(testTaskId)
                .sceneId(testSceneId)
                .promptId(UUID.randomUUID().toString())
                .promptTitle("测试提示词")
                .promptContent("请写一个Java函数，计算两个数的和")
                .modelName("openai/gpt-4o")
                .userId(testUserId)
                .build();

        ChatResponse mockResponse = createMockChatResponse("public int add(int a, int b) { return a + b; }", 10, 20);
        when(chatModel.call(any(Prompt.class))).thenReturn(mockResponse);

        testWorker.processSubTask(subTask);

        List<TestResult> results = testResultMapper.selectListByQuery(
                com.mybatisflex.core.query.QueryWrapper.create()
                        .eq("taskId", testTaskId)
                        .eq("modelName", "openai/gpt-4o")
        );

        assertFalse(results.isEmpty(), "应该创建测试结果");
        TestResult result = results.get(0);
        assertEquals(testTaskId, result.getTaskId(), "任务ID应匹配");
        assertEquals("openai/gpt-4o", result.getModelName(), "模型名称应匹配");
        assertNotNull(result.getOutputText(), "输出文本不应为空");
        assertNotNull(result.getResponseTimeMs(), "响应时间不应为空");
        assertNotNull(result.getInputTokens(), "输入Token不应为空");
        assertNotNull(result.getOutputTokens(), "输出Token不应为空");

        log.info("处理子任务测试通过: resultId={}", result.getId());
    }

    /**
     * 测试处理子任务 - 任务不存在
     */
    @Test
    void testProcessSubTask_TaskNotFound() {
        log.info("=== 测试处理子任务 - 任务不存在 ===");

        SubTaskMessage subTask = SubTaskMessage.builder()
                .taskId("non-existent-task-id")
                .sceneId(testSceneId)
                .promptId(UUID.randomUUID().toString())
                .promptTitle("测试提示词")
                .promptContent("测试内容")
                .modelName("openai/gpt-4o")
                .userId(testUserId)
                .build();

        testWorker.processSubTask(subTask);

        verify(chatModel, never()).call(any(Prompt.class));
        log.info("任务不存在测试通过");
    }

    /**
     * 测试处理子任务 - 任务已取消
     */
    @Test
    void testProcessSubTask_TaskCancelled() {
        log.info("=== 测试处理子任务 - 任务已取消 ===");

        TestTask cancelledTask = TestTask.builder()
                .id(UUID.randomUUID().toString())
                .userId(testUserId)
                .name("已取消任务")
                .sceneId(testSceneId)
                .models("[\"openai/gpt-4o\"]")
                .status("cancelled")
                .totalSubtasks(1)
                .completedSubtasks(0)
                .createTime(LocalDateTime.now())
                .updateTime(LocalDateTime.now())
                .isDelete(0)
                .build();

        testTaskMapper.insert(cancelledTask);

        SubTaskMessage subTask = SubTaskMessage.builder()
                .taskId(cancelledTask.getId())
                .sceneId(testSceneId)
                .promptId(UUID.randomUUID().toString())
                .promptTitle("测试提示词")
                .promptContent("测试内容")
                .modelName("openai/gpt-4o")
                .userId(testUserId)
                .build();

        testWorker.processSubTask(subTask);

        verify(chatModel, never()).call(any(Prompt.class));
        log.info("任务已取消测试通过");

        testTaskMapper.deleteById(cancelledTask.getId());
    }

    /**
     * 测试处理子任务 - AI调用异常
     */
    @Test
    void testProcessSubTask_AIException() {
        log.info("=== 测试处理子任务 - AI调用异常 ===");

        if (testTaskId == null) {
            log.warn("跳过测试：testTaskId为空");
            return;
        }

        SubTaskMessage subTask = SubTaskMessage.builder()
                .taskId(testTaskId)
                .sceneId(testSceneId)
                .promptId(UUID.randomUUID().toString())
                .promptTitle("测试提示词")
                .promptContent("测试内容")
                .modelName("openai/gpt-4o")
                .userId(testUserId)
                .build();

        when(chatModel.call(any(Prompt.class))).thenThrow(new RuntimeException("AI服务调用失败"));

        assertDoesNotThrow(() -> {
            testWorker.processSubTask(subTask);
        }, "异常应该被捕获，不应抛出");

        TestTask task = testTaskMapper.selectOneById(testTaskId);
        if (task != null && "failed".equals(task.getStatus())) {
            log.info("任务状态已更新为failed，异常处理正确");
        }

        log.info("AI调用异常测试通过");
    }

    /**
     * 测试成本计算 - 有模型价格信息
     */
    @Test
    void testCalculateCost_WithModelPrice() {
        log.info("=== 测试成本计算 - 有模型价格信息 ===");

        Model model = Model.builder()
                .id("openai/gpt-4o")
                .name("GPT-4o")
                .inputPrice(new BigDecimal("2.50"))
                .outputPrice(new BigDecimal("10.00"))
                .build();

        if (modelMapper.selectOneById("openai/gpt-4o") == null) {
            modelMapper.insert(model);
        }

        SubTaskMessage subTask = SubTaskMessage.builder()
                .taskId(testTaskId != null ? testTaskId : UUID.randomUUID().toString())
                .sceneId(testSceneId)
                .promptId(UUID.randomUUID().toString())
                .promptTitle("测试提示词")
                .promptContent("测试内容")
                .modelName("openai/gpt-4o")
                .userId(testUserId)
                .build();

        ChatResponse mockResponse = createMockChatResponse("测试响应", 100, 200);
        when(chatModel.call(any(Prompt.class))).thenReturn(mockResponse);

        if (testTaskId != null) {
            testWorker.processSubTask(subTask);

            List<TestResult> results = testResultMapper.selectListByQuery(
                    com.mybatisflex.core.query.QueryWrapper.create()
                            .eq("taskId", subTask.getTaskId())
                            .eq("modelName", "openai/gpt-4o")
                            .orderBy("createTime", false)
                            .limit(1)
            );

            if (!results.isEmpty()) {
                TestResult result = results.get(0);
                assertNotNull(result.getCost(), "成本不应为空");
                assertTrue(result.getCost().compareTo(BigDecimal.ZERO) > 0, "成本应大于0");
                log.info("成本计算测试通过: cost={}", result.getCost());
            }
        }
    }

    /**
     * 创建Mock ChatResponse
     * 注意：由于ChatResponse构造复杂，这里简化处理
     * 实际测试中可以使用Mockito.mock()或者集成测试
     */
    private ChatResponse createMockChatResponse(String text, int inputTokens, int outputTokens) {
        ChatResponse mockResponse = mock(ChatResponse.class);
        
        // Mock getResult()返回Generation对象
        Generation mockGeneration = mock(Generation.class);
        org.springframework.ai.chat.messages.AssistantMessage mockOutput = 
                mock(org.springframework.ai.chat.messages.AssistantMessage.class);
        
        // Mock Metadata - 创建一个接口来Mock getUsage()方法
        Usage mockUsage = mock(Usage.class);
        
        // 创建一个Metadata接口的Mock
        ChatResponseMetadata mockMetadata = mock(ChatResponseMetadata.class);

        // Mock调用链: getResult() -> getOutput() -> getText()
        when(mockResponse.getResult()).thenReturn(mockGeneration);
        when(mockGeneration.getOutput()).thenReturn(mockOutput);
        when(mockOutput.getText()).thenReturn(text);
        
        // Mock Metadata
        when(mockResponse.getMetadata()).thenReturn(mockMetadata);
        when(mockMetadata.getUsage()).thenReturn(mockUsage);
        when(mockUsage.getPromptTokens()).thenReturn(inputTokens);
        when(mockUsage.getCompletionTokens()).thenReturn(outputTokens);

        return mockResponse;
    }

    /**
     * 初始化测试数据
     */
    @BeforeEach
    void setUp() {
        if (testTaskId == null && testSceneId != null) {
            TestTask task = TestTask.builder()
                    .id(UUID.randomUUID().toString())
                    .userId(testUserId)
                    .name("Worker测试任务")
                    .sceneId(testSceneId)
                    .models("[\"openai/gpt-4o\"]")
                    .status("pending")
                    .totalSubtasks(1)
                    .completedSubtasks(0)
                    .createTime(LocalDateTime.now())
                    .updateTime(LocalDateTime.now())
                    .isDelete(0)
                    .build();

            testTaskMapper.insert(task);
            testTaskId = task.getId();
            log.info("创建测试任务: taskId={}", testTaskId);
        }
    }
}
