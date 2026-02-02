package com.yupi.template.service.impl;

import com.mybatisflex.core.query.QueryWrapper;
import com.yupi.template.exception.BusinessException;
import com.yupi.template.exception.ErrorCode;
import com.yupi.template.mapper.ModelMapper;
import com.yupi.template.model.dto.evaluation.AIScoreResult;
import com.yupi.template.model.dto.evaluation.EvaluationResult;
import com.yupi.template.model.dto.evaluation.JudgeScore;
import com.yupi.template.model.entity.Model;
import com.yupi.template.service.AIScoringService;
import com.yupi.template.utils.AiRetryHelper;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

/**
 * AI评分服务实现
 *
 * @author <a href="https://codefather.cn">编程导航学习圈</a>
 */
@Service
@Slf4j
public class AIScoringServiceImpl implements AIScoringService {

    private static final String JUDGE_MODEL = "qwen/qwen-plus";

    private static final String SCORING_PROMPT_TEMPLATE = """
            你是一位专业的AI评测专家。请对以下AI模型的回答进行评分。

            ## 问题
            {question}

            ## 模型回答
            {model_response}

            ## 评分标准
            1. 准确性（30分）：答案是否正确，事实是否准确
            2. 相关性（20分）：是否切题，是否回答了问题
            3. 完整性（20分）：是否全面，是否遗漏重要信息
            4. 清晰度（15分）：表达是否清楚，逻辑是否连贯
            5. 创意性（15分）：是否有独特见解或创新点

            请以JSON格式输出评分结果：
            {
              "scores": {
                "accuracy": 分数,
                "relevance": 分数,
                "completeness": 分数,
                "clarity": 分数,
                "creativity": 分数
              },
              "total_score": 总分（100分制）,
              "rating": 评级（1-10分）,
              "comment": "简短评价（50字以内）"
            }
            """;

    @Resource
    private ChatClient chatClient;

    @Resource
    private ModelMapper modelMapper;

    private static final int MAX_JUDGES = 3;
    private static final int MIN_JUDGES = 2;

    @Override
    public EvaluationResult score(String question, String modelResponse) {
        if (question == null || question.trim().isEmpty()) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "问题不能为空");
        }
        if (modelResponse == null || modelResponse.trim().isEmpty()) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "模型回答不能为空");
        }

        try {
            String scoringPrompt = SCORING_PROMPT_TEMPLATE
                    .replace("{question}", question)
                    .replace("{model_response}", modelResponse);

            log.info("开始AI评分: questionLength={}, responseLength={}", 
                    question.length(), modelResponse.length());

            EvaluationResult result = AiRetryHelper.runWithRetry(() ->
                    chatClient.prompt()
                            .user(scoringPrompt)
                            .options(OpenAiChatOptions.builder()
                                    .model(JUDGE_MODEL)
                                    .temperature(0.3)
                                    .build())
                            .call()
                            .entity(EvaluationResult.class)
            );

            log.info("AI评分完成: totalScore={}, rating={}", 
                    result.totalScore(), result.rating());

            return result;
        } catch (Exception e) {
            log.error("AI评分失败: question={}, error={}", question, e.getMessage(), e);
            throw new BusinessException(ErrorCode.SYSTEM_ERROR,
                    "AI评分失败: " + (e.getMessage() != null ? e.getMessage() : "未知错误"));
        }
    }

    @Override
    public AIScoreResult scoreWithMultipleJudges(String question, String modelResponse, String testedModelName) {
        if (question == null || question.trim().isEmpty()) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "问题不能为空");
        }
        if (modelResponse == null || modelResponse.trim().isEmpty()) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "模型回答不能为空");
        }

        try {
            List<String> judgeModels = selectJudgeModels(testedModelName);
            if (judgeModels.isEmpty()) {
                log.warn("未找到可用的评委模型，使用单评委模式: testedModel={}", testedModelName);
                EvaluationResult singleResult = score(question, modelResponse);
                JudgeScore judgeScore = new JudgeScore(
                        "qwen/qwen-plus",
                        singleResult.scores(),
                        singleResult.totalScore(),
                        singleResult.rating(),
                        singleResult.comment()
                );
                return new AIScoreResult(
                        List.of(judgeScore),
                        singleResult.rating() != null ? singleResult.rating().doubleValue() : null,
                        0.0
                );
            }

            log.info("开始多评委交叉验证评分: testedModel={}, judges={}, questionLength={}, responseLength={}",
                    testedModelName, judgeModels, question.length(), modelResponse.length());

            String scoringPrompt = SCORING_PROMPT_TEMPLATE
                    .replace("{question}", question)
                    .replace("{model_response}", modelResponse);

            List<CompletableFuture<JudgeScore>> futures = judgeModels.stream()
                    .map(judgeModel -> CompletableFuture.supplyAsync(() -> {
                        try {
                            EvaluationResult result = AiRetryHelper.runWithRetry(() ->
                                    chatClient.prompt()
                                            .user(scoringPrompt)
                                            .options(OpenAiChatOptions.builder()
                                                    .model(judgeModel)
                                                    .temperature(0.3)
                                                    .build())
                                            .call()
                                            .entity(EvaluationResult.class)
                            );

                            log.info("评委{}评分完成: totalScore={}, rating={}",
                                    judgeModel, result.totalScore(), result.rating());

                            return new JudgeScore(
                                    judgeModel,
                                    result.scores(),
                                    result.totalScore(),
                                    result.rating(),
                                    result.comment()
                            );
                        } catch (Exception e) {
                            log.error("评委{}评分失败: {}", judgeModel, e.getMessage(), e);
                            return null;
                        }
                    }))
                    .collect(Collectors.toList());

            List<JudgeScore> judgeScores = futures.stream()
                    .map(CompletableFuture::join)
                    .filter(score -> score != null)
                    .collect(Collectors.toList());

            if (judgeScores.isEmpty()) {
                throw new BusinessException(ErrorCode.SYSTEM_ERROR, "所有评委评分都失败了");
            }

            double averageRating = calculateAverageRating(judgeScores);
            double consistency = calculateConsistency(judgeScores);

            log.info("多评委评分完成: testedModel={}, judges={}, averageRating={}, consistency={}",
                    testedModelName, judgeScores.size(), averageRating, consistency);

            return new AIScoreResult(judgeScores, averageRating, consistency);
        } catch (Exception e) {
            log.error("多评委评分失败: question={}, testedModel={}, error={}",
                    question, testedModelName, e.getMessage(), e);
            throw new BusinessException(ErrorCode.SYSTEM_ERROR, "多评委评分失败: " + e.getMessage());
        }
    }

    /**
     * 选择评委模型（排除被测试的模型）
     */
    private List<String> selectJudgeModels(String testedModelName) {
        QueryWrapper queryWrapper = QueryWrapper.create()
                .select("id")
                .where("isDelete = 0")
                .and("isChina = 1")
                .orderBy("recommended", false)
                .orderBy("updateTime", false);

        List<Model> domesticModels = modelMapper.selectListByQuery(queryWrapper);

        List<String> candidateModels = domesticModels.stream()
                .map(Model::getId)
                .filter(modelId -> {
                    if (testedModelName == null) {
                        return true;
                    }
                    return !modelId.equals(testedModelName) && !isSameProvider(modelId, testedModelName);
                })
                .collect(Collectors.toList());

        int count = Math.min(MAX_JUDGES, Math.max(MIN_JUDGES, candidateModels.size()));
        return candidateModels.stream()
                .limit(count)
                .collect(Collectors.toList());
    }

    /**
     * 判断两个模型是否来自同一提供商（避免同一提供商的不同模型互相评分）
     */
    private boolean isSameProvider(String model1, String model2) {
        if (model1 == null || model2 == null) {
            return false;
        }
        String provider1 = model1.contains("/") ? model1.split("/")[0] : "";
        String provider2 = model2.contains("/") ? model2.split("/")[0] : "";
        return provider1.equals(provider2) && !provider1.isEmpty();
    }

    /**
     * 计算平均评分
     */
    private double calculateAverageRating(List<JudgeScore> judgeScores) {
        if (judgeScores.isEmpty()) {
            return 0.0;
        }
        double sum = judgeScores.stream()
                .filter(score -> score.rating() != null)
                .mapToDouble(score -> score.rating().doubleValue())
                .sum();
        return sum / judgeScores.size();
    }

    /**
     * 计算评分一致性（标准差）
     */
    private double calculateConsistency(List<JudgeScore> judgeScores) {
        if (judgeScores.size() < 2) {
            return 0.0;
        }

        double average = calculateAverageRating(judgeScores);
        double variance = judgeScores.stream()
                .filter(score -> score.rating() != null)
                .mapToDouble(score -> {
                    double diff = score.rating().doubleValue() - average;
                    return diff * diff;
                })
                .average()
                .orElse(0.0);

        return Math.sqrt(variance);
    }
}
