package com.yupi.template.mapper;

import com.mybatisflex.core.BaseMapper;
import com.yupi.template.model.entity.ConversationMessage;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.math.BigDecimal;
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

    /**
     * 查询用户今日花费
     *
     * @param userId 用户ID
     * @return 今日花费
     */
    @Select("SELECT COALESCE(SUM(cost), 0) FROM conversation_message " +
            "WHERE userId = #{userId} AND isDelete = 0 " +
            "AND DATE(createTime) = CURDATE()")
    BigDecimal selectTodayCostByUserId(@Param("userId") Long userId);

    /**
     * 查询用户本月花费
     *
     * @param userId 用户ID
     * @return 本月花费
     */
    @Select("SELECT COALESCE(SUM(cost), 0) FROM conversation_message " +
            "WHERE userId = #{userId} AND isDelete = 0 " +
            "AND YEAR(createTime) = YEAR(CURDATE()) " +
            "AND MONTH(createTime) = MONTH(CURDATE())")
    BigDecimal selectMonthCostByUserId(@Param("userId") Long userId);

    /**
     * 查询用户本周花费
     *
     * @param userId 用户ID
     * @return 本周花费
     */
    @Select("SELECT COALESCE(SUM(cost), 0) FROM conversation_message " +
            "WHERE userId = #{userId} AND isDelete = 0 " +
            "AND YEARWEEK(createTime, 1) = YEARWEEK(CURDATE(), 1)")
    BigDecimal selectWeekCostByUserId(@Param("userId") Long userId);

    /**
     * 查询用户今日API调用次数
     *
     * @param userId 用户ID
     * @return 今日调用次数
     */
    @Select("SELECT COUNT(*) FROM conversation_message " +
            "WHERE userId = #{userId} AND isDelete = 0 " +
            "AND role = 'assistant' " +
            "AND DATE(createTime) = CURDATE()")
    Long selectTodayApiCallsByUserId(@Param("userId") Long userId);

    /**
     * 查询用户总API调用次数
     *
     * @param userId 用户ID
     * @return 总调用次数
     */
    @Select("SELECT COUNT(*) FROM conversation_message " +
            "WHERE userId = #{userId} AND isDelete = 0 " +
            "AND role = 'assistant'")
    Long selectTotalApiCallsByUserId(@Param("userId") Long userId);

    /**
     * 查询用户今日Token消耗
     *
     * @param userId 用户ID
     * @return 今日Token消耗
     */
    @Select("SELECT COALESCE(SUM(inputTokens + outputTokens), 0) FROM conversation_message " +
            "WHERE userId = #{userId} AND isDelete = 0 " +
            "AND DATE(createTime) = CURDATE()")
    Long selectTodayTokensByUserId(@Param("userId") Long userId);

    /**
     * 查询用户总Token消耗（输入）
     *
     * @param userId 用户ID
     * @return 总输入Token
     */
    @Select("SELECT COALESCE(SUM(inputTokens), 0) FROM conversation_message " +
            "WHERE userId = #{userId} AND isDelete = 0")
    Long selectTotalInputTokensByUserId(@Param("userId") Long userId);

    /**
     * 查询用户总Token消耗（输出）
     *
     * @param userId 用户ID
     * @return 总输出Token
     */
    @Select("SELECT COALESCE(SUM(outputTokens), 0) FROM conversation_message " +
            "WHERE userId = #{userId} AND isDelete = 0")
    Long selectTotalOutputTokensByUserId(@Param("userId") Long userId);
}
