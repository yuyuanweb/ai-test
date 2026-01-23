package com.yupi.template.model.converter;


import com.yupi.template.model.dto.openrouter.OpenRouterChoice;
import com.yupi.template.model.dto.openrouter.OpenRouterDelta;
import com.yupi.template.model.dto.openrouter.OpenRouterResponse;
import com.yupi.template.model.dto.openrouter.OpenRouterUsage;
import com.yupi.template.model.dto.standard.StandardChoice;
import com.yupi.template.model.dto.standard.StandardDelta;
import com.yupi.template.model.dto.standard.StandardResponse;
import com.yupi.template.model.dto.standard.StandardUsage;

import java.util.ArrayList;
import java.util.List;

/**
 * 响应转换器 - 将OpenRouter响应转换为标准响应格式
 */
public class ResponseConverter {

    /**
     * 将OpenRouter响应转换为标准响应
     * 主要功能：将reasoning字段转换为reasoning_content
     */
    public static StandardResponse convertToStandard(OpenRouterResponse openRouterResponse) {
        if (openRouterResponse == null) {
            return null;
        }

        StandardResponse standardResponse = new StandardResponse();

        // 复制基本字段
        standardResponse.setId(openRouterResponse.getId());
        standardResponse.setModel(openRouterResponse.getModel());
        standardResponse.setObject(openRouterResponse.getObject());
        standardResponse.setCreated(openRouterResponse.getCreated());
        standardResponse.setSystemFingerprint(openRouterResponse.getSystemFingerprint());

        // 转换choices
        if (openRouterResponse.getChoices() != null) {
            List<StandardChoice> standardChoices = new ArrayList<>();
            for (OpenRouterChoice openRouterChoice : openRouterResponse.getChoices()) {
                StandardChoice standardChoice = convertChoice(openRouterChoice);
                if (standardChoice != null) {
                    standardChoices.add(standardChoice);
                }
            }
            standardResponse.setChoices(standardChoices);
        }

        // 转换usage
        if (openRouterResponse.getUsage() != null) {
            StandardUsage standardUsage = convertUsage(openRouterResponse.getUsage());
            standardResponse.setUsage(standardUsage);
        }

        return standardResponse;
    }

    /**
     * 转换Choice对象
     */
    private static StandardChoice convertChoice(OpenRouterChoice openRouterChoice) {
        if (openRouterChoice == null) {
            return null;
        }

        StandardChoice standardChoice = new StandardChoice();
        standardChoice.setIndex(openRouterChoice.getIndex());
        standardChoice.setFinishReason(openRouterChoice.getFinishReason());
        standardChoice.setLogprobs(openRouterChoice.getLogprobs());
        if (openRouterChoice.getDelta() != null) {
            StandardDelta standardDelta = convertDelta(openRouterChoice.getDelta());
            standardChoice.setDelta(standardDelta);
        }

        return standardChoice;
    }

    /**
     * 转换Delta对象 - 核心转换逻辑
     * 将reasoning字段转换为reasoning_content
     */
    private static StandardDelta convertDelta(OpenRouterDelta openRouterDelta) {
        if (openRouterDelta == null) {
            return null;
        }

        StandardDelta standardDelta = new StandardDelta();
        standardDelta.setRole(openRouterDelta.getRole());
        standardDelta.setContent(openRouterDelta.getContent());
        if (openRouterDelta.getReasoning() != null && !openRouterDelta.getReasoning().isEmpty()) {
            standardDelta.setReasoningContent(openRouterDelta.getReasoning());
        }

        return standardDelta;
    }

    /**
     * 转换Usage对象
     */
    private static StandardUsage convertUsage(OpenRouterUsage openRouterUsage) {
        if (openRouterUsage == null) {
            return null;
        }

        StandardUsage standardUsage = new StandardUsage();
        standardUsage.setPromptTokens(openRouterUsage.getPromptTokens());
        standardUsage.setCompletionTokens(openRouterUsage.getCompletionTokens());
        standardUsage.setTotalTokens(openRouterUsage.getTotalTokens());
        standardUsage.setCost(openRouterUsage.getCost());
        standardUsage.setIsByok(openRouterUsage.getIsByok());

        return standardUsage;
    }
}
