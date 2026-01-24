package com.yupi.template.service;

import com.mybatisflex.core.paginate.Page;
import com.yupi.template.model.dto.conversation.ChatRequest;
import com.yupi.template.model.dto.conversation.CreateConversationRequest;
import com.yupi.template.model.dto.conversation.PromptLabRequest;
import com.yupi.template.model.dto.conversation.SideBySideRequest;
import com.yupi.template.model.dto.conversation.CodeModeRequest;
import com.yupi.template.model.dto.conversation.CodeModePromptLabRequest;
import com.yupi.template.model.entity.Conversation;
import com.yupi.template.model.entity.ConversationMessage;
import com.yupi.template.model.vo.StreamChunkVO;
import org.springframework.http.codec.ServerSentEvent;
import reactor.core.publisher.Flux;

import java.util.List;

/**
 * 对话服务接口
 *
 * @author <a href="https://codefather.cn">编程导航学习圈</a>
 */
public interface ConversationService {

    /**
     * 创建对话
     *
     * @param request 请求参数
     * @param userId  用户ID
     * @return 对话ID
     */
    String createConversation(CreateConversationRequest request, Long userId);

    /**
     * 基础对话 (流式响应)
     *
     * @param request 请求参数
     * @param userId  用户ID
     * @return 流式响应
     */
    Flux<ServerSentEvent<StreamChunkVO>> chatStream(ChatRequest request, Long userId);

    /**
     * Side-by-Side 并排对比 (流式响应)
     *
     * @param request 请求参数
     * @param userId  用户ID
     * @return 流式响应
     */
    Flux<ServerSentEvent<StreamChunkVO>> sideBySideStream(SideBySideRequest request, Long userId);

    /**
     * Prompt Lab 单模型多提示词对比 (流式响应)
     *
     * @param request 请求参数
     * @param userId  用户ID
     * @return 流式响应
     */
    Flux<ServerSentEvent<StreamChunkVO>> promptLabStream(PromptLabRequest request, Long userId);

    /**
     * Code Mode 代码模式 (流式响应)
     *
     * @param request 请求参数
     * @param userId  用户ID
     * @return 流式响应
     */
    Flux<ServerSentEvent<StreamChunkVO>> codeModeStream(CodeModeRequest request, Long userId);

    /**
     * Code Mode 提示词实验 (流式响应) - 代码模式下的多提示词对比
     *
     * @param request 请求参数
     * @param userId  用户ID
     * @return 流式响应
     */
    Flux<ServerSentEvent<StreamChunkVO>> codeModePromptLabStream(CodeModePromptLabRequest request, Long userId);

    /**
     * 获取对话详情
     *
     * @param conversationId 对话ID
     * @param userId         用户ID
     * @return 对话记录
     */
    Conversation getConversation(String conversationId, Long userId);

    /**
     * 获取对话列表（分页）
     *
     * @param userId   用户ID
     * @param pageNum  页码
     * @param pageSize 每页大小
     * @param codePreviewEnabled 是否启用代码预览（可选，null表示不过滤）
     * @return 分页结果
     */
    Page<Conversation> listConversations(Long userId, int pageNum, int pageSize, Boolean codePreviewEnabled);

    /**
     * 获取对话的所有消息
     *
     * @param conversationId 对话ID
     * @param userId         用户ID
     * @return 消息列表
     */
    List<ConversationMessage> getConversationMessages(String conversationId, Long userId);

    /**
     * 删除对话
     *
     * @param conversationId 对话ID
     * @param userId         用户ID
     * @return 是否成功
     */
    boolean deleteConversation(String conversationId, Long userId);
}
