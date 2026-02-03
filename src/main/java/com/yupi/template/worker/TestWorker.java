package com.yupi.template.worker;

import cn.hutool.json.JSONUtil;
import com.yupi.template.constant.ConversationConstant;
import com.yupi.template.constant.RabbitMQConstant;
import com.yupi.template.guardrail.PromptGuardrail;
import com.yupi.template.mapper.TestResultMapper;
import com.yupi.template.mapper.TestTaskMapper;
import com.yupi.template.model.dto.test.SubTaskMessage;
import com.yupi.template.model.entity.TestResult;
import com.yupi.template.model.entity.TestTask;
import com.yupi.template.model.dto.evaluation.AIScoreResult;
import com.yupi.template.model.vo.ModelPricingVO;
import com.yupi.template.service.AIScoringService;
import com.yupi.template.service.BatchTestService;
import com.yupi.template.service.ModelService;
import com.yupi.template.service.ProgressNotificationService;
import com.yupi.template.service.UserModelUsageService;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;
import io.micrometer.core.instrument.MeterRegistry;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import java.time.Duration;
import reactor.core.publisher.Flux;

/**
 * 测试任务异步执行器
 *
 * @author <a href="https://codefather.cn">编程导航学习圈</a>
 */
@Component
@Slf4j
public class TestWorker {

    @Resource
    private ChatModel chatModel;

    @Resource
    private TestResultMapper testResultMapper;

    @Resource
    private TestTaskMapper testTaskMapper;

    @Resource
    private BatchTestService batchTestService;

    @Resource
    private ModelService modelService;

    @Resource
    private UserModelUsageService userModelUsageService;

    @Resource
    private ProgressNotificationService progressNotificationService;

    @Resource
    private AIScoringService aiScoringService;

    @Resource
    private MeterRegistry meterRegistry;

    @Resource
    private com.yupi.template.service.BudgetService budgetService;

    @RabbitListener(queues = RabbitMQConstant.TEST_QUEUE)
    public void processSubTask(SubTaskMessage subTask) {
        log.info("开始处理子任务: taskId={}, model={}, prompt={}", 
                subTask.getTaskId(), subTask.getModelName(), subTask.getPromptTitle());

        long startTime = System.currentTimeMillis();
        String resultId = UUID.randomUUID().toString();

        try {
            TestTask task = testTaskMapper.selectOneById(subTask.getTaskId());
            if (task == null) {
                log.error("任务不存在: taskId={}", subTask.getTaskId());
                return;
            }

            if ("cancelled".equals(task.getStatus()) || "failed".equals(task.getStatus())) {
                log.warn("任务已取消或失败，跳过子任务: taskId={}, status={}", subTask.getTaskId(), task.getStatus());
                return;
            }

            PromptGuardrail.validate(subTask.getPromptContent());

            List<org.springframework.ai.chat.messages.Message> messages = new ArrayList<>();
            messages.add(new UserMessage(subTask.getPromptContent()));

            OpenAiChatOptions.Builder optionsBuilder = OpenAiChatOptions.builder()
                    .model(subTask.getModelName());

            // 从任务配置中读取参数
            if (task.getConfig() != null && !task.getConfig().trim().isEmpty()) {
                try {
                    Map<String, Object> configMap = JSONUtil.parseObj(task.getConfig());
                    if (configMap.containsKey("temperature")) {
                        Object temp = configMap.get("temperature");
                        if (temp instanceof Number) {
                            optionsBuilder.temperature(((Number) temp).doubleValue());
                        }
                    } else {
                        optionsBuilder.temperature(ConversationConstant.DEFAULT_TEMPERATURE);
                    }
                    if (configMap.containsKey("topP")) {
                        Object topP = configMap.get("topP");
                        if (topP instanceof Number) {
                            optionsBuilder.topP(((Number) topP).doubleValue());
                        }
                    }
                    if (configMap.containsKey("maxTokens")) {
                        Object maxTokens = configMap.get("maxTokens");
                        if (maxTokens instanceof Number) {
                            optionsBuilder.maxTokens(((Number) maxTokens).intValue());
                        }
                    }
                    if (configMap.containsKey("frequencyPenalty")) {
                        Object freqPenalty = configMap.get("frequencyPenalty");
                        if (freqPenalty instanceof Number) {
                            optionsBuilder.frequencyPenalty(((Number) freqPenalty).doubleValue());
                        }
                    }
                    if (configMap.containsKey("presencePenalty")) {
                        Object presPenalty = configMap.get("presencePenalty");
                        if (presPenalty instanceof Number) {
                            optionsBuilder.presencePenalty(((Number) presPenalty).doubleValue());
                        }
                    }
                } catch (Exception e) {
                    log.warn("解析任务配置失败，使用默认参数: taskId={}, error={}", task.getId(), e.getMessage());
                    optionsBuilder.temperature(ConversationConstant.DEFAULT_TEMPERATURE);
                }
            } else {
                optionsBuilder.temperature(ConversationConstant.DEFAULT_TEMPERATURE);
            }

            Prompt chatPrompt = new Prompt(messages, optionsBuilder.build());

            // 使用流式调用以获取reasoning内容（思考模型需要流式调用才能获取reasoning）
            AtomicReference<String> fullContent = new AtomicReference<>("");
            AtomicReference<String> reasoning = new AtomicReference<>("");
            AtomicReference<Integer> inputTokens = new AtomicReference<>(0);
            AtomicReference<Integer> outputTokens = new AtomicReference<>(0);

            // 流式调用并累积所有chunk
            Flux<ChatResponse> responseFlux = chatModel.stream(chatPrompt);
            
            // 阻塞等待所有chunk处理完成
            responseFlux
                    .doOnNext(chatResponse -> {
                        if (chatResponse.getResult() == null || chatResponse.getResult().getOutput() == null) {
                            return;
                        }
                        // 累积输出内容
                        String content = chatResponse.getResult().getOutput().getText();
                        if (content != null) {
                            fullContent.updateAndGet(prev -> prev + content);
                        }

                        // 提取思考过程（reasoning tokens）- 用于o1/DeepSeek R1等模型
                        if (chatResponse.getResult() != null && chatResponse.getResult().getOutput() != null) {
                            Map<String, Object> outputMetadata = chatResponse.getResult().getOutput().getMetadata();
                            if (outputMetadata != null && outputMetadata.containsKey("reasoningContent")) {
                                Object reasoningObj = outputMetadata.get("reasoningContent");
                                if (reasoningObj != null) {
                                    String reasoningContent = reasoningObj.toString();
                                    reasoning.updateAndGet(prev -> prev + reasoningContent);
                                }
                            }
                        }

                        // 累积Token统计（最后一个chunk包含完整的usage信息）
                        if (chatResponse.getMetadata() != null && chatResponse.getMetadata().getUsage() != null) {
                            var usage = chatResponse.getMetadata().getUsage();
                            if (usage.getPromptTokens() != null) {
                                inputTokens.set(usage.getPromptTokens());
                            }
                            if (usage.getCompletionTokens() != null) {
                                outputTokens.set(usage.getCompletionTokens());
                            }
                        }
                    })
                    .blockLast(Duration.ofMinutes(10)); // 阻塞等待所有chunk处理完成，最多等待10分钟

            long responseTimeMs = System.currentTimeMillis() - startTime;
            String outputText = fullContent.get();
            String reasoningText = reasoning.get().isEmpty() ? null : reasoning.get();

            BigDecimal cost = calculateCost(subTask.getModelName(), inputTokens.get(), outputTokens.get());

            TestResult testResult = TestResult.builder()
                    .id(resultId)
                    .taskId(subTask.getTaskId())
                    .userId(subTask.getUserId())
                    .sceneId(subTask.getSceneId())
                    .promptId(subTask.getPromptId())
                    .modelName(subTask.getModelName())
                    .inputPrompt(subTask.getPromptContent())
                    .outputText(outputText)
                    .reasoning(reasoningText)
                    .responseTimeMs((int) responseTimeMs)
                    .inputTokens(inputTokens.get())
                    .outputTokens(outputTokens.get())
                    .cost(cost)
                    .createTime(LocalDateTime.now())
                    .updateTime(LocalDateTime.now())
                    .isDelete(0)
                    .build();

            testResultMapper.insert(testResult);

            // 累加用户消耗到Redis
            if (cost != null && cost.compareTo(BigDecimal.ZERO) > 0) {
                budgetService.addCost(subTask.getUserId(), cost);
            }

            // AI评分（如果启用）
            boolean enableAiScoring = checkEnableAiScoring(task);
            if (enableAiScoring) {
                try {
                    log.info("开始AI评分: taskId={}, model={}, prompt={}", 
                            subTask.getTaskId(), subTask.getModelName(), subTask.getPromptTitle());
                    
                    AIScoreResult aiScoreResult = aiScoringService.scoreWithMultipleJudges(
                            subTask.getPromptContent(),
                            outputText,
                            subTask.getModelName(),
                            subTask.getUserId()
                    );

                    String aiScoreJson = JSONUtil.toJsonStr(aiScoreResult);
                    testResult.setAiScore(aiScoreJson);
                    testResult.setUpdateTime(LocalDateTime.now());
                    testResultMapper.update(testResult);

                    log.info("AI评分完成: taskId={}, model={}, judges={}, averageRating={}, consistency={}", 
                            subTask.getTaskId(), subTask.getModelName(),
                            aiScoreResult.judges().size(), aiScoreResult.averageRating(), aiScoreResult.consistency());
                } catch (Exception e) {
                    log.error("AI评分失败: taskId={}, model={}, prompt={}", 
                            subTask.getTaskId(), subTask.getModelName(), subTask.getPromptTitle(), e);
                }
            }

            // 更新模型使用统计（全局）
            int totalTokens = inputTokens.get() + outputTokens.get();
            modelService.updateModelUsage(subTask.getModelName(), totalTokens, cost);

            // 更新用户-模型使用统计
            if (subTask.getUserId() != null) {
                userModelUsageService.updateUserModelUsage(
                        subTask.getUserId(),
                        subTask.getModelName(),
                        totalTokens,
                        cost
                );
            }

            batchTestService.updateTaskProgress(
                    subTask.getTaskId(),
                    subTask.getModelName(),
                    subTask.getPromptTitle()
            );

            String modelTag = subTask.getModelName() != null ? subTask.getModelName() : "unknown";
            meterRegistry.timer("batch_test.subtask.duration", "model", modelTag)
                    .record(responseTimeMs, java.util.concurrent.TimeUnit.MILLISECONDS);

            log.info("子任务完成: taskId={}, model={}, prompt={}, responseTime={}ms, tokens={}/{}, cost=${}, reasoningLength={}",
                    subTask.getTaskId(), subTask.getModelName(), subTask.getPromptTitle(),
                    responseTimeMs, inputTokens.get(), outputTokens.get(), cost,
                    reasoningText != null ? reasoningText.length() : 0);

        } catch (Exception e) {
            String modelTag = subTask.getModelName() != null ? subTask.getModelName() : "unknown";
            meterRegistry.counter("batch_test.subtask.failures", "model", modelTag).increment();

            log.error("子任务执行失败: taskId={}, model={}, prompt={}", 
                    subTask.getTaskId(), subTask.getModelName(), subTask.getPromptTitle(), e);

            try {
                batchTestService.markTaskFailed(subTask.getTaskId());
            } catch (Exception ex) {
                log.error("标记任务失败异常: taskId={}", subTask.getTaskId(), ex);
            }
        }
    }

    /**
     * 计算成本（使用 ModelService.getModelPricing，带 Redis 缓存）
     */
    private BigDecimal calculateCost(String modelName, int inputTokens, int outputTokens) {
        ModelPricingVO pricing = modelService.getModelPricing(modelName);
        if (pricing == null) {
            log.warn("模型不存在，使用默认价格: modelName={}", modelName);
            double defaultCost = (inputTokens * ConversationConstant.DEFAULT_INPUT_PRICE_PER_MILLION
                    + outputTokens * ConversationConstant.DEFAULT_OUTPUT_PRICE_PER_MILLION)
                    / ConversationConstant.TOKENS_PER_MILLION;
            return BigDecimal.valueOf(defaultCost);
        }

        BigDecimal inputPrice = pricing.getInputPrice() != null
                ? pricing.getInputPrice()
                : BigDecimal.valueOf(ConversationConstant.DEFAULT_INPUT_PRICE_PER_MILLION);
        BigDecimal outputPrice = pricing.getOutputPrice() != null
                ? pricing.getOutputPrice()
                : BigDecimal.valueOf(ConversationConstant.DEFAULT_OUTPUT_PRICE_PER_MILLION);

        BigDecimal inputCost = inputPrice
                .multiply(BigDecimal.valueOf(inputTokens))
                .divide(BigDecimal.valueOf(ConversationConstant.TOKENS_PER_MILLION), 6, RoundingMode.HALF_UP);
        BigDecimal outputCost = outputPrice
                .multiply(BigDecimal.valueOf(outputTokens))
                .divide(BigDecimal.valueOf(ConversationConstant.TOKENS_PER_MILLION), 6, RoundingMode.HALF_UP);

        return inputCost.add(outputCost);
    }

    /**
     * 检查是否启用AI评分
     */
    private boolean checkEnableAiScoring(TestTask task) {
        if (task.getConfig() == null || task.getConfig().trim().isEmpty()) {
            return false;
        }
        try {
            Map<String, Object> configMap = JSONUtil.parseObj(task.getConfig());
            Object enableAiScoring = configMap.get("enableAiScoring");
            if (enableAiScoring instanceof Boolean) {
                return (Boolean) enableAiScoring;
            }
            if (enableAiScoring instanceof String) {
                return Boolean.parseBoolean((String) enableAiScoring);
            }
        } catch (Exception e) {
            log.warn("解析AI评分配置失败: taskId={}, error={}", task.getId(), e.getMessage());
        }
        return false;
    }
}
