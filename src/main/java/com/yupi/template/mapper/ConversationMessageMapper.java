package com.yupi.template.mapper;

import com.mybatisflex.core.BaseMapper;
import com.yupi.template.model.entity.ConversationMessage;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/**
 * 对话消息 Mapper
 *
 * @author <a href="https://codefather.cn">编程导航学习圈</a>
 */
public interface ConversationMessageMapper extends BaseMapper<ConversationMessage> {

    /**
     * 查询对话消息（正确处理 JSON 字段）
     * 使用 JSON_UNQUOTE 函数将 JSON 类型字段转换为字符串
     *
     * @param conversationId 对话ID
     * @return 消息列表
     */
    @Select("SELECT id, conversationId, userId, messageIndex, role, modelName, " +
            "variantIndex, content, " +
            "IFNULL(JSON_UNQUOTE(images), images) as images, " +
            "IFNULL(JSON_UNQUOTE(toolsUsed), toolsUsed) as toolsUsed, " +
            "responseTimeMs, inputTokens, outputTokens, cost, " +
            "reasoning, codeBlocks, createTime, updateTime, isDelete " +
            "FROM conversation_message " +
            "WHERE conversationId = #{conversationId} AND isDelete = 0 " +
            "ORDER BY messageIndex ASC")
    List<ConversationMessage> selectByConversationIdWithImages(@Param("conversationId") String conversationId);
}
