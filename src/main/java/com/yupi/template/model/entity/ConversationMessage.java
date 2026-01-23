package com.yupi.template.model.entity;

import com.mybatisflex.annotation.Column;
import com.mybatisflex.annotation.Id;
import com.mybatisflex.annotation.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 对话消息实体
 *
 * @author <a href="https://codefather.cn">编程导航学习圈</a>
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Table("conversation_message")
public class ConversationMessage implements Serializable {

    /**
     * 消息唯一标识
     */
    @Id
    private String id;

    /**
     * 对话ID
     */
    @Column("conversationId")
    private String conversationId;

    /**
     * 用户ID
     */
    @Column("userId")
    private Long userId;

    /**
     * 消息序号(从0开始)
     */
    @Column("messageIndex")
    private Integer messageIndex;

    /**
     * 角色: user/assistant
     */
    @Column("role")
    private String role;

    /**
     * 模型名称(assistant消息)
     */
    @Column("modelName")
    private String modelName;

    /**
     * 消息内容
     */
    @Column("content")
    private String content;

    /**
     * 响应时间(毫秒)
     */
    @Column("responseTimeMs")
    private Integer responseTimeMs;

    /**
     * 输入Token数
     */
    @Column("inputTokens")
    private Integer inputTokens;

    /**
     * 输出Token数
     */
    @Column("outputTokens")
    private Integer outputTokens;

    /**
     * 成本(USD)
     */
    @Column("cost")
    private BigDecimal cost;

    /**
     * 思考过程（thinking模式）
     */
    @Column("reasoning")
    private String reasoning;

    /**
     * 代码块列表（JSON格式）
     */
    @Column("codeBlocks")
    private String codeBlocks;

    /**
     * 创建时间
     */
    @Column("createTime")
    private LocalDateTime createTime;

    /**
     * 更新时间
     */
    @Column("updateTime")
    private LocalDateTime updateTime;

    /**
     * 是否删除
     */
    @Column("isDelete")
    private Integer isDelete;

    private static final long serialVersionUID = 1L;
}
