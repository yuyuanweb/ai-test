package com.yupi.template.model.entity;

import com.mybatisflex.annotation.Column;
import com.mybatisflex.annotation.Id;
import com.mybatisflex.annotation.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serial;
import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 批量测试结果实体
 *
 * @author <a href="https://codefather.cn">编程导航学习圈</a>
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Table("test_result")
public class TestResult implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 结果唯一标识
     */
    @Id
    private String id;

    /**
     * 任务ID
     */
    @Column("taskId")
    private String taskId;

    /**
     * 用户ID
     */
    @Column("userId")
    private Long userId;

    /**
     * 场景ID
     */
    @Column("sceneId")
    private String sceneId;

    /**
     * 提示词ID
     */
    @Column("promptId")
    private String promptId;

    /**
     * 模型名称
     */
    @Column("modelName")
    private String modelName;

    /**
     * 输入提示词
     */
    @Column("inputPrompt")
    private String inputPrompt;

    /**
     * 输出内容
     */
    @Column("outputText")
    private String outputText;

    /**
     * 思考过程内容
     */
    @Column("reasoning")
    private String reasoning;

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
     * 用户评分(1-5)
     */
    @Column("userRating")
    private Integer userRating;

    /**
     * AI评分详情(多个评委模型的评分,JSON格式)
     */
    @Column("aiScore")
    private String aiScore;

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
    @Column(value = "isDelete", isLogicDelete = true)
    private Integer isDelete;
}

