package com.yupi.template.model.vo;

import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 测试结果VO
 *
 * @author <a href="https://codefather.cn">编程导航学习圈</a>
 */
@Data
public class TestResultVO implements Serializable {

    /**
     * 结果唯一标识
     */
    private String id;

    /**
     * 任务ID
     */
    private String taskId;

    /**
     * 场景ID
     */
    private String sceneId;

    /**
     * 提示词ID
     */
    private String promptId;

    /**
     * 模型名称
     */
    private String modelName;

    /**
     * 输入提示词
     */
    private String inputPrompt;

    /**
     * 输出内容
     */
    private String outputText;

    /**
     * 思考过程内容
     */
    private String reasoning;

    /**
     * 响应时间(毫秒)
     */
    private Integer responseTimeMs;

    /**
     * 输入Token数
     */
    private Integer inputTokens;

    /**
     * 输出Token数
     */
    private Integer outputTokens;

    /**
     * 成本(USD)
     */
    private BigDecimal cost;

    /**
     * 用户评分(1-5)
     */
    private Integer userRating;

    /**
     * AI评分详情(JSON字符串)
     */
    private String aiScore;

    /**
     * 创建时间
     */
    private LocalDateTime createTime;

    private static final long serialVersionUID = 1L;
}
