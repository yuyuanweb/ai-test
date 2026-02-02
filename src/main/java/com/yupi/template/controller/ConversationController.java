package com.yupi.template.controller;

import com.mybatisflex.core.paginate.Page;
import com.yupi.template.common.BaseResponse;
import com.yupi.template.common.DeleteRequest;
import com.yupi.template.common.ResultUtils;
import com.yupi.template.exception.ErrorCode;
import com.yupi.template.exception.ThrowUtils;
import com.yupi.template.model.dto.conversation.BattleRequest;
import com.yupi.template.model.dto.conversation.ChatRequest;
import com.yupi.template.model.dto.conversation.CreateConversationRequest;
import com.yupi.template.model.dto.conversation.PromptLabRequest;
import com.yupi.template.model.dto.conversation.SideBySideRequest;
import com.yupi.template.model.dto.conversation.CodeModeRequest;
import com.yupi.template.model.dto.conversation.CodeModePromptLabRequest;
import com.yupi.template.model.entity.Conversation;
import com.yupi.template.model.entity.ConversationMessage;
import com.yupi.template.model.entity.User;
import com.yupi.template.model.vo.BattleModelMappingVO;
import com.yupi.template.model.vo.StreamChunkVO;
import com.yupi.template.ratelimit.RateLimit;
import com.yupi.template.ratelimit.RateLimitType;
import com.yupi.template.service.ConversationService;
import com.yupi.template.service.UserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Resource;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Flux;

import java.util.List;

/**
 * 对话接口
 *
 * @author <a href="https://codefather.cn">编程导航学习圈</a>
 */
@RestController
@RequestMapping("/conversation")
@Slf4j
@Tag(name = "对话接口")
public class ConversationController {

    @Resource
    private ConversationService conversationService;

    @Resource
    private UserService userService;

    /**
     * 创建对话
     */
    @PostMapping("/create")
    @Operation(summary = "创建对话")
    public BaseResponse<String> createConversation(
            @RequestBody CreateConversationRequest request,
            HttpServletRequest httpRequest
    ) {
        User loginUser = userService.getLoginUser(httpRequest);
        String conversationId = conversationService.createConversation(request, loginUser.getId());
        return ResultUtils.success(conversationId);
    }

    /**
     * 基础对话（流式响应）
     */
    @RateLimit(limitType = RateLimitType.USER, rate = 5, rateInterval = 60, message = "AI 对话请求过于频繁，请稍后再试")
    @PostMapping(value = "/chat/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    @Operation(summary = "基础对话(流式)")
    public Flux<ServerSentEvent<StreamChunkVO>> chatStream(
            @RequestBody ChatRequest request,
            HttpServletRequest httpRequest
    ) {
        User loginUser = userService.getLoginUser(httpRequest);
        log.info("Chat stream request: user={}, model={}", 
                loginUser.getId(), request.getModel());
        return conversationService.chatStream(request, loginUser.getId());
    }

    /**
     * Side-by-Side 多模型并排对比（流式响应）
     */
    @RateLimit(limitType = RateLimitType.USER, rate = 5, rateInterval = 60, message = "AI 对话请求过于频繁，请稍后再试")
    @PostMapping(value = "/side-by-side/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    @Operation(summary = "Side-by-Side多模型并排对比(流式)")
    public Flux<ServerSentEvent<StreamChunkVO>> sideBySideStream(
            @RequestBody SideBySideRequest request,
            HttpServletRequest httpRequest
    ) {
        User loginUser = userService.getLoginUser(httpRequest);
        log.info("Side-by-Side stream request: user={}, models={}", 
                loginUser.getId(), request.getModels());
        return conversationService.sideBySideStream(request, loginUser.getId());
    }

    /**
     * Battle 匿名模型对比（流式响应）
     */
    @RateLimit(limitType = RateLimitType.USER, rate = 5, rateInterval = 60, message = "AI 对话请求过于频繁，请稍后再试")
    @PostMapping(value = "/battle/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    @Operation(summary = "Battle匿名模型对比(流式)")
    public Flux<ServerSentEvent<StreamChunkVO>> battleStream(
            @RequestBody BattleRequest request,
            HttpServletRequest httpRequest
    ) {
        User loginUser = userService.getLoginUser(httpRequest);
        log.info("Battle stream request: user={}, models={}", 
                loginUser.getId(), request.getModels());
        return conversationService.battleStream(request, loginUser.getId());
    }

    /**
     * Prompt Lab 单模型多提示词对比 (流式响应)
     */
    @RateLimit(limitType = RateLimitType.USER, rate = 5, rateInterval = 60, message = "AI 对话请求过于频繁，请稍后再试")
    @PostMapping(value = "/prompt-lab/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    @Operation(summary = "Prompt Lab单模型多提示词对比(流式)")
    public Flux<ServerSentEvent<StreamChunkVO>> promptLabStream(
            @RequestBody PromptLabRequest request,
            HttpServletRequest httpRequest
    ) {
        User loginUser = userService.getLoginUser(httpRequest);
        log.info("Prompt Lab stream request: user={}, model={}, variants={}",
                loginUser.getId(), request.getModel(), request.getPromptVariants().size());
        return conversationService.promptLabStream(request, loginUser.getId());
    }

    /**
     * Code Mode 代码模式 (流式响应)
     */
    @RateLimit(limitType = RateLimitType.USER, rate = 5, rateInterval = 60, message = "AI 对话请求过于频繁，请稍后再试")
    @PostMapping(value = "/code-mode/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    @Operation(summary = "代码模式(流式)")
    public Flux<ServerSentEvent<StreamChunkVO>> codeModeStream(
            @RequestBody CodeModeRequest request,
            HttpServletRequest httpRequest
    ) {
        User loginUser = userService.getLoginUser(httpRequest);
        log.info("Code Mode stream request: user={}, models={}",
                loginUser.getId(), request.getModels());
        return conversationService.codeModeStream(request, loginUser.getId());
    }

    /**
     * Code Mode 提示词实验 (流式响应) - 代码模式下的多提示词对比
     */
    @RateLimit(limitType = RateLimitType.USER, rate = 5, rateInterval = 60, message = "AI 对话请求过于频繁，请稍后再试")
    @PostMapping(value = "/code-mode/prompt-lab/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    @Operation(summary = "代码模式提示词实验(流式)")
    public Flux<ServerSentEvent<StreamChunkVO>> codeModePromptLabStream(
            @RequestBody CodeModePromptLabRequest request,
            HttpServletRequest httpRequest
    ) {
        User loginUser = userService.getLoginUser(httpRequest);
        log.info("Code Mode Prompt Lab stream request: user={}, model={}, variants={}",
                loginUser.getId(), request.getModel(), request.getPromptVariants().size());
        return conversationService.codeModePromptLabStream(request, loginUser.getId());
    }

    /**
     * 获取对话详情
     */
    @GetMapping("/get")
    @Operation(summary = "获取对话详情")
    public BaseResponse<Conversation> getConversation(
            @RequestParam String conversationId,
            HttpServletRequest httpRequest
    ) {
        User loginUser = userService.getLoginUser(httpRequest);
        Conversation conversation = conversationService.getConversation(conversationId, loginUser.getId());
        return ResultUtils.success(conversation);
    }

    /**
     * 获取对话列表（分页）
     */
    @GetMapping("/list")
    @Operation(summary = "获取对话列表")
    public BaseResponse<Page<Conversation>> listConversations(
            @RequestParam(defaultValue = "1") int pageNum,
            @RequestParam(defaultValue = "10") int pageSize,
            @RequestParam(required = false) Integer codePreviewEnabled,
            HttpServletRequest httpRequest
    ) {
        User loginUser = userService.getLoginUser(httpRequest);
        Boolean codePreviewEnabledBool = codePreviewEnabled != null ? (codePreviewEnabled == 1) : null;
        Page<Conversation> page = conversationService.listConversations(loginUser.getId(), pageNum, pageSize, codePreviewEnabledBool);
        return ResultUtils.success(page);
    }

    /**
     * 获取对话的所有消息
     */
    @GetMapping("/messages")
    @Operation(summary = "获取对话的所有消息")
    public BaseResponse<List<ConversationMessage>> getConversationMessages(
            @RequestParam String conversationId,
            HttpServletRequest httpRequest
    ) {
        User loginUser = userService.getLoginUser(httpRequest);
        List<ConversationMessage> messages = conversationService.getConversationMessages(conversationId, loginUser.getId());
        return ResultUtils.success(messages);
    }

    /**
     * 删除对话
     */
    @PostMapping("/delete")
    @Operation(summary = "删除对话")
    public BaseResponse<Boolean> deleteConversation(
            @RequestBody DeleteRequest deleteRequest,
            HttpServletRequest httpRequest
    ) {
        ThrowUtils.throwIf(deleteRequest == null || deleteRequest.getId() == null, ErrorCode.PARAMS_ERROR);
        User loginUser = userService.getLoginUser(httpRequest);
        boolean result = conversationService.deleteConversation(deleteRequest.getId().toString(), loginUser.getId());
        return ResultUtils.success(result);
    }

    /**
     * 获取Battle模式的模型映射关系（揭晓答案）
     */
    @GetMapping("/battle/mapping")
    @Operation(summary = "获取Battle模式模型映射关系（揭晓答案）")
    public BaseResponse<BattleModelMappingVO> getBattleModelMapping(
            @RequestParam String conversationId,
            HttpServletRequest httpRequest
    ) {
        User loginUser = userService.getLoginUser(httpRequest);
        BattleModelMappingVO mapping = conversationService.getBattleModelMapping(conversationId, loginUser.getId());
        return ResultUtils.success(mapping);
    }
}
