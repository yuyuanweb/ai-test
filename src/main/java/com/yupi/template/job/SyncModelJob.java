package com.yupi.template.job;

import cn.hutool.json.JSONUtil;
import com.yupi.template.constant.ConversationConstant;
import com.yupi.template.mapper.ModelMapper;
import com.yupi.template.model.dto.openrouter.OpenRouterModelResponse;
import com.yupi.template.model.entity.Model;
import com.yupi.template.service.ModelService;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * 同步OpenRouter模型列表定时任务
 *
 * @author <a href="https://codefather.cn">编程导航学习圈</a>
 */
@Component
@Slf4j
public class SyncModelJob {

    @Resource
    private ModelMapper modelMapper;

    @Resource
    private ModelService modelService;

    @Value("${spring.ai.openai.api-key}")
    private String openRouterApiKey;

    private static final String OPENROUTER_MODELS_URL = "https://openrouter.ai/api/v1/models";


    /**
     * 每天凌晨2点执行一次
     * 可以通过配置文件覆盖：job.sync-model.cron
     */
    @Scheduled(cron = "0 0 2 * * ?")
    public void syncModels() {
        log.info("开始同步OpenRouter模型列表");

        try {
            // 调用OpenRouter API获取模型列表
            List<OpenRouterModelResponse.OpenRouterModel> openRouterModels = fetchModelsFromOpenRouter();

            if (openRouterModels == null || openRouterModels.isEmpty()) {
                log.warn("未获取到任何模型数据");
                return;
            }

            log.info("从OpenRouter获取到{}个模型", openRouterModels.size());

            // 转换并保存到数据库
            int successCount = 0;
            int failCount = 0;

            for (OpenRouterModelResponse.OpenRouterModel openRouterModel : openRouterModels) {
                try {
                    Model model = convertToModel(openRouterModel);

                    // 使用replace方式：存在则更新，不存在则插入
                    Model existingModel = modelMapper.selectOneById(model.getId());
                    if (existingModel != null) {
                        model.setCreateTime(existingModel.getCreateTime());
                        modelMapper.update(model);
                    } else {
                        modelMapper.insert(model);
                    }
                    modelService.evictModelPricingCache(model.getId());

                    successCount++;
                } catch (Exception e) {
                    log.error("保存模型失败: {}", openRouterModel.getId(), e);
                    failCount++;
                }
            }

            log.info("模型同步完成: 成功{}个, 失败{}个", successCount, failCount);
        } catch (Exception e) {
            log.error("同步模型列表失败", e);
        }
    }

    /**
     * 从OpenRouter API获取模型列表
     */
    private List<OpenRouterModelResponse.OpenRouterModel> fetchModelsFromOpenRouter() {
        try {
            RestTemplate restTemplate = new RestTemplate();

            // 设置请求头
            HttpHeaders headers = new HttpHeaders();
            headers.set("Authorization", "Bearer " + openRouterApiKey);
            HttpEntity<String> entity = new HttpEntity<>(headers);

            // 调用API
            ResponseEntity<OpenRouterModelResponse> response = restTemplate.exchange(
                    OPENROUTER_MODELS_URL,
                    HttpMethod.GET,
                    entity,
                    OpenRouterModelResponse.class
            );

            if (response.getBody() != null && response.getBody().getData() != null) {
                return response.getBody().getData();
            }
        } catch (Exception e) {
            log.error("调用OpenRouter API失败", e);
        }

        return null;
    }

    /**
     * 转换OpenRouter模型为数据库实体
     */
    private Model convertToModel(OpenRouterModelResponse.OpenRouterModel openRouterModel) {
        // 提取提供商
        String provider = extractProvider(openRouterModel.getId());

        // 转换价格（从每token转为每百万tokens）
        BigDecimal inputPrice = convertPrice(openRouterModel.getPricing().getPrompt());
        BigDecimal outputPrice = convertPrice(openRouterModel.getPricing().getCompletion());


        // 生成标签
        String[] tags = generateTags(openRouterModel);
        String tagsJson = JSONUtil.toJsonStr(tags);

        // 保存原始数据
        String rawData = JSONUtil.toJsonStr(openRouterModel);

        LocalDateTime now = LocalDateTime.now();
        return Model.builder()
                .id(openRouterModel.getId())
                .name(openRouterModel.getName())
                .description(openRouterModel.getDescription())
                .provider(provider)
                .contextLength(openRouterModel.getContextLength())
                .inputPrice(inputPrice)
                .outputPrice(outputPrice)
                .recommended(0)
                .tags(tagsJson)
                .rawData(rawData)
                .createTime(now)
                .updateTime(now)
                .isDelete(0)
                .build();
    }

    /**
     * 从模型ID提取提供商名称
     */
    private String extractProvider(String modelId) {
        if (modelId == null || !modelId.contains("/")) {
            return "Unknown";
        }
        String prefix = modelId.split("/")[0];
        return switch (prefix.toLowerCase()) {
            // 国产厂商
            case "qwen", "alibaba" -> "Alibaba";
            case "deepseek" -> "DeepSeek";
            case "z-ai", "zhipu" -> "Zhipu AI";
            case "moonshotai" -> "Moonshotai";
            case "baidu" -> "Baidu";
            case "tencent" -> "Tencent";
            case "bytedance", "bytedance-seed" -> "Bytedance";
            case "meituan" -> "Meituan";
            // 国外厂商
            case "openai" -> "OpenAI";
            case "anthropic" -> "Anthropic";
            case "google" -> "Google";
            case "meta-llama", "meta" -> "Meta";
            case "mistralai" -> "Mistral AI";
            default -> capitalize(prefix);
        };
    }

    /**
     * 首字母大写
     */
    private String capitalize(String str) {
        if (str == null || str.isEmpty()) {
            return str;
        }
        return str.substring(0, 1).toUpperCase() + str.substring(1);
    }

    /**
     * 转换价格：从每token价格转换为每百万tokens价格
     */
    private BigDecimal convertPrice(String priceStr) {
        try {
            if (priceStr == null || priceStr.isEmpty()) {
                return BigDecimal.ZERO;
            }
            // OpenRouter返回的价格是每token的美元价格（科学计数法字符串）
            BigDecimal pricePerToken = new BigDecimal(priceStr);
            return pricePerToken.multiply(new BigDecimal(ConversationConstant.TOKENS_PER_MILLION));
        } catch (Exception e) {
            log.warn("价格转换失败: {}", priceStr, e);
            return BigDecimal.ZERO;
        }
    }

    /**
     * 生成模型标签
     */
    private String[] generateTags(OpenRouterModelResponse.OpenRouterModel model) {
        List<String> tags = new ArrayList<>();

        // 根据模态添加标签
        if (model.getArchitecture() != null) {
            if (model.getArchitecture().getInputModalities() != null) {
                if (model.getArchitecture().getInputModalities().contains("image")) {
                    tags.add("多模态");
                }
            }
            if (model.getArchitecture().getOutputModalities() != null) {
                if (model.getArchitecture().getOutputModalities().contains("image")) {
                    tags.add("图像生成");
                }
            }
        }

        // 根据模型名称添加标签
        String name = model.getName().toLowerCase();
        String id = model.getId().toLowerCase();

        if (name.contains("code") || id.contains("code")) {
            tags.add("代码");
        }
        if (name.contains("mini") || name.contains("flash") || name.contains("haiku")) {
            tags.add("快速");
        }
        if (name.contains("vision") || name.contains("multimodal")) {
            tags.add("多模态");
        }
        if (id.contains("qwen") || id.contains("glm") || id.contains("ernie")) {
            tags.add("中文");
        }

        // 默认添加文本生成标签
        if (tags.isEmpty()) {
            tags.add("文本生成");
        } else if (!tags.contains("文本生成")) {
            tags.add("文本生成");
        }

        return tags.toArray(new String[0]);
    }
}

