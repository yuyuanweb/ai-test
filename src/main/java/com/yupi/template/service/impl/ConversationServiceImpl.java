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
import com.yupi.template.model.dto.conversation.PromptLabRequest;
import com.yupi.template.model.dto.conversation.SideBySideRequest;
import com.yupi.template.model.dto.conversation.CodeModeRequest;
import com.yupi.template.model.dto.conversation.CodeModePromptLabRequest;
import com.yupi.template.model.entity.Conversation;
import com.yupi.template.model.entity.ConversationMessage;
import com.yupi.template.model.enums.ConversationTypeEnum;
import com.yupi.template.model.enums.MessageRoleEnum;
import com.yupi.template.model.vo.StreamChunkVO;
import com.yupi.template.service.ConversationService;
import com.yupi.template.service.ModelService;
import com.yupi.template.service.UserModelUsageService;
import com.yupi.template.utils.CodeExtractor;
import com.yupi.template.model.dto.code.CodeBlock;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.MessageType;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.messages.SystemMessage;
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
import java.util.Collections;
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

    @Resource
    private ModelService modelService;

    @Resource
    private UserModelUsageService userModelUsageService;

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
    public Flux<ServerSentEvent<StreamChunkVO>> promptLabStream(PromptLabRequest request, Long userId) {
        // 1. 参数校验
        validatePromptLabRequest(request);

        // 2. 创建或获取对话记录
        String conversationId = createOrGetConversation(
                request.getConversationId(),
                userId,
                request.getPromptVariants().get(0),
                ConversationTypeEnum.PROMPT_LAB,
                Collections.singletonList(request.getModel())
        );

        // 3. 获取本轮对话的messageIndex（所有变体共享同一个messageIndex）
        int userMessageIndex = getNextMessageIndex(conversationId);
        int assistantMessageIndex = userMessageIndex + 1;

        // 4. 并行调用同一模型的不同提示词变体
        List<Flux<ServerSentEvent<StreamChunkVO>>> variantFluxes = new ArrayList<>();
        for (int i = 0; i < request.getPromptVariants().size(); i++) {
            String promptVariant = request.getPromptVariants().get(i);
            Flux<ServerSentEvent<StreamChunkVO>> variantFlux = createModelStream(
                    conversationId,
                    userId,
                    request.getModel(),
                    promptVariant,
                    i,
                    assistantMessageIndex  // 所有变体的AI响应使用同一个messageIndex
            );
            variantFluxes.add(variantFlux);
        }

        // 5. 合并所有变体的流
        return Flux.merge(variantFluxes);
    }

    @Override
    public Flux<ServerSentEvent<StreamChunkVO>> codeModeStream(CodeModeRequest request, Long userId) {
        // 1. 参数校验
        validateCodeModeRequest(request);

        // 2. 创建或获取对话记录（使用codePreviewEnabled=true，conversationType为side_by_side）
        String conversationId = createOrGetConversation(
                request.getConversationId(),
                userId,
                request.getPrompt(),
                ConversationTypeEnum.SIDE_BY_SIDE,
                request.getModels(),
                true  // codePreviewEnabled = true
        );

        // 3. 保存用户消息
        int userMessageIndex = saveUserMessage(conversationId, userId, request.getPrompt());
        int assistantMessageIndex = userMessageIndex + 1;

        // 4. 并行调用多个模型（带系统提示词）
        List<Flux<ServerSentEvent<StreamChunkVO>>> modelFluxes = new ArrayList<>();
        for (String modelName : request.getModels()) {
            Flux<ServerSentEvent<StreamChunkVO>> modelFlux = createModelStreamWithSystemPrompt(
                    conversationId,
                    userId,
                    modelName,
                    request.getPrompt(),
                    ConversationConstant.CODE_MODE_SYSTEM_PROMPT,
                    assistantMessageIndex
            );
            modelFluxes.add(modelFlux);
        }

        // 5. 合并所有模型的流
        int concurrency = Math.min(request.getModels().size(), 8);
        @SuppressWarnings("unchecked")
        Flux<ServerSentEvent<StreamChunkVO>> mergedFlux = Flux.merge(concurrency, modelFluxes.toArray(new Flux[0]));
        return mergedFlux;
    }

    @Override
    public Flux<ServerSentEvent<StreamChunkVO>> codeModePromptLabStream(CodeModePromptLabRequest request, Long userId) {
        // 1. 参数校验
        validateCodeModePromptLabRequest(request);

        // 2. 创建或获取对话记录（使用codePreviewEnabled=true，conversationType为prompt_lab）
        String conversationId = createOrGetConversation(
                request.getConversationId(),
                userId,
                request.getPromptVariants().get(0),
                ConversationTypeEnum.PROMPT_LAB,
                Collections.singletonList(request.getModel()),
                true  // codePreviewEnabled = true
        );

        // 3. 获取本轮对话的messageIndex（所有变体共享同一个messageIndex）
        int userMessageIndex = getNextMessageIndex(conversationId);
        int assistantMessageIndex = userMessageIndex + 1;

        // 4. 为每个变体保存用户消息
        for (int i = 0; i < request.getPromptVariants().size(); i++) {
            String promptVariant = request.getPromptVariants().get(i);
            saveUserMessage(conversationId, userId, promptVariant, userMessageIndex, i);
            log.info("保存变体{}的用户消息: messageIndex={}, content={}", i, userMessageIndex, promptVariant);
        }

        // 5. 并行调用同一模型的不同提示词变体（使用代码模式的系统提示词）
        List<Flux<ServerSentEvent<StreamChunkVO>>> variantFluxes = new ArrayList<>();
        for (int i = 0; i < request.getPromptVariants().size(); i++) {
            String promptVariant = request.getPromptVariants().get(i);
            Flux<ServerSentEvent<StreamChunkVO>> variantFlux = createModelStreamWithSystemPrompt(
                    conversationId,
                    userId,
                    request.getModel(),
                    promptVariant,
                    ConversationConstant.CODE_MODE_SYSTEM_PROMPT,
                    i,  // variantIndex
                    assistantMessageIndex  // 所有变体的AI响应使用同一个messageIndex
            );
            variantFluxes.add(variantFlux);
        }

        // 5. 合并所有变体的流
        int concurrency = Math.min(request.getPromptVariants().size(), 8);
        @SuppressWarnings("unchecked")
        Flux<ServerSentEvent<StreamChunkVO>> mergedFlux = Flux.merge(concurrency, variantFluxes.toArray(new Flux[0]));
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
    public Page<Conversation> listConversations(Long userId, int pageNum, int pageSize, Boolean codePreviewEnabled) {
        QueryWrapper wrapper = QueryWrapper.create()
                .from(Conversation.class)
                .where("userId = ? and isDelete = 0", userId);
        
        if (codePreviewEnabled != null) {
            wrapper.and("codePreviewEnabled = ?", codePreviewEnabled);
        }
        
        wrapper.orderBy("createTime", false);
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
     * 校验Prompt Lab请求
     */
    private void validatePromptLabRequest(PromptLabRequest request) {
        if (request.getModel() == null || request.getModel().trim().isEmpty()) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "模型不能为空");
        }
        if (request.getPromptVariants() == null || request.getPromptVariants().isEmpty()) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "提示词变体列表不能为空");
        }
        if (request.getPromptVariants().size() < ConversationConstant.MIN_PROMPT_VARIANTS_COUNT ||
                request.getPromptVariants().size() > ConversationConstant.MAX_PROMPT_VARIANTS_COUNT) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR,
                    "提示词变体数量必须在" + ConversationConstant.MIN_PROMPT_VARIANTS_COUNT + "-" +
                            ConversationConstant.MAX_PROMPT_VARIANTS_COUNT + "个之间");
        }
    }

    /**
     * 校验Code Mode Prompt Lab请求
     */
    private void validateCodeModePromptLabRequest(CodeModePromptLabRequest request) {
        if (request.getModel() == null || request.getModel().trim().isEmpty()) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "模型不能为空");
        }
        if (request.getPromptVariants() == null || request.getPromptVariants().isEmpty()) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "提示词变体列表不能为空");
        }
        if (request.getPromptVariants().size() < ConversationConstant.MIN_PROMPT_VARIANTS_COUNT ||
                request.getPromptVariants().size() > ConversationConstant.MAX_PROMPT_VARIANTS_COUNT) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR,
                    "提示词变体数量必须在" + ConversationConstant.MIN_PROMPT_VARIANTS_COUNT + "-" +
                            ConversationConstant.MAX_PROMPT_VARIANTS_COUNT + "个之间");
        }
    }


    /**
     * 校验Code Mode请求
     */
    private void validateCodeModeRequest(CodeModeRequest request) {
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
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "需求描述不能为空");
        }
    }

    /**
     * 创建带系统提示词的模型流（用于代码模式）
     */
    private Flux<ServerSentEvent<StreamChunkVO>> createModelStreamWithSystemPrompt(
            String conversationId,
            Long userId,
            String modelName,
            String userPrompt,
            String systemPrompt,
            Integer fixedMessageIndex
    ) {
        return createModelStreamWithSystemPrompt(conversationId, userId, modelName, userPrompt, systemPrompt, null, fixedMessageIndex);
    }

    /**
     * 创建带系统提示词的模型流（用于代码模式，支持variantIndex）
     */
    private Flux<ServerSentEvent<StreamChunkVO>> createModelStreamWithSystemPrompt(
            String conversationId,
            Long userId,
            String modelName,
            String userPrompt,
            String systemPrompt,
            Integer variantIndex,
            Integer fixedMessageIndex
    ) {
        AtomicLong startTime = new AtomicLong(System.currentTimeMillis());
        AtomicInteger inputTokens = new AtomicInteger(0);
        AtomicInteger outputTokens = new AtomicInteger(0);
        AtomicReference<Double> totalCost = new AtomicReference<>(0.0);
        AtomicReference<String> fullContent = new AtomicReference<>("");
        AtomicReference<String> reasoning = new AtomicReference<>("");

        List<org.springframework.ai.chat.messages.Message> messages = new ArrayList<>();
        
        // 添加系统提示词（在最前面）
        if (systemPrompt != null && !systemPrompt.trim().isEmpty()) {
            messages.add(new SystemMessage(systemPrompt));
        }
        
        List<ConversationMessage> historyMessages = getHistoryMessagesForContext(conversationId, fixedMessageIndex, variantIndex);
        
        // 转换历史消息为Spring AI的Message格式
        boolean hasCurrentUserMessage = false;
        for (ConversationMessage msg : historyMessages) {
            if (MessageType.USER.getValue().equals(msg.getRole())) {
                String content = msg.getContent();
                // 如果是Prompt Lab模式（variantIndex不为null），只添加当前变体的历史消息
                if (variantIndex != null) {
                    if (msg.getVariantIndex() != null && !msg.getVariantIndex().equals(variantIndex)) {
                        continue;
                    } else if (msg.getVariantIndex() == null) {
                        String variantPrefix = "变体" + variantIndex + ":";
                        if (!content.startsWith(variantPrefix)) {
                            continue;
                        }
                        content = content.substring(variantPrefix.length()).trim();
                    }
                    messages.add(new UserMessage(content));
                    if (content.equals(userPrompt)) {
                        hasCurrentUserMessage = true;
                    }
                } else {
                    messages.add(new UserMessage(content));
                    if (content.equals(userPrompt)) {
                        hasCurrentUserMessage = true;
                    }
                }
            } else if (MessageType.ASSISTANT.getValue().equals(msg.getRole())) {
                if (modelName.equals(msg.getModelName())) {
                    if (variantIndex != null) {
                        if (msg.getVariantIndex() != null && !msg.getVariantIndex().equals(variantIndex)) {
                            continue;
                        }
                    }
                    String content = msg.getContent();
                    if (variantIndex != null && content.startsWith("变体")) {
                        int colonIndex = content.indexOf(":");
                        if (colonIndex > 0 && colonIndex < content.length() - 1) {
                            content = content.substring(colonIndex + 1).trim();
                        }
                    }
                    messages.add(new AssistantMessage(content));
                }
            }
        }
        
        // 添加当前的用户prompt
        if (userPrompt != null && !userPrompt.trim().isEmpty()) {
            if (!hasCurrentUserMessage || messages.size() == 1) {  // 只有系统提示词时也要添加
                messages.add(new UserMessage(userPrompt));
            }
        }
        
        log.info("🚀 代码模式流式调用: model={}, 上下文消息数: {}, 系统提示词长度: {}", 
                modelName, messages.size(), systemPrompt.length());
        
        OpenAiChatOptions.Builder optionsBuilder = OpenAiChatOptions.builder()
                .model(modelName)
                .temperature(ConversationConstant.DEFAULT_TEMPERATURE);
        
        Prompt chatPrompt = new Prompt(messages, optionsBuilder.build());

        return chatModel.stream(chatPrompt)
                .doOnNext(chatResponse -> {
                    String content = chatResponse.getResult().getOutput().getText();
                    log.info("📦 {}收到流式块: '{}' ({} 字符)", modelName, content, content != null ? content.length() : 0);
                })
                .map(chatResponse -> {
                    String content = chatResponse.getResult().getOutput().getText();
                    fullContent.updateAndGet(prev -> prev + content);
                    
                    if (chatResponse.getResult() != null && chatResponse.getResult().getOutput() != null) {
                        Map<String, Object> outputMetadata = chatResponse.getResult().getOutput().getMetadata();
                        if (outputMetadata != null && outputMetadata.containsKey("reasoningContent")) {
                            Object reasoningObj = outputMetadata.get("reasoningContent");
                            if (reasoningObj != null) {
                                reasoning.updateAndGet(prev -> prev + reasoningObj.toString());
                            }
                        }
                    }

                    if (chatResponse.getMetadata() != null && chatResponse.getMetadata().getUsage() != null) {
                        Usage usage = chatResponse.getMetadata().getUsage();
                        if (usage.getPromptTokens() != null) {
                            inputTokens.set(usage.getPromptTokens());
                        }
                        if (usage.getCompletionTokens() != null) {
                            outputTokens.set(usage.getCompletionTokens());
                        }
                    }

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
                            reasoning.get(),
                            fixedMessageIndex
                    );

                    return ServerSentEvent.<StreamChunkVO>builder()
                            .data(chunkVO)
                            .build();
                })
                .concatWith(Mono.defer(() -> {
                    long responseTimeMs = System.currentTimeMillis() - startTime.get();
                    
                    Double cost = totalCost.get();
                    if (cost == null || cost == 0.0) {
                        cost = calculateCostByModel(modelName, inputTokens.get(), outputTokens.get());
                    }
                    
                    // 提取代码块并序列化为JSON
                    String codeBlocksJson = null;
                    if (fullContent.get() != null && !fullContent.get().isEmpty()) {
                        List<CodeBlock> codeBlocks = CodeExtractor.extractCodeBlocks(fullContent.get());
                        if (codeBlocks != null && !codeBlocks.isEmpty()) {
                            codeBlocksJson = JSONUtil.toJsonStr(codeBlocks);
                            log.info("💾 保存代码块: 模型={}, 代码块数={}", modelName, codeBlocks.size());
                        }
                    }
                    
                    saveAssistantMessage(
                            conversationId,
                            userId,
                            modelName,
                            userPrompt,
                            fullContent.get(),
                            variantIndex,
                            (int) responseTimeMs,
                            inputTokens.get(),
                            outputTokens.get(),
                            fixedMessageIndex,
                            reasoning.get(),
                            codeBlocksJson
                    );

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
                            reasoning.get(),
                            fixedMessageIndex
                    );
                    doneVO.setTotalTokens(totalTokensValue);

                    return Mono.just(ServerSentEvent.<StreamChunkVO>builder()
                            .data(doneVO)
                            .build());
                }))
                .onErrorResume(error -> {
                    log.error("模型{}调用失败", modelName, error);
                    StreamChunkVO errorVO = buildErrorChunk(
                            conversationId,
                            modelName,
                            variantIndex,
                            extractErrorMessage(error),
                            fixedMessageIndex
                    );
                    return Mono.just(ServerSentEvent.<StreamChunkVO>builder()
                            .data(errorVO)
                            .build());
                });
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
        return createOrGetConversation(existingConversationId, userId, prompt, conversationType, models, false);
    }

    /**
     * 创建或获取对话记录（支持codePreviewEnabled参数）
     */
    private String createOrGetConversation(
            String existingConversationId,
            Long userId,
            String prompt,
            ConversationTypeEnum conversationType,
            List<String> models,
            boolean codePreviewEnabled
    ) {
        if (existingConversationId != null && !existingConversationId.isEmpty()) {
            return existingConversationId;
        }

        String conversationId = IdUtil.randomUUID();
        
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
        
        // 加载历史消息（排除当前正在创建的消息，如果是Prompt Lab模式，只加载当前变体的消息）
        List<ConversationMessage> historyMessages = getHistoryMessagesForContext(conversationId, fixedMessageIndex, variantIndex);
        
        // 转换历史消息为Spring AI的Message格式
        boolean hasCurrentUserMessage = false;
        for (ConversationMessage msg : historyMessages) {
            if (MessageType.USER.getValue().equals(msg.getRole())) {
                String content = msg.getContent();
                // 如果是Prompt Lab模式（variantIndex不为null），只添加当前变体的历史消息
                // 由于getHistoryMessagesForContext已经过滤了variantIndex，这里直接添加即可
                if (variantIndex != null) {
                    // 检查variantIndex是否匹配（兼容旧数据：如果variantIndex为null，说明是旧数据，需要通过内容判断）
                    if (msg.getVariantIndex() != null && !msg.getVariantIndex().equals(variantIndex)) {
                        // variantIndex不匹配，跳过
                        log.debug("⏭️ 跳过非当前变体{}的用户消息: variantIndex={}", variantIndex, msg.getVariantIndex());
                        continue;
                    } else if (msg.getVariantIndex() == null) {
                        // 旧数据：通过内容前缀判断
                        String variantPrefix = "变体" + variantIndex + ":";
                        if (!content.startsWith(variantPrefix)) {
                            log.debug("⏭️ 跳过非当前变体{}的用户消息（旧数据）: {}", variantIndex, content);
                            continue;
                        }
                        // 去除"变体X: "前缀
                        content = content.substring(variantPrefix.length()).trim();
                    }
                    // variantIndex匹配，直接使用content（新数据不再有前缀）
                    messages.add(new UserMessage(content));
                    log.info("📝 添加当前变体{}的历史用户消息: {}", variantIndex, content);
                    // 检查是否是当前prompt
                    if (content.equals(prompt)) {
                        hasCurrentUserMessage = true;
                    }
                } else {
                    // 非Prompt Lab模式，直接添加所有用户消息
                    messages.add(new UserMessage(content));
                    // 检查是否是当前prompt
                    if (content.equals(prompt)) {
                        hasCurrentUserMessage = true;
                    }
                }
            } else if (MessageType.ASSISTANT.getValue().equals(msg.getRole())) {
                // 只添加当前模型的历史回复（避免混淆）
                if (modelName.equals(msg.getModelName())) {
                    // 如果是Prompt Lab模式，需要检查这个AI消息是否属于当前变体
                    if (variantIndex != null) {
                        // 优先使用variantIndex字段（如果存在）
                        if (msg.getVariantIndex() != null) {
                            if (!msg.getVariantIndex().equals(variantIndex)) {
                                // 不是当前变体的AI消息，跳过（虽然getHistoryMessagesForContext已经过滤，但为了安全还是检查）
                                log.debug("⏭️ 跳过非当前变体{}的AI消息: messageIndex={}, variantIndex={}", 
                                        variantIndex, msg.getMessageIndex(), msg.getVariantIndex());
                                continue;
                            }
                            log.debug("✅ messageIndex {} 的AI消息属于当前变体{} (通过variantIndex字段)", msg.getMessageIndex(), variantIndex);
                        } else {
                            // 旧数据：如果没有variantIndex字段，通过对应的用户消息来判断
                            int msgIndex = msg.getMessageIndex();
                            int userMessageIndex = msgIndex - 1;
                            
                            // 查找对应的用户消息
                            ConversationMessage correspondingUserMsg = null;
                            for (ConversationMessage userMsg : historyMessages) {
                                if (MessageType.USER.getValue().equals(userMsg.getRole()) 
                                        && userMsg.getMessageIndex() == userMessageIndex) {
                                    correspondingUserMsg = userMsg;
                                    break;
                                }
                            }
                            
                            if (correspondingUserMsg == null) {
                                log.warn("⚠️ 无法找到对应的用户消息: messageIndex={}, userMessageIndex={}, 跳过", 
                                        msgIndex, userMessageIndex);
                                continue;
                            }
                            
                            // 检查用户消息的variantIndex或内容
                            if (correspondingUserMsg.getVariantIndex() != null) {
                                // 新数据：通过variantIndex判断
                                if (!correspondingUserMsg.getVariantIndex().equals(variantIndex)) {
                                    log.info("⏭️ 跳过非当前变体{}的AI消息: messageIndex={}, 对应的用户variantIndex={}", 
                                            variantIndex, msgIndex, correspondingUserMsg.getVariantIndex());
                                    continue;
                                }
                            } else {
                                // 旧数据：通过内容前缀判断
                                String userContent = correspondingUserMsg.getContent();
                                String expectedPrefix = "变体" + variantIndex + ":";
                                if (!userContent.startsWith(expectedPrefix)) {
                                    log.info("⏭️ 跳过非当前变体{}的AI消息: messageIndex={}, 对应的用户消息内容={}", 
                                            variantIndex, msgIndex, userContent.substring(0, Math.min(50, userContent.length())));
                                    continue;
                                }
                            }
                            
                            log.info("✅ messageIndex {} 的AI消息属于当前变体{} (通过用户消息匹配)", msgIndex, variantIndex);
                        }
                    }
                    
                    String content = msg.getContent();
                    // 如果是Prompt Lab模式，检查是否有变体前缀需要去除（旧数据兼容）
                    if (variantIndex != null && content.startsWith("变体")) {
                        // 去除"变体X: "前缀（如果存在）
                        int colonIndex = content.indexOf(":");
                        if (colonIndex > 0 && colonIndex < content.length() - 1) {
                            content = content.substring(colonIndex + 1).trim();
                        }
                    }
                    messages.add(new AssistantMessage(content));
                    log.info("📝 添加当前模型{}的历史AI回复: {}", modelName, content.substring(0, Math.min(50, content.length())));
                }
            }
        }

        // 添加当前的用户prompt
        // 对于Prompt Lab模式：用户消息是在saveAssistantMessage中保存的，此时可能还没有保存到数据库
        // 对于Side-by-Side模式：用户消息是在调用createModelStream之前保存的，已经包含在历史消息中
        // 所以：如果历史消息中没有包含当前prompt，或者messages为空，就添加当前prompt
        if (prompt != null && !prompt.trim().isEmpty()) {
            if (!hasCurrentUserMessage || messages.isEmpty()) {
                messages.add(new UserMessage(prompt));
                log.info("📝 添加当前用户prompt: {}", prompt);
            } else {
                log.info("⏭️ 跳过添加当前prompt（历史消息中已包含）: {}", prompt);
            }
        }

        log.info("🚀 开始流式调用模型: {}, 上下文消息数: {}, 当前prompt: {}", modelName, messages.size(), prompt);
        
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
                    if (chatResponse.getResult() == null || chatResponse.getResult().getOutput() == null) {
                        return;
                    }
                    String content = chatResponse.getResult().getOutput().getText();
                    log.info("收到流式块: '{}' ({} 字符)", content, content != null ? content.length() : 0);
                })
                .map(chatResponse -> {
                    String content = (chatResponse.getResult() != null && chatResponse.getResult().getOutput() != null)
                            ? chatResponse.getResult().getOutput().getText()
                            : null;
                    if (content != null && !content.isEmpty()) {
                        fullContent.updateAndGet(prev -> prev + content);
                    }
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
                            reasoning.get(),
                            fixedMessageIndex
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
                    
                    // 提取代码块并序列化为JSON
                    String codeBlocksJson = null;
                    if (fullContent.get() != null && !fullContent.get().isEmpty()) {
                        List<CodeBlock> codeBlocks = CodeExtractor.extractCodeBlocks(fullContent.get());
                        if (codeBlocks != null && !codeBlocks.isEmpty()) {
                            codeBlocksJson = JSONUtil.toJsonStr(codeBlocks);
                            log.info("保存代码块: 模型={}, 代码块数={}", modelName, codeBlocks.size());
                        }
                    }
                    
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
                            reasoning.get(),
                            fixedMessageIndex
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
                            errorMessage,
                            fixedMessageIndex
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
            String reasoning,
            Integer messageIndex
    ) {
        // 计算思考时间（秒）
        Integer thinkingTime = null;
        if (reasoning != null && !reasoning.isEmpty()) {
            // 简单估算：每200个字符约1秒，最少1秒，最多60秒
            thinkingTime = Math.max(1, Math.min(reasoning.length() / 200, 60));
        }
        
        // 提取代码块（仅在完成时提取，避免流式过程中重复提取）
        List<CodeBlock> codeBlocks = null;
        Boolean hasCodeBlocks = false;
        if (done != null && done && fullContent != null && !fullContent.isEmpty()) {
            log.info("开始提取代码块，fullContent长度: {}", fullContent.length());
            log.debug("fullContent内容: {}", fullContent.substring(0, Math.min(500, fullContent.length())));
            
            codeBlocks = CodeExtractor.extractCodeBlocks(fullContent);
            
            log.info("提取结果: codeBlocks={}, size={}", 
                codeBlocks == null ? "null" : "not null", 
                codeBlocks == null ? 0 : codeBlocks.size());
            
            hasCodeBlocks = codeBlocks != null && !codeBlocks.isEmpty();
            if (hasCodeBlocks) {
                log.info("从响应中提取到 {} 个代码块", codeBlocks.size());
                for (int i = 0; i < codeBlocks.size(); i++) {
                    CodeBlock block = codeBlocks.get(i);
                    log.info("   代码块[{}]: language={}, codeLength={}", 
                        i, block.getLanguage(), block.getCode() == null ? 0 : block.getCode().length());
                }
            } else {
                log.warn("未提取到代码块！fullContent可能不包含```代码块格式");
                log.debug("fullContent内容:\n{}", fullContent);
            }
        }
        
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
                .messageIndex(messageIndex)
                .codeBlocks(codeBlocks)
                .hasCodeBlocks(hasCodeBlocks)
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
        return saveUserMessage(conversationId, userId, content, messageIndex, null);
    }

    /**
     * 保存用户消息（支持variantIndex）
     *
     * @return 返回保存的消息的messageIndex
     */
    private int saveUserMessage(String conversationId, Long userId, String content, int messageIndex, Integer variantIndex) {
        ConversationMessage message = ConversationMessage.builder()
                .id(IdUtil.randomUUID())
                .conversationId(conversationId)
                .userId(userId)
                .messageIndex(messageIndex)
                .role(MessageRoleEnum.USER.getValue())
                .content(content)
                .variantIndex(variantIndex)
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
        // 注意：在codeModePromptLabStream中，用户消息已经在调用createModelStreamWithSystemPrompt之前保存了
        // 所以这里不再重复保存用户消息，避免重复
        // 如果variantIndex不为null但fixedMessageIndex为null，说明是旧的Prompt Lab逻辑，需要保存用户消息
        if (variantIndex != null && fixedMessageIndex == null) {
            // 兼容旧逻辑：获取新的messageIndex（不推荐，会导致变体分散到不同的messageIndex）
            int userMessageIndex = getNextMessageIndex(conversationId);
            
            ConversationMessage userMessage = ConversationMessage.builder()
                    .id(IdUtil.randomUUID())
                    .conversationId(conversationId)
                    .userId(userId)
                    .messageIndex(userMessageIndex)
                    .role(MessageRoleEnum.USER.getValue())
                    .content(prompt)  // 不再添加"变体X: "前缀，因为variantIndex字段已经标识了变体
                    .variantIndex(variantIndex)  // 保存变体索引（用于Prompt Lab）
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
                .variantIndex(variantIndex)  // 保存变体索引（用于Prompt Lab）
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

        // 更新模型使用统计（全局）
        modelService.updateModelUsage(modelName, inputTokens + outputTokens, cost);

        // 更新用户-模型使用统计
        Conversation conversation = conversationMapper.selectOneById(conversationId);
        if (conversation != null && conversation.getUserId() != null) {
            userModelUsageService.updateUserModelUsage(
                    conversation.getUserId(),
                    modelName,
                    inputTokens + outputTokens,
                    cost
            );
        }
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
     * @param conversationId 会话ID
     * @param excludeMessageIndex 排除的messageIndex（当前正在创建的消息）
     * @param variantIndex 变体索引（用于Prompt Lab模式，只加载当前变体的消息）
     * @return 历史消息列表
     */
    private List<ConversationMessage> getHistoryMessagesForContext(String conversationId, Integer excludeMessageIndex, Integer variantIndex) {
        QueryWrapper wrapper = QueryWrapper.create()
                .from(ConversationMessage.class)
                .where("conversationId = ? and isDelete = 0", conversationId);
        
        // 如果是Prompt Lab模式，只加载当前变体的消息
        // 注意：为了兼容旧数据（variantIndex为null），我们加载所有消息，然后在后续处理中过滤
        // 如果严格只加载当前变体，可以使用：wrapper.and("variantIndex = ?", variantIndex);
        // 但为了兼容旧数据，我们暂时不在这里过滤，而是在后续处理中通过内容前缀判断
        if (variantIndex != null) {
            // 加载当前变体的消息，以及variantIndex为null的消息（旧数据兼容）
            wrapper.and("(variantIndex = ? or variantIndex is null)", variantIndex);
        }
        
        wrapper.orderBy("messageIndex", true);
        
        // 如果指定了排除的index，添加过滤条件
        if (excludeMessageIndex != null) {
            wrapper.and("messageIndex < ?", excludeMessageIndex);
        }
        
        List<ConversationMessage> messages = conversationMessageMapper.selectListByQuery(wrapper);
        log.info("📚 加载历史消息: 会话ID={}, variantIndex={}, 数量={}", conversationId, variantIndex, messages.size());
        
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
            String errorMessage,
            Integer messageIndex
    ) {
        return StreamChunkVO.builder()
                .conversationId(conversationId)
                .modelName(modelName)
                .variantIndex(variantIndex)
                .error(errorMessage)
                .hasError(true)
                .hasReasoning(false)
                .done(true)
                .messageIndex(messageIndex)
                .build();
    }
}
