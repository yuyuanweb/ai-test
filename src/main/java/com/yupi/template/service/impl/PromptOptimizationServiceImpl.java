package com.yupi.template.service.impl;

import com.yupi.template.exception.BusinessException;
import com.yupi.template.exception.ErrorCode;
import com.yupi.template.model.dto.prompt.OptimizationSuggestion;
import com.yupi.template.model.vo.PromptOptimizationVO;
import com.yupi.template.service.PromptOptimizationService;
import com.yupi.template.service.UserModelUsageService;
import com.yupi.template.utils.AiRetryHelper;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.metadata.Usage;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.ArrayList;

/**
 * 提示词优化服务实现
 *
 * @author <a href="https://codefather.cn">编程导航学习圈</a>
 */
@Service
@Slf4j
public class PromptOptimizationServiceImpl implements PromptOptimizationService {

    private static final String DEFAULT_EVALUATION_MODEL = "qwen/qwen-plus";

    private static final String OPTIMIZATION_PROMPT_TEMPLATE = """
            你是一位专业的提示词工程专家。请分析以下提示词，并提供优化建议。

            ## 原始提示词
            {originalPrompt}

            {aiResponseSection}

            ## 分析维度
            请从以下5个维度分析提示词：
            1. **角色设定**：是否明确指定了AI的角色和身份？
            2. **任务描述**：任务目标是否清晰、具体？
            3. **输出格式**：是否明确指定了期望的输出格式？
            4. **思维链**：是否引导AI进行逐步思考？
            5. **Few-shot示例**：是否提供了示例来帮助AI理解需求？

            ## 输出要求
            请以JSON格式输出分析结果：
            {
              "issues": ["问题1", "问题2", ...],
              "optimized_prompt": "优化后的完整提示词",
              "improvements": ["改进点1", "改进点2", ...]
            }

            要求：
            - issues: 列出当前提示词存在的问题（至少3个维度的问题）
            - optimized_prompt: 提供优化后的完整提示词，保持原意但更加清晰、具体
            - improvements: 说明每个优化带来的具体提升（至少3个改进点）
            """;

    @Resource
    private ChatClient chatClient;

    @Resource
    private UserModelUsageService userModelUsageService;

    @Resource
    private com.yupi.template.service.BudgetService budgetService;

    @Override
    public PromptOptimizationVO optimizePrompt(String originalPrompt, String aiResponse, String evaluationModel, Long userId) {
        if (originalPrompt == null || originalPrompt.trim().isEmpty()) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "原始提示词不能为空");
        }

        String model = evaluationModel != null && !evaluationModel.trim().isEmpty()
                ? evaluationModel
                : DEFAULT_EVALUATION_MODEL;

        try {
            String aiResponseSection = "";
            if (aiResponse != null && !aiResponse.trim().isEmpty()) {
                aiResponseSection = "\n## AI回答\n" + aiResponse + "\n";
            }

            String analysisPrompt = OPTIMIZATION_PROMPT_TEMPLATE
                    .replace("{originalPrompt}", originalPrompt)
                    .replace("{aiResponseSection}", aiResponseSection);

            log.info("开始提示词优化分析: promptLength={}, hasResponse={}, model={}, userId={}",
                    originalPrompt.length(), aiResponse != null && !aiResponse.trim().isEmpty(), model, userId);

            ChatResponse chatResponse = AiRetryHelper.runWithRetry(() ->
                    chatClient.prompt()
                            .user(analysisPrompt)
                            .options(OpenAiChatOptions.builder()
                                    .model(model)
                                    .temperature(0.3)
                                    .build())
                            .call()
                            .chatResponse()
            );

            String responseContent = chatResponse.getResult().getOutput().getText();
            OptimizationSuggestion suggestion = responseContent != null
                    ? cn.hutool.json.JSONUtil.toBean(responseContent, OptimizationSuggestion.class)
                    : new OptimizationSuggestion(new ArrayList<>(), "", new ArrayList<>());

            // 统计模型使用量
            int inputTokens = 0;
            int outputTokens = 0;
            if (chatResponse.getMetadata() != null && chatResponse.getMetadata().getUsage() != null) {
                Usage usage = chatResponse.getMetadata().getUsage();
                inputTokens = usage.getPromptTokens() != null ? usage.getPromptTokens() : 0;
                outputTokens = usage.getCompletionTokens() != null ? usage.getCompletionTokens() : 0;
            }
            int totalTokens = inputTokens + outputTokens;

            if (userId != null && totalTokens > 0) {
                BigDecimal cost = calculateCost(model, inputTokens, outputTokens);
                userModelUsageService.updateUserModelUsage(userId, model, totalTokens, cost);
                budgetService.addCost(userId, cost);
                log.info("提示词优化统计: userId={}, model={}, tokens={}, cost={}", userId, model, totalTokens, cost);
            }

            log.info("提示词优化分析完成: issuesCount={}, improvementsCount={}, tokens={}",
                    suggestion.issues() != null ? suggestion.issues().size() : 0,
                    suggestion.improvements() != null ? suggestion.improvements().size() : 0,
                    totalTokens);

            PromptOptimizationVO vo = new PromptOptimizationVO();
            vo.setIssues(suggestion.issues() != null ? suggestion.issues() : new ArrayList<>());
            vo.setOptimizedPrompt(suggestion.optimizedPrompt() != null ? suggestion.optimizedPrompt() : "");
            vo.setImprovements(suggestion.improvements() != null ? suggestion.improvements() : new ArrayList<>());

            return vo;
        } catch (Exception e) {
            log.error("提示词优化分析失败: prompt={}, error={}", originalPrompt, e.getMessage(), e);
            throw new BusinessException(ErrorCode.SYSTEM_ERROR,
                    "提示词优化分析失败: " + (e.getMessage() != null ? e.getMessage() : "未知错误"));
        }
    }

    /**
     * 计算成本（简化版本，使用默认价格）
     */
    private BigDecimal calculateCost(String model, int inputTokens, int outputTokens) {
        double inputPrice = 0.001;
        double outputPrice = 0.002;

        if (model != null && model.contains("qwen")) {
            inputPrice = 0.0005;
            outputPrice = 0.001;
        }

        double cost = (inputTokens * inputPrice + outputTokens * outputPrice) / 1000.0;
        return BigDecimal.valueOf(cost);
    }
}
