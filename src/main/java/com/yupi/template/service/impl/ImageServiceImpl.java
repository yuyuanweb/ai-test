package com.yupi.template.service.impl;

import cn.hutool.core.codec.Base64;
import cn.hutool.core.io.FileUtil;
import cn.hutool.core.util.IdUtil;
import cn.hutool.json.JSONUtil;
import com.yupi.template.exception.BusinessException;
import com.yupi.template.exception.ErrorCode;
import com.yupi.template.mapper.ConversationMessageMapper;
import com.yupi.template.model.dto.image.GenerateImageRequest;
import com.yupi.template.model.dto.image.OpenRouterImageRequestBody;
import com.yupi.template.model.dto.image.OpenRouterImageResponse;
import com.yupi.template.model.entity.Conversation;
import com.yupi.template.model.entity.ConversationMessage;
import com.yupi.template.model.entity.Model;
import com.yupi.template.model.entity.User;
import com.yupi.template.model.enums.ConversationTypeEnum;
import com.yupi.template.model.enums.MessageRoleEnum;
import com.yupi.template.model.vo.GeneratedImageVO;
import com.yupi.template.model.vo.ImageStreamChunkVO;
import com.yupi.template.service.ImageService;
import com.yupi.template.service.ModelService;
import com.yupi.template.service.UserModelUsageService;
import com.yupi.template.service.UserService;
import com.yupi.template.utils.TencentCosUtil;
import com.mybatisflex.core.query.QueryWrapper;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Flux;

import java.io.File;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * 图片生成服务实现类（通过 OpenRouter 多模态模型）
 *
 * @author <a href="https://codefather.cn">编程导航学习圈</a>
 */
@Service
@Slf4j
public class ImageServiceImpl implements ImageService {

    @Value("${spring.ai.openai.base-url}")
    private String openRouterBaseUrl;

    @Value("${spring.ai.openai.api-key}")
    private String openRouterApiKey;

    @Resource
    private ModelService modelService;

    @Resource
    private UserService userService;

    @Resource
    private UserModelUsageService userModelUsageService;

    @Resource
    private TencentCosUtil tencentCosUtil;

    @Resource
    private ConversationMessageMapper conversationMessageMapper;

    @Resource
    private com.yupi.template.mapper.ConversationMapper conversationMapper;

    @Resource
    private com.yupi.template.mapper.ModelMapper modelMapper;

    @Resource
    private WebClient.Builder webClientBuilder;

    private static final String COS_IMAGE_PREFIX = "/aitest/%s/generated/";

    private static final long ONE_M = 1024 * 1024L;

    @Override
    public List<GeneratedImageVO> generateImages(GenerateImageRequest request, Long userId) {
        if (request == null) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "请求参数不能为空");
        }
        log.info("图片生成请求参数: conversationId={}, conversationType={}, models={}, variantIndex={}, messageIndex={}, isAnonymous={}",
                request.getConversationId(), request.getConversationType(), request.getModels(),
                request.getVariantIndex(), request.getMessageIndex(), request.getIsAnonymous());
        if (request.getCount() == null || request.getCount() <= 0) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "生成数量必须大于 0");
        }
        if (userId == null || userId <= 0) {
            throw new BusinessException(ErrorCode.NOT_LOGIN_ERROR);
        }

        // 匿名模式处理：随机选择一个支持图片生成的模型
        if (Boolean.TRUE.equals(request.getIsAnonymous())) {
            log.info("匿名图片生成模式，随机选择模型");
            Model randomModel = selectRandomImageGenModel();
            if (randomModel == null) {
                throw new BusinessException(ErrorCode.NOT_FOUND_ERROR, "没有可用的图片生成模型");
            }
            request.setModel(randomModel.getId());
            log.info("匿名模式选择的模型: {}", randomModel.getId());
        }

        // 验证模型参数
        if (request.getModel() == null || request.getModel().isBlank()) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "模型名称不能为空");
        }

        // 校验模型
        Model model = modelService.getModelById(request.getModel());
        if (model == null || (model.getIsDelete() != null && model.getIsDelete() == 1)) {
            throw new BusinessException(ErrorCode.NOT_FOUND_ERROR, "模型不存在或已下线");
        }
        if (model.getSupportsMultimodal() == null || model.getSupportsMultimodal() != 1) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "当前模型不支持图片多模态，请更换模型");
        }

        // 查询用户
        User loginUser = userService.getById(userId);
        if (loginUser == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND_ERROR, "用户不存在");
        }

        try {
            String url = buildCompletionsUrl();
            RestTemplate restTemplate = new RestTemplate();

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.setBearerAuth(openRouterApiKey);

            OpenRouterImageRequestBody body = buildRequestBody(request, false);
            String requestBodyJson = JSONUtil.toJsonStr(body);
            log.info("OpenRouter 图片生成请求参数: model={}, prompt={}, referenceImages={}, requestBody={}",
                    request.getModel(), request.getPrompt(),
                    request.getReferenceImageUrls() != null ? request.getReferenceImageUrls().size() : 0,
                    requestBodyJson);

            HttpEntity<String> entity = new HttpEntity<>(requestBodyJson, headers);

            long startTime = System.currentTimeMillis();
            ResponseEntity<String> response = restTemplate.postForEntity(url, entity, String.class);
            long costTime = System.currentTimeMillis() - startTime;
            log.info("调用 OpenRouter 图片生成接口完成, model={}, 状态码={}, 耗时={}ms",
                    request.getModel(), response.getStatusCode().value(), costTime);

            if (!response.getStatusCode().is2xxSuccessful() || response.getBody() == null) {
                throw new BusinessException(ErrorCode.SYSTEM_ERROR, "调用图片生成服务失败");
            }

            String bodyString = response.getBody();
            log.info("OpenRouter 图片生成响应内容: {}", bodyString);

            OpenRouterImageResponse imageResponse;
            try {
                imageResponse = JSONUtil.toBean(bodyString, OpenRouterImageResponse.class);
            } catch (Exception e) {
                log.error("解析 OpenRouter 响应失败, bodyString={}", bodyString, e);
                throw new BusinessException(ErrorCode.SYSTEM_ERROR, "解析图片生成响应失败：" + e.getMessage());
            }

            if (imageResponse == null) {
                log.error("解析后的 imageResponse 为 null, bodyString={}", bodyString);
                throw new BusinessException(ErrorCode.SYSTEM_ERROR, "图片生成失败：响应解析为 null");
            }

            log.debug("解析后的 imageResponse: choices={}, usage={}",
                    imageResponse.getChoices() != null ? imageResponse.getChoices().size() : 0,
                    imageResponse.getUsage());

            if (imageResponse.getChoices() == null || imageResponse.getChoices().isEmpty()) {
                log.error("imageResponse.choices 为空或 null, bodyString={}", bodyString);
                throw new BusinessException(ErrorCode.SYSTEM_ERROR, "图片生成失败：未返回结果");
            }

            OpenRouterImageResponse.OpenRouterUsage usage = imageResponse.getUsage();
            int promptTokens = usage != null && usage.getPromptTokens() != null ? usage.getPromptTokens() : 0;
            int completionTokens = usage != null && usage.getCompletionTokens() != null ? usage.getCompletionTokens() : 0;
            int totalTokens = usage != null && usage.getTotalTokens() != null
                    ? usage.getTotalTokens()
                    : promptTokens + completionTokens;
            Double cost = usage != null ? usage.getCost() : null;

            List<GeneratedImageVO> resultList = new ArrayList<>();
            int imageIndex = 0;
            int requestedCount = request.getCount() != null ? request.getCount() : 1;
            StringBuilder thinkingContent = new StringBuilder();

            for (OpenRouterImageResponse.OpenRouterChoice choice : imageResponse.getChoices()) {
                // 已达到请求数量，停止处理
                if (resultList.size() >= requestedCount) {
                    log.info("已达到请求的图片数量 {}, 停止处理", requestedCount);
                    break;
                }

                log.debug("处理 choice: finishReason={}", choice.getFinishReason());
                OpenRouterImageResponse.OpenRouterMessage message = choice.getMessage();
                if (message == null) {
                    log.warn("choice.message 为 null, choice={}", JSONUtil.toJsonStr(choice));
                    continue;
                }

                // 提取思考内容（content 字段可能包含模型的思考过程）
                if (message.getContent() != null && !message.getContent().isEmpty()) {
                    thinkingContent.append(message.getContent());
                    log.info("提取到思考内容: {} 字符", message.getContent().length());
                }

                log.debug("处理 message: role={}, content={}, images={}",
                        message.getRole(), message.getContent(),
                        message.getImages() != null ? message.getImages().size() : 0);
                if (message.getImages() == null || message.getImages().isEmpty()) {
                    log.warn("message.images 为空或 null, message={}", JSONUtil.toJsonStr(message));
                    continue;
                }
                for (OpenRouterImageResponse.OpenRouterImagePart imgPart : message.getImages()) {
                    // 已达到请求数量，停止处理
                    if (resultList.size() >= requestedCount) {
                        log.info("已达到请求的图片数量 {}, 停止处理", requestedCount);
                        break;
                    }

                    if (imgPart == null) {
                        log.warn("imgPart 为 null");
                        continue;
                    }
                    log.debug("处理 imgPart: type={}, imageUrl={}",
                            imgPart.getType(),
                            imgPart.getImageUrl() != null ? "存在" : "null");
                    if (imgPart.getImageUrl() == null) {
                        log.warn("imgPart.imageUrl 为 null, imgPart={}", JSONUtil.toJsonStr(imgPart));
                        continue;
                    }
                    String urlData = imgPart.getImageUrl().getUrl();
                    if (urlData == null || urlData.isEmpty()) {
                        log.warn("imgPart.imageUrl.url 为空或 null");
                        continue;
                    }
                    log.debug("找到图片 URL, 长度={}, 前缀={}",
                            urlData.length(),
                            urlData.length() > 50 ? urlData.substring(0, 50) + "..." : urlData);

                    String imageUrl = handleImageData(urlData, loginUser);

                    GeneratedImageVO vo = GeneratedImageVO.builder()
                            .url(imageUrl)
                            .modelName(request.getModel())
                            .index(imageIndex)
                            .inputTokens(promptTokens)
                            .outputTokens(completionTokens)
                            .totalTokens(totalTokens)
                            .cost(cost)
                            .build();
                    resultList.add(vo);
                    imageIndex++;
                }
            }

            log.info("图片生成完成，请求数量={}, 实际返回数量={}, 思考内容长度={}",
                    requestedCount, resultList.size(), thinkingContent.length());

            // 将思考内容设置到 request 中，供 generateImagesStream 使用
            if (thinkingContent.length() > 0) {
                request.setReasoning(thinkingContent.toString());
            }

            if (resultList.isEmpty()) {
                log.error("未解析到任何图片结果, imageResponse={}", JSONUtil.toJsonStr(imageResponse));
                throw new BusinessException(ErrorCode.SYSTEM_ERROR, "图片生成失败：未解析到图片结果");
            }

            // 更新使用统计
            if (totalTokens > 0) {
                BigDecimal costValue = cost != null ? BigDecimal.valueOf(cost) : BigDecimal.ZERO;
                modelService.updateModelUsage(request.getModel(), totalTokens, costValue);
                userModelUsageService.updateUserModelUsage(userId, request.getModel(), totalTokens, costValue);
            }

            // 保存到会话（如果提供了 conversationId 或 models）
            String conversationId = request.getConversationId();
            Integer savedMessageIndex = null;
            if (conversationId != null && !conversationId.trim().isEmpty()) {
                // 验证会话是否存在且属于当前用户
                Conversation conversation = conversationMapper.selectOneById(conversationId);
                if (conversation == null || !conversation.getUserId().equals(userId)) {
                    log.warn("会话不存在或不属于当前用户，跳过保存: conversationId={}, userId={}", conversationId, userId);
                } else {
                    savedMessageIndex = saveToConversation(conversationId, userId, request, resultList, promptTokens, completionTokens, totalTokens, cost);
                    // 将 conversationId 和 messageIndex 添加到所有返回结果中
                    for (GeneratedImageVO vo : resultList) {
                        vo.setConversationId(conversationId);
                        vo.setMessageIndex(savedMessageIndex);
                    }
                }
            } else if (request.getModels() != null && !request.getModels().isEmpty()) {
                // 如果没有 conversationId 但有 models，创建新会话
                conversationId = createConversationForImageGen(userId, request);
                savedMessageIndex = saveToConversation(conversationId, userId, request, resultList, promptTokens, completionTokens, totalTokens, cost);
                // 将 conversationId 和 messageIndex 添加到所有返回结果中
                for (GeneratedImageVO vo : resultList) {
                    vo.setConversationId(conversationId);
                    vo.setMessageIndex(savedMessageIndex);
                }
            }

            return resultList;
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            log.error("图片生成异常, model={}, prompt={}", request.getModel(), request.getPrompt(), e);
            throw new BusinessException(ErrorCode.SYSTEM_ERROR, "图片生成失败：" + e.getMessage());
        }
    }

    @Override
    public Flux<ServerSentEvent<ImageStreamChunkVO>> generateImagesStream(GenerateImageRequest request, Long userId) {
        if (request == null) {
            return Flux.error(new BusinessException(ErrorCode.PARAMS_ERROR, "请求参数不能为空"));
        }
        if (request.getCount() == null || request.getCount() <= 0) {
            return Flux.error(new BusinessException(ErrorCode.PARAMS_ERROR, "生成数量必须大于 0"));
        }
        if (userId == null || userId <= 0) {
            return Flux.error(new BusinessException(ErrorCode.NOT_LOGIN_ERROR));
        }

        // 匿名模式处理：随机选择一个支持图片生成的模型
        if (Boolean.TRUE.equals(request.getIsAnonymous())) {
            log.info("匿名图片生成模式，随机选择模型");
            Model randomModel = selectRandomImageGenModel();
            if (randomModel == null) {
                return Flux.error(new BusinessException(ErrorCode.NOT_FOUND_ERROR, "没有可用的图片生成模型"));
            }
            request.setModel(randomModel.getId());
            log.info("匿名模式选择的模型: {}", randomModel.getId());
        }

        // 验证模型参数
        if (request.getModel() == null || request.getModel().isBlank()) {
            return Flux.error(new BusinessException(ErrorCode.PARAMS_ERROR, "模型名称不能为空"));
        }

        Model model = modelService.getModelById(request.getModel());
        if (model == null || (model.getIsDelete() != null && model.getIsDelete() == 1)) {
            return Flux.error(new BusinessException(ErrorCode.NOT_FOUND_ERROR, "模型不存在或已下线"));
        }
        if (model.getSupportsMultimodal() == null || model.getSupportsMultimodal() != 1) {
            return Flux.error(new BusinessException(ErrorCode.PARAMS_ERROR, "当前模型不支持图片多模态，请更换模型"));
        }

        // 直接调用非流式方法生成图片
        return Flux.defer(() -> {
            try {
                log.info("开始图片生成（单次 API 调用）...");
                List<GeneratedImageVO> images = generateImages(request, userId);

                List<ServerSentEvent<ImageStreamChunkVO>> allEvents = new ArrayList<>();
                String conversationId = null;
                Integer messageIndex = null;

                // 如果有思考内容，先发送思考事件
                String reasoning = request.getReasoning();
                if (reasoning != null && !reasoning.isEmpty()) {
                    allEvents.add(ServerSentEvent.<ImageStreamChunkVO>builder()
                            .data(ImageStreamChunkVO.builder()
                                    .type("thinking")
                                    .thinking(reasoning)
                                    .fullThinking(reasoning)
                                    .modelName(request.getModel())
                                    .variantIndex(request.getVariantIndex())
                                    .build())
                            .build());
                }

                for (GeneratedImageVO img : images) {
                    if (conversationId == null && img.getConversationId() != null) {
                        conversationId = img.getConversationId();
                    }
                    if (messageIndex == null && img.getMessageIndex() != null) {
                        messageIndex = img.getMessageIndex();
                    }

                    allEvents.add(ServerSentEvent.<ImageStreamChunkVO>builder()
                            .data(ImageStreamChunkVO.builder()
                                    .type("image")
                                    .image(img)
                                    .conversationId(img.getConversationId())
                                    .messageIndex(img.getMessageIndex())
                                    .variantIndex(request.getVariantIndex())
                                    .modelName(request.getModel())
                                    .build())
                            .build());
                }

                allEvents.add(ServerSentEvent.<ImageStreamChunkVO>builder()
                        .data(ImageStreamChunkVO.builder()
                                .type("done")
                                .conversationId(conversationId)
                                .messageIndex(messageIndex)
                                .variantIndex(request.getVariantIndex())
                                .modelName(request.getModel())
                                .fullThinking(reasoning)
                                .build())
                        .build());

                log.info("图片生成完成，返回 {} 张图片，思考内容 {} 字符",
                        images.size(), reasoning != null ? reasoning.length() : 0);
                return Flux.fromIterable(allEvents);
            } catch (Exception e) {
                log.error("图片生成失败", e);
                String errorConversationId = null;
                Integer errorMessageIndex = null;
                try {
                    SaveFailedMessageResult saveResult = saveFailedMessage(request, userId, e.getMessage());
                    if (saveResult != null) {
                        errorConversationId = saveResult.conversationId;
                        errorMessageIndex = saveResult.messageIndex;
                    }
                } catch (Exception saveError) {
                    log.error("保存失败消息时出错", saveError);
                }

                return Flux.just(ServerSentEvent.<ImageStreamChunkVO>builder()
                        .data(ImageStreamChunkVO.builder()
                                .type("error")
                                .error(e.getMessage())
                                .conversationId(errorConversationId)
                                .messageIndex(errorMessageIndex)
                                .variantIndex(request.getVariantIndex())
                                .modelName(request.getModel())
                                .build())
                        .build());
            }
        });
    }

    private String buildCompletionsUrl() {
        String base = openRouterBaseUrl;
        if (base == null || base.isEmpty()) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "请配置OpenRouter的baseUrl");
        }
        if (base.endsWith("/")) {
            return base + "v1/chat/completions";
        }
        return base + "/v1/chat/completions";
    }

    private OpenRouterImageRequestBody buildRequestBody(GenerateImageRequest request, boolean stream) {
        List<OpenRouterImageRequestBody.Message> messages = new ArrayList<>();

        Object content;
        if (request.getReferenceImageUrls() != null && !request.getReferenceImageUrls().isEmpty()) {
            List<OpenRouterImageRequestBody.ContentItem> contentItems = new ArrayList<>();

            contentItems.add(OpenRouterImageRequestBody.ContentItem.builder()
                    .type("text")
                    .text(request.getPrompt())
                    .build());

            for (String url : request.getReferenceImageUrls()) {
                if (url == null || url.trim().isEmpty()) {
                    continue;
                }
                contentItems.add(OpenRouterImageRequestBody.ContentItem.builder()
                        .type("image_url")
                        .image_url(OpenRouterImageRequestBody.ImageUrl.builder()
                                .url(url)
                                .build())
                        .build());
            }
            content = contentItems;
        } else {
            content = request.getPrompt();
        }

        messages.add(OpenRouterImageRequestBody.Message.builder()
                .role("user")
                .content(content)
                .build());

        return OpenRouterImageRequestBody.builder()
                .model(request.getModel())
                .modalities(List.of("text", "image"))
                .stream(stream)
                .messages(messages)
                .n(request.getCount())
                .build();
    }

    private String handleImageData(String data, User loginUser) {
        // 如果是普通 URL，直接返回
        if (!data.startsWith("data:image")) {
            return data;
        }
        try {
            int commaIndex = data.indexOf(',');
            if (commaIndex <= 0) {
                throw new BusinessException(ErrorCode.SYSTEM_ERROR, "图片数据格式错误");
            }
            String header = data.substring(5, commaIndex);
            String[] headerParts = header.split(";");
            String mimeType = headerParts.length > 0 ? headerParts[0] : "image/png";
            String base64Data = data.substring(commaIndex + 1);

            byte[] bytes = Base64.decode(base64Data);
            if (bytes.length > 10 * ONE_M) {
                throw new BusinessException(ErrorCode.PARAMS_ERROR, "生成图片过大，请减少分辨率或数量");
            }

            String ext = getExtensionByMimeType(mimeType);
            String fileName = IdUtil.fastSimpleUUID() + "." + ext;
            String keyPath = String.format(COS_IMAGE_PREFIX, loginUser.getId()) + fileName;

            File tempFile = File.createTempFile("gen_img_", "." + ext);
            FileUtil.writeBytes(bytes, tempFile);

            // 直接上传到 COS（不加水印，不再压缩）
            String storedKey = tencentCosUtil.putObject(keyPath, tempFile, false, false);
            boolean deleted = tempFile.delete();
            if (!deleted) {
                log.warn("临时图片删除失败: {}", tempFile.getAbsolutePath());
            }

            // FileServiceImpl 中的 COS_HOST 常量为 https://yupi-1300582479.cos.ap-guangzhou.myqcloud.com
            String cosHost = "https://yupi-1300582479.cos.ap-guangzhou.myqcloud.com";
            return cosHost + storedKey;
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            log.error("处理生成图片数据失败", e);
            throw new BusinessException(ErrorCode.SYSTEM_ERROR, "处理生成图片数据失败：" + e.getMessage());
        }
    }

    private String getExtensionByMimeType(String mimeType) {
        if (mimeType == null) {
            return "png";
        }
        if ("image/jpeg".equalsIgnoreCase(mimeType) || "image/jpg".equalsIgnoreCase(mimeType)) {
            return "jpg";
        }
        if ("image/png".equalsIgnoreCase(mimeType)) {
            return "png";
        }
        if ("image/webp".equalsIgnoreCase(mimeType)) {
            return "webp";
        }
        if ("image/gif".equalsIgnoreCase(mimeType)) {
            return "gif";
        }
        return "png";
    }

    /**
     * 为图片生成创建会话
     */
    private String createConversationForImageGen(Long userId, GenerateImageRequest request) {
        String conversationId = IdUtil.randomUUID();
        List<String> models = request.getModels() != null && !request.getModels().isEmpty()
                ? request.getModels()
                : List.of(request.getModel());

        String title = request.getPrompt().length() > 50
                ? request.getPrompt().substring(0, 50) + "..."
                : request.getPrompt();

        // 根据请求中的会话类型决定，默认为 SIDE_BY_SIDE
        log.info("创建会话时的请求会话类型: conversationType={}", request.getConversationType());
        String conversationType = ConversationTypeEnum.SIDE_BY_SIDE.getValue();
        if (request.getConversationType() != null && !request.getConversationType().isEmpty()) {
            if (ConversationTypeEnum.PROMPT_LAB.getValue().equals(request.getConversationType())) {
                conversationType = ConversationTypeEnum.PROMPT_LAB.getValue();
            } else if (ConversationTypeEnum.BATTLE.getValue().equals(request.getConversationType())) {
                conversationType = ConversationTypeEnum.BATTLE.getValue();
            }
        }
        log.info("最终使用的会话类型: {}", conversationType);

        Conversation conversation = Conversation.builder()
                .id(conversationId)
                .userId(userId)
                .title(title)
                .conversationType(conversationType)
                .codePreviewEnabled(false)
                .isAnonymous(false)
                .models(JSONUtil.toJsonStr(models))
                .totalTokens(0)
                .totalCost(BigDecimal.ZERO)
                .createTime(LocalDateTime.now())
                .updateTime(LocalDateTime.now())
                .isDelete(0)
                .build();
        conversationMapper.insert(conversation);
        log.info("为图片生成创建新会话: conversationId={}, conversationType={}, models={}", conversationId, conversationType, models);
        return conversationId;
    }

    /**
     * 保存图片生成结果到会话
     */
    private Integer saveToConversation(
            String conversationId,
            Long userId,
            GenerateImageRequest request,
            List<GeneratedImageVO> resultList,
            int inputTokens,
            int outputTokens,
            int totalTokens,
            Double cost
    ) {
        try {
            // 获取消息索引：如果请求中提供了则使用，否则获取下一个
            int userMessageIndex;
            if (request.getMessageIndex() != null) {
                userMessageIndex = request.getMessageIndex();
            } else {
                userMessageIndex = getNextMessageIndex(conversationId);
            }

            // 获取变体索引
            Integer variantIndex = request.getVariantIndex();

            // 对于提示词对比模式，用户消息和助手消息使用相同的 messageIndex
            // 通过 variantIndex 区分不同的变体
            int assistantMessageIndex = userMessageIndex;

            // 保存用户消息
            List<String> referenceImageUrls = request.getReferenceImageUrls();
            ConversationMessage userMessage = ConversationMessage.builder()
                    .id(IdUtil.randomUUID())
                    .conversationId(conversationId)
                    .userId(userId)
                    .messageIndex(userMessageIndex)
                    .variantIndex(variantIndex)
                    .role(MessageRoleEnum.USER.getValue())
                    .content(request.getPrompt())
                    .images(referenceImageUrls != null && !referenceImageUrls.isEmpty()
                            ? JSONUtil.toJsonStr(referenceImageUrls)
                            : null)
                    .createTime(LocalDateTime.now())
                    .updateTime(LocalDateTime.now())
                    .isDelete(0)
                    .build();
            conversationMessageMapper.insert(userMessage);

            // 收集所有生成的图片URL
            List<String> generatedImageUrls = resultList.stream()
                    .map(GeneratedImageVO::getUrl)
                    .filter(url -> url != null && !url.isEmpty())
                    .collect(java.util.stream.Collectors.toList());

            // 保存助手消息（包含生成的图片）
            BigDecimal costValue = cost != null ? BigDecimal.valueOf(cost) : BigDecimal.ZERO;
            String imagesJson = generatedImageUrls.isEmpty() ? null : JSONUtil.toJsonStr(generatedImageUrls);
            log.info("💾 保存图片到会话: conversationId={}, messageIndex={}, variantIndex={}, 图片数={}, imagesJson={}",
                    conversationId, assistantMessageIndex, variantIndex, generatedImageUrls.size(), imagesJson);

            ConversationMessage assistantMessage = ConversationMessage.builder()
                    .id(IdUtil.randomUUID())
                    .conversationId(conversationId)
                    .userId(userId)
                    .messageIndex(assistantMessageIndex)
                    .variantIndex(variantIndex)
                    .role(MessageRoleEnum.ASSISTANT.getValue())
                    .modelName(request.getModel())
                    .content("已生成 " + generatedImageUrls.size() + " 张图片")
                    .images(imagesJson)
                    .inputTokens(inputTokens)
                    .outputTokens(outputTokens)
                    .cost(costValue)
                    .reasoning(request.getReasoning())
                    .createTime(LocalDateTime.now())
                    .updateTime(LocalDateTime.now())
                    .isDelete(0)
                    .build();
            conversationMessageMapper.insert(assistantMessage);

            // 验证保存结果
            ConversationMessage savedMessage = conversationMessageMapper.selectOneById(assistantMessage.getId());
            log.info("✅ 验证保存结果: messageId={}, images={}",
                    assistantMessage.getId(), savedMessage != null ? savedMessage.getImages() : "null");

            // 更新会话统计
            updateConversationStats(conversationId, totalTokens, costValue);

            log.info("图片生成结果已保存到会话: conversationId={}, messageIndex={}, 图片数={}", conversationId, userMessageIndex, generatedImageUrls.size());
            return userMessageIndex;
        } catch (Exception e) {
            log.error("保存图片生成结果到会话失败: conversationId={}", conversationId, e);
            return null;
        }
    }

    /**
     * 保存失败消息的结果
     */
    private static class SaveFailedMessageResult {
        String conversationId;
        Integer messageIndex;

        SaveFailedMessageResult(String conversationId, Integer messageIndex) {
            this.conversationId = conversationId;
            this.messageIndex = messageIndex;
        }
    }

    /**
     * 保存失败的图片生成消息（即使没有图片也保存）
     */
    private SaveFailedMessageResult saveFailedMessage(GenerateImageRequest request, Long userId, String errorMessage) {
        try {
            // 确定会话 ID
            String conversationId = request.getConversationId();
            if (conversationId == null || conversationId.isEmpty()) {
                conversationId = createConversationForImageGen(userId, request);
            }

            // 获取消息索引
            int messageIndex;
            if (request.getMessageIndex() != null) {
                messageIndex = request.getMessageIndex();
            } else {
                messageIndex = getNextMessageIndex(conversationId);
            }

            Integer variantIndex = request.getVariantIndex();

            // 保存用户消息
            List<String> referenceImageUrls = request.getReferenceImageUrls();
            ConversationMessage userMessage = ConversationMessage.builder()
                    .id(IdUtil.randomUUID())
                    .conversationId(conversationId)
                    .userId(userId)
                    .messageIndex(messageIndex)
                    .variantIndex(variantIndex)
                    .role(MessageRoleEnum.USER.getValue())
                    .content(request.getPrompt())
                    .images(referenceImageUrls != null && !referenceImageUrls.isEmpty()
                            ? JSONUtil.toJsonStr(referenceImageUrls)
                            : null)
                    .createTime(LocalDateTime.now())
                    .updateTime(LocalDateTime.now())
                    .isDelete(0)
                    .build();
            conversationMessageMapper.insert(userMessage);

            // 保存助手消息（包含错误信息和思考内容）
            String content = "图片生成失败: " + (errorMessage != null ? errorMessage : "未知错误");
            ConversationMessage assistantMessage = ConversationMessage.builder()
                    .id(IdUtil.randomUUID())
                    .conversationId(conversationId)
                    .userId(userId)
                    .messageIndex(messageIndex)
                    .variantIndex(variantIndex)
                    .role(MessageRoleEnum.ASSISTANT.getValue())
                    .modelName(request.getModel())
                    .content(content)
                    .reasoning(request.getReasoning())
                    .createTime(LocalDateTime.now())
                    .updateTime(LocalDateTime.now())
                    .isDelete(0)
                    .build();
            conversationMessageMapper.insert(assistantMessage);

            log.info("失败消息已保存: conversationId={}, messageIndex={}, error={}",
                    conversationId, messageIndex, errorMessage);
            return new SaveFailedMessageResult(conversationId, messageIndex);
        } catch (Exception e) {
            log.error("保存失败消息时出错", e);
            return null;
        }
    }

    /**
     * 获取下一个消息索引
     */
    private int getNextMessageIndex(String conversationId) {
        QueryWrapper wrapper = QueryWrapper.create()
                .select("MAX(messageIndex)")
                .from(ConversationMessage.class)
                .where("conversationId = ?", conversationId);
        Integer maxIndex = conversationMessageMapper.selectObjectByQueryAs(wrapper, Integer.class);
        return maxIndex == null ? 0 : maxIndex + 1;
    }

    /**
     * 更新会话统计
     */
    private void updateConversationStats(String conversationId, int tokens, BigDecimal cost) {
        try {
            Conversation conversation = conversationMapper.selectOneById(conversationId);
            if (conversation != null) {
                int currentTokens = conversation.getTotalTokens() != null ? conversation.getTotalTokens() : 0;
                BigDecimal currentCost = conversation.getTotalCost() != null ? conversation.getTotalCost() : BigDecimal.ZERO;

                conversation.setTotalTokens(currentTokens + tokens);
                conversation.setTotalCost(currentCost.add(cost));
                conversation.setUpdateTime(LocalDateTime.now());
                conversationMapper.update(conversation);
            }
        } catch (Exception e) {
            log.error("更新会话统计失败: conversationId={}", conversationId, e);
        }
    }

    /**
     * 随机选择一个支持图片生成的模型
     */
    private Model selectRandomImageGenModel() {
        QueryWrapper wrapper = QueryWrapper.create()
                .where("isDelete = 0")
                .and("supportsImageGen = 1");
        List<Model> models = modelMapper.selectListByQuery(wrapper);
        if (models == null || models.isEmpty()) {
            log.warn("没有找到支持图片生成的模型");
            return null;
        }
        // 随机选择一个模型
        int randomIndex = new java.util.Random().nextInt(models.size());
        return models.get(randomIndex);
    }
}

