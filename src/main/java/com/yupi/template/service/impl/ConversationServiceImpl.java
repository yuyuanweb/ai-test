package com.yupi.template.service.impl;

import cn.hutool.core.util.IdUtil;
import cn.hutool.json.JSONUtil;
import com.mybatisflex.core.paginate.Page;
import com.mybatisflex.core.query.QueryWrapper;
import com.yupi.template.constant.ConversationConstant;
import com.yupi.template.exception.BusinessException;
import com.yupi.template.exception.ErrorCode;
import com.yupi.template.exception.ThrowUtils;
import com.yupi.template.mapper.ConversationMapper;
import com.yupi.template.mapper.ConversationMessageMapper;
import com.yupi.template.mapper.ModelMapper;
import com.yupi.template.model.dto.conversation.ChatRequest;
import com.yupi.template.model.dto.conversation.CreateConversationRequest;
import com.yupi.template.model.dto.conversation.SideBySideRequest;
import com.yupi.template.model.entity.Conversation;
import com.yupi.template.model.entity.ConversationMessage;
import com.yupi.template.model.enums.ConversationTypeEnum;
import com.yupi.template.model.enums.MessageRoleEnum;
import com.yupi.template.model.vo.StreamChunkVO;
import com.yupi.template.service.ConversationService;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.MessageType;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.metadata.Usage;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

/**
 * 对话服务实现类
 *
 * @author <a href="https://codefather.cn">编程导航学习圈</a>
 */
@Slf4j
@Service
public class ConversationServiceImpl implements ConversationService {

    @Resource
    private ChatClient chatClient;

    @Resource
    private org.springframework.ai.chat.model.ChatModel chatModel;

    @Resource
    private ConversationMapper conversationMapper;

    @Resource
    private ConversationMessageMapper conversationMessageMapper;

    @Resource
    private ModelMapper modelMapper;

    @Override
    public String createConversation(CreateConversationRequest request, Long userId) {
        // 参数校验
        if (request.getConversationType() == null || request.getConversationType().trim().isEmpty()) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "对话类型不能为空");
        }
        ConversationTypeEnum typeEnum = ConversationTypeEnum.getEnumByValue(request.getConversationType());
        if (typeEnum == null) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "对话类型无效");
        }
        if (request.getModels() == null || request.getModels().isEmpty()) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "模型列表不能为空");
        }

        // 创建对话
        String conversationId = IdUtil.randomUUID();
        String title = request.getTitle() != null && !request.getTitle().trim().isEmpty()
                ? request.getTitle()
                : "新对话";

        boolean codePreviewEnabled = request.getCodePreviewEnabled() != null && request.getCodePreviewEnabled();
        
        Conversation conversation = Conversation.builder()
                .id(conversationId)
                .userId(userId)
                .title(title)
                .conversationType(typeEnum.getValue())
                .codePreviewEnabled(codePreviewEnabled)
                .models(JSONUtil.toJsonStr(request.getModels()))
                .totalTokens(0)
                .totalCost(BigDecimal.ZERO)
                .createTime(LocalDateTime.now())
                .updateTime(LocalDateTime.now())
                .isDelete(0)
                .build();
        conversationMapper.insert(conversation);

        return conversationId;
    }

    @Override
    public Flux<ServerSentEvent<StreamChunkVO>> chatStream(ChatRequest request, Long userId) {
        // 1. 参数校验
        ThrowUtils.throwIf(request == null, ErrorCode.PARAMS_ERROR);
        ThrowUtils.throwIf(request.getModel() == null || request.getModel().isEmpty(),
                ErrorCode.PARAMS_ERROR, "模型名称不能为空");
        ThrowUtils.throwIf(request.getMessage() == null || request.getMessage().isEmpty(),
                ErrorCode.PARAMS_ERROR, "消息内容不能为空");

        // 2. 创建或获取对话记录
        String conversationId = createOrGetConversation(
                request.getConversationId(),
                userId,
                request.getMessage(),
                ConversationTypeEnum.SIDE_BY_SIDE,
                List.of(request.getModel())
        );

        // 3. 保存用户消息并获取消息索引
        int userMessageIndex = saveUserMessage(conversationId, userId, request.getMessage());
        int assistantMessageIndex = userMessageIndex + 1;

        // 4. 调用模型并返回流式响应
        return createModelStream(
                conversationId,
                userId,
                request.getModel(),
                request.getMessage(),
                null,  // variantIndex: null 表示非 Prompt Lab 模式
                assistantMessageIndex  // 固定的消息索引
        );
    }

    @Override
    public Flux<ServerSentEvent<StreamChunkVO>> sideBySideStream(SideBySideRequest request, Long userId) {
        // 1. 参数校验
        validateSideBySideRequest(request);

        // 2. 创建或获取对话记录
        String conversationId = createOrGetConversation(
                request.getConversationId(),
                userId,
                request.getPrompt(),
                ConversationTypeEnum.SIDE_BY_SIDE,
                request.getModels()
        );

        // 3. 保存用户消息（每次对话都保存），并获取其messageIndex
        int userMessageIndex = saveUserMessage(conversationId, userId, request.getPrompt());
        // 所有模型的响应将使用下一个index（同一个index）
        int assistantMessageIndex = userMessageIndex + 1;

        // 4. 并行调用多个模型，使用Flux.merge实现真正的流式并发
        log.info("开始并行调用 {} 个模型: {}", request.getModels().size(), request.getModels());

        List<Flux<ServerSentEvent<StreamChunkVO>>> modelFluxes = new ArrayList<>();
        for (String modelName : request.getModels()) {
            log.info("创建模型流: {}", modelName);
            Flux<ServerSentEvent<StreamChunkVO>> modelFlux = createModelStream(
                    conversationId,
                    userId,
                    modelName,
                    request.getPrompt(),
                    null,
                    assistantMessageIndex  // 传递固定的messageIndex
            );
            modelFluxes.add(modelFlux);
        }

        // 使用Flux.merge并指定高并发数，确保所有流都能立即开始且事件立即发送
        int concurrency = Math.min(request.getModels().size(), 8);
        log.info("使用Flux.merge，并发数: {}, 流数量: {}", concurrency, modelFluxes.size());

        // Flux.merge会立即订阅所有流并立即发送事件（不等待）
        @SuppressWarnings("unchecked")
        Flux<ServerSentEvent<StreamChunkVO>> mergedFlux = Flux.merge(concurrency, modelFluxes.toArray(new Flux[0]));
        return mergedFlux;
    }

    @Override
    public Conversation getConversation(String conversationId, Long userId) {
        QueryWrapper wrapper = QueryWrapper.create()
                .from(Conversation.class)
                .where("id = ? and userId = ? and isDelete = 0", conversationId, userId);
        return conversationMapper.selectOneByQuery(wrapper);
    }

    @Override
    public Page<Conversation> listConversations(Long userId, int pageNum, int pageSize) {
        QueryWrapper wrapper = QueryWrapper.create()
                .from(Conversation.class)
                .where("userId = ? and isDelete = 0", userId)
                .orderBy("createTime", false);
        return conversationMapper.paginate(pageNum, pageSize, wrapper);
    }

    @Override
    public List<ConversationMessage> getConversationMessages(String conversationId, Long userId) {
        // 先验证对话是否属于当前用户
        Conversation conversation = getConversation(conversationId, userId);
        ThrowUtils.throwIf(conversation == null, ErrorCode.NOT_FOUND_ERROR, "对话不存在");

        // 查询消息列表
        QueryWrapper wrapper = QueryWrapper.create()
                .from(ConversationMessage.class)
                .where("conversationId = ? and isDelete = 0", conversationId)
                .orderBy("messageIndex", true);
        return conversationMessageMapper.selectListByQuery(wrapper);
    }

    @Override
    public boolean deleteConversation(String conversationId, Long userId) {
        // 验证对话是否属于当前用户
        Conversation conversation = getConversation(conversationId, userId);
        ThrowUtils.throwIf(conversation == null, ErrorCode.NOT_FOUND_ERROR, "对话不存在");

        // 逻辑删除
        conversation.setIsDelete(1);
        conversation.setUpdateTime(LocalDateTime.now());
        return conversationMapper.update(conversation) > 0;
    }

    /**
     * 校验Side-by-Side请求
     */
    private void validateSideBySideRequest(SideBySideRequest request) {
        if (request.getModels() == null || request.getModels().isEmpty()) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "模型列表不能为空");
        }
        if (request.getModels().size() < ConversationConstant.MIN_MODELS_COUNT ||
                request.getModels().size() > ConversationConstant.MAX_MODELS_COUNT) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR,
                    "模型数量必须在" + ConversationConstant.MIN_MODELS_COUNT + "-" +
                            ConversationConstant.MAX_MODELS_COUNT + "个之间");
        }
        if (request.getPrompt() == null || request.getPrompt().trim().isEmpty()) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "提示词不能为空");
        }
    }


    /**
     * 创建或获取对话记录
     */
    private String createOrGetConversation(
            String existingConversationId,
            Long userId,
            String prompt,
            ConversationTypeEnum conversationType,
            List<String> models
    ) {
        if (existingConversationId != null && !existingConversationId.isEmpty()) {
            return existingConversationId;
        }

        String conversationId = IdUtil.randomUUID();

        boolean codePreviewEnabled = ConversationTypeEnum.CODE_MODE.equals(conversationType);

        Conversation conversation = Conversation.builder()
                .id(conversationId)
                .userId(userId)
                .title(generateTitle(prompt))
                .conversationType(conversationType.getValue())
                .codePreviewEnabled(codePreviewEnabled)
                .models(JSONUtil.toJsonStr(models))
                .totalTokens(0)
                .totalCost(BigDecimal.ZERO)
                .createTime(LocalDateTime.now())
                .updateTime(LocalDateTime.now())
                .isDelete(0)
                .build();
        conversationMapper.insert(conversation);

        return conversationId;
    }


    /**
     * 为单个模型创建流式响应
     */
    private Flux<ServerSentEvent<StreamChunkVO>> createModelStream(
            String conversationId,
            Long userId,
            String modelName,
            String prompt,
            Integer variantIndex,
            Integer fixedMessageIndex  // 固定的messageIndex，用于side-by-side模式
    ) {
        // 累加器：用于统计Token、成本和计时
        AtomicLong startTime = new AtomicLong(System.currentTimeMillis());
        AtomicInteger inputTokens = new AtomicInteger(0);
        AtomicInteger outputTokens = new AtomicInteger(0);
        AtomicReference<Double> totalCost = new AtomicReference<>(0.0);
        AtomicReference<String> fullContent = new AtomicReference<>("");
        AtomicReference<String> reasoning = new AtomicReference<>("");

        // 获取历史消息构建上下文
        List<org.springframework.ai.chat.messages.Message> messages = new ArrayList<>();

        // 加载历史消息（排除当前正在创建的消息）
        List<ConversationMessage> historyMessages = getHistoryMessagesForContext(conversationId, fixedMessageIndex);

        // 转换历史消息为Spring AI的Message格式
        for (ConversationMessage msg : historyMessages) {
            if (MessageType.USER.getValue().equals(msg.getRole())) {
                messages.add(new UserMessage(msg.getContent()));
            } else if (MessageType.ASSISTANT.getValue().equals(msg.getRole())) {
                // 只添加当前模型的历史回复（避免混淆）
                if (modelName.equals(msg.getModelName())) {
                    messages.add(new AssistantMessage(msg.getContent()));
                }
            }
        }


        log.info("🚀 开始流式调用模型: {}, 上下文消息数: {}", modelName, messages.size());

        // 调用Spring AI流式API
        OpenAiChatOptions.Builder optionsBuilder = OpenAiChatOptions.builder()
                .model(modelName)
                .temperature(ConversationConstant.DEFAULT_TEMPERATURE);

        Prompt chatPrompt = new Prompt(messages, optionsBuilder.build());
        for (org.springframework.ai.chat.messages.Message message:messages){
            log.info("messages == {}",JSONUtil.toJsonStr(message.getText()));

        }

        // 使用chatModel.stream()直接获取流式响应（参考测试代码）
        return chatModel.stream(chatPrompt)
                .doOnNext(chatResponse -> {
                    String content = chatResponse.getResult().getOutput().getText();
                    log.info("📦 {}收到流式块: '{}' ({} 字符)", modelName, content, content != null ? content.length() : 0);
                })
                .map(chatResponse -> {
                    String content = chatResponse.getResult().getOutput().getText();
                    fullContent.updateAndGet(prev -> prev + content);
                    // 提取思考过程（reasoning tokens）- 用于o1/DeepSeek R1等模型
                    if (chatResponse.getResult() != null && chatResponse.getResult().getOutput() != null) {
                        Map<String, Object> outputMetadata = chatResponse.getResult().getOutput().getMetadata();
                        if (outputMetadata != null) {

                            // 提取reasoningContent
                            if (outputMetadata.containsKey("reasoningContent")) {
                                Object reasoningObj = outputMetadata.get("reasoningContent");
                                if (reasoningObj != null) {
                                    String reasoningContent = reasoningObj.toString();
                                    reasoning.updateAndGet(prev -> prev + reasoningContent);
                                }
                            }
                        }
                    }

                    if (chatResponse.getMetadata() != null && chatResponse.getMetadata().getUsage() != null) {
                        Usage usage = chatResponse.getMetadata().getUsage();

                        // 获取实际的prompt tokens（输入tokens）- Spring AI 1.1.2
                        Integer promptTokens = usage.getPromptTokens();
                        if (promptTokens != null) {
                            inputTokens.set(promptTokens);
                        }

                        // 获取实际的completion tokens（输出tokens）- Spring AI 1.1.2
                        Integer completionTokens = usage.getCompletionTokens();
                        if (completionTokens != null) {
                            outputTokens.set(completionTokens);
                        }
                    }

                    // 构建SSE数据
                    StreamChunkVO chunkVO = buildStreamChunk(
                            conversationId,
                            modelName,
                            variantIndex,
                            content,
                            fullContent.get(),
                            inputTokens.get(),
                            outputTokens.get(),
                            System.currentTimeMillis() - startTime.get(),
                            null,
                            null,
                            false,
                            reasoning.get()
                    );

                    log.debug("📤 发送SSE事件: model={}, contentLength={}, done=false",
                            modelName, fullContent.get().length());

                    return ServerSentEvent.<StreamChunkVO>builder()
                            .data(chunkVO)
                            .build();
                })
                .doOnNext(event -> log.debug("✅ SSE事件已发送: {}", event.data().getModelName()))
                .concatWith(Mono.defer(() -> {
                    // 流结束后保存消息
                    long responseTimeMs = System.currentTimeMillis() - startTime.get();

                    // 使用API返回的实际成本，如果没有则根据模型价格计算
                    Double cost = totalCost.get();
                    if (cost == null || cost == 0.0) {
                        log.warn("API未返回cost，使用模型价格计算: {}", modelName);
                        cost = calculateCostByModel(modelName, inputTokens.get(), outputTokens.get());
                    }

                    // 代码块提取功能暂未实现
                    String codeBlocksJson = null;

                    saveAssistantMessage(
                            conversationId,
                            userId,
                            modelName,
                            prompt,
                            fullContent.get(),
                            variantIndex,
                            (int) responseTimeMs,
                            inputTokens.get(),
                            outputTokens.get(),
                            fixedMessageIndex,  // 传递固定的messageIndex
                            reasoning.get(),  // 传递思考内容
                            codeBlocksJson  // 传递代码块JSON
                    );

                    // 发送完成事件
                    int totalTokensValue = inputTokens.get() + outputTokens.get();
                    StreamChunkVO doneVO = buildStreamChunk(
                            conversationId,
                            modelName,
                            variantIndex,
                            null,
                            fullContent.get(),
                            inputTokens.get(),
                            outputTokens.get(),
                            null,
                            (int) responseTimeMs,
                            cost,
                            true,
                            reasoning.get()
                    );
                    doneVO.setTotalTokens(totalTokensValue);

                    log.info("🏁 {}响应完成: {} 字符, {} tokens",
                            modelName, fullContent.get().length(), totalTokensValue);

                    return Mono.just(ServerSentEvent.<StreamChunkVO>builder()
                            .data(doneVO)
                            .build());
                }))
                .doOnComplete(() -> log.info("✅ {}流完成", modelName))
                .onErrorResume(error -> {
                    // 捕获错误并返回错误信息（避免500错误）
                    log.error("Model {} stream error: {}", modelName, error.getMessage(), error);

                    String errorMessage = extractErrorMessage(error);
                    StreamChunkVO errorVO = buildErrorChunk(
                            conversationId,
                            modelName,
                            variantIndex,
                            errorMessage
                    );

                    return Mono.just(ServerSentEvent.<StreamChunkVO>builder()
                            .data(errorVO)
                            .build());
                });
    }

    /**
     * 构建流式响应数据块
     */
    private StreamChunkVO buildStreamChunk(
            String conversationId,
            String modelName,
            Integer variantIndex,
            String content,
            String fullContent,
            Integer inputTokens,
            Integer outputTokens,
            Long elapsedMs,
            Integer responseTimeMs,
            Double cost,
            Boolean done,
            String reasoning
    ) {
        // 计算思考时间（秒）
        Integer thinkingTime = null;
        if (reasoning != null && !reasoning.isEmpty()) {
            // 简单估算：每200个字符约1秒，最少1秒，最多60秒
            thinkingTime = Math.max(1, Math.min(reasoning.length() / 200, 60));
        }

        // 代码块提取功能暂未实现（将在阶段4添加）

        StreamChunkVO chunkVO = StreamChunkVO.builder()
                .conversationId(conversationId)
                .modelName(modelName)
                .variantIndex(variantIndex)
                .content(content)
                .fullContent(fullContent)
                .inputTokens(inputTokens)
                .outputTokens(outputTokens)
                .elapsedMs(elapsedMs)
                .responseTimeMs(responseTimeMs)
                .cost(cost)
                .done(done)
                .reasoning(reasoning)
                .hasReasoning(reasoning != null && !reasoning.isEmpty())
                .thinkingTime(thinkingTime)
                .build();
        return chunkVO;
    }

    /**
     * 保存用户消息
     *
     * @return 返回保存的消息的messageIndex
     */
    private int saveUserMessage(String conversationId, Long userId, String content) {
        int messageIndex = getNextMessageIndex(conversationId);
        ConversationMessage message = ConversationMessage.builder()
                .id(IdUtil.randomUUID())
                .conversationId(conversationId)
                .userId(userId)
                .messageIndex(messageIndex)
                .role(MessageRoleEnum.USER.getValue())
                .content(content)
                .createTime(LocalDateTime.now())
                .updateTime(LocalDateTime.now())
                .isDelete(0)
                .build();
        conversationMessageMapper.insert(message);
        return messageIndex;
    }

    /**
     * 保存助手消息
     */
    private void saveAssistantMessage(
            String conversationId,
            Long userId,
            String modelName,
            String prompt,
            String content,
            Integer variantIndex,
            int responseTimeMs,
            int inputTokens,
            int outputTokens,
            Integer fixedMessageIndex,  // 固定的messageIndex（可选）
            String reasoning,  // 思考过程（可选）
            String codeBlocks  // 代码块JSON（可选）
    ) {
        // 对于Prompt Lab，先保存用户的提示词变体
        if (variantIndex != null) {
            ConversationMessage userMessage = ConversationMessage.builder()
                    .id(IdUtil.randomUUID())
                    .conversationId(conversationId)
                    .userId(userId)
                    .messageIndex(getNextMessageIndex(conversationId))
                    .role(MessageRoleEnum.USER.getValue())
                    .content("变体" + variantIndex + ": " + prompt)
                    .createTime(LocalDateTime.now())
                    .updateTime(LocalDateTime.now())
                    .isDelete(0)
                    .build();
            conversationMessageMapper.insert(userMessage);
        }

        // 保存助手消息
        BigDecimal cost = BigDecimal.valueOf(calculateCostByModel(modelName, inputTokens, outputTokens));
        // 使用固定的messageIndex（如果提供），否则获取下一个
        int messageIndex = (fixedMessageIndex != null) ? fixedMessageIndex : getNextMessageIndex(conversationId);

        // 记录思考内容日志
        if (reasoning != null && !reasoning.isEmpty()) {
            log.info("💾 保存思考内容: 模型={}, 长度={}", modelName, reasoning.length());
        }

        ConversationMessage message = ConversationMessage.builder()
                .id(IdUtil.randomUUID())
                .conversationId(conversationId)
                .userId(userId)
                .messageIndex(messageIndex)
                .role(MessageRoleEnum.ASSISTANT.getValue())
                .modelName(modelName)
                .content(content)
                .responseTimeMs(responseTimeMs)
                .inputTokens(inputTokens)
                .outputTokens(outputTokens)
                .cost(cost)
                .reasoning(reasoning)  // 保存思考内容
                .codeBlocks(codeBlocks)  // 保存代码块JSON
                .createTime(LocalDateTime.now())
                .updateTime(LocalDateTime.now())
                .isDelete(0)
                .build();
        conversationMessageMapper.insert(message);

        // 更新对话统计
        updateConversationStats(conversationId, inputTokens + outputTokens, cost);
    }

    /**
     * 获取下一个消息序号
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
     * 获取用于上下文的历史消息
     *
     * @param conversationId      会话ID
     * @param excludeMessageIndex 排除的messageIndex（当前正在创建的消息）
     * @return 历史消息列表
     */
    private List<ConversationMessage> getHistoryMessagesForContext(String conversationId, Integer excludeMessageIndex) {
        QueryWrapper wrapper = QueryWrapper.create()
                .from(ConversationMessage.class)
                .where("conversationId = ? and isDelete = 0", conversationId)
                .orderBy("messageIndex", true);

        // 如果指定了排除的index，添加过滤条件
        if (excludeMessageIndex != null) {
            wrapper.and("messageIndex < ?", excludeMessageIndex);
        }

        List<ConversationMessage> messages = conversationMessageMapper.selectListByQuery(wrapper);
        log.info("📚 加载历史消息: 会话ID={}, 数量={}", conversationId, messages.size());

        return messages;
    }

    /**
     * 更新对话统计信息
     */
    private void updateConversationStats(String conversationId, int tokens, BigDecimal cost) {
        Conversation conversation = conversationMapper.selectOneById(conversationId);
        if (conversation != null) {
            conversation.setTotalTokens(
                    (conversation.getTotalTokens() == null ? 0 : conversation.getTotalTokens()) + tokens
            );
            conversation.setTotalCost(
                    (conversation.getTotalCost() == null ? BigDecimal.ZERO : conversation.getTotalCost()).add(cost)
            );
            conversation.setUpdateTime(LocalDateTime.now());
            conversationMapper.update(conversation);
        }
    }

    /**
     * 根据模型实际价格计算成本（从数据库获取模型价格）
     */
    private Double calculateCostByModel(String modelId, int inputTokens, int outputTokens) {
        try {
            // 从数据库查询模型价格信息
            com.yupi.template.model.entity.Model model = modelMapper.selectOneById(modelId);

            if (model != null && model.getInputPrice() != null && model.getOutputPrice() != null) {
                // 使用模型实际价格计算
                double inputCost = (inputTokens / ConversationConstant.TOKENS_PER_MILLION) *
                        model.getInputPrice().doubleValue();
                double outputCost = (outputTokens / ConversationConstant.TOKENS_PER_MILLION) *
                        model.getOutputPrice().doubleValue();
                return inputCost + outputCost;
            } else {
                log.warn("模型{}价格信息不存在，使用默认价格", modelId);
                return calculateCostWithDefaultPrice(inputTokens, outputTokens);
            }
        } catch (Exception e) {
            log.error("查询模型{}价格失败，使用默认价格", modelId, e);
            return calculateCostWithDefaultPrice(inputTokens, outputTokens);
        }
    }

    /**
     * 使用默认价格计算成本（降级方案）
     */
    private Double calculateCostWithDefaultPrice(int inputTokens, int outputTokens) {
        double inputCost = (inputTokens / ConversationConstant.TOKENS_PER_MILLION) *
                ConversationConstant.DEFAULT_INPUT_PRICE_PER_MILLION;
        double outputCost = (outputTokens / ConversationConstant.TOKENS_PER_MILLION) *
                ConversationConstant.DEFAULT_OUTPUT_PRICE_PER_MILLION;
        return inputCost + outputCost;
    }

    /**
     * 生成对话标题
     */
    private String generateTitle(String prompt) {
        if (prompt.length() > ConversationConstant.MAX_TITLE_LENGTH) {
            return prompt.substring(0, ConversationConstant.MAX_TITLE_LENGTH) + "...";
        }
        return prompt;
    }

    /**
     * 从异常中提取错误信息（保留OpenRouter原始错误）
     */
    private String extractErrorMessage(Throwable error) {
        if (error == null) {
            return "未知错误";
        }

        String message = error.getMessage();
        if (message == null || message.isEmpty()) {
            message = error.getClass().getSimpleName();
        }

        // 添加友好提示，但保留原始错误信息
        if (message.contains("401") || message.contains("Unauthorized")) {
            return "API Key验证失败 - " + message;
        }
        if (message.contains("403") || message.contains("Forbidden")) {
            return "权限不足或配额超限 - " + message;
        }
        if (message.contains("404") || message.contains("not found")) {
            return message;
        }
        if (message.contains("429") || message.contains("rate limit")) {
            return "请求频率超限 - " + message;
        }
        if (message.contains("timeout")) {
            return "请求超时 - " + message;
        }

        // 直接返回原始错误信息
        return message;
    }

    /**
     * 构建错误响应
     */
    private StreamChunkVO buildErrorChunk(
            String conversationId,
            String modelName,
            Integer variantIndex,
            String errorMessage
    ) {
        return StreamChunkVO.builder()
                .conversationId(conversationId)
                .modelName(modelName)
                .variantIndex(variantIndex)
                .error(errorMessage)
                .hasError(true)
                .hasReasoning(false)
                .done(true)
                .build();
    }
}
