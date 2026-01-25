package com.yupi.template.model.vo;

import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;

/**
 * 报告摘要VO
 *
 * @author <a href="https://codefather.cn">编程导航学习圈</a>
 */
@Data
public class ReportSummaryVO implements Serializable {

    /**
     * 总成本(USD)
     */
    private BigDecimal totalCost;

    /**
     * 平均响应时间(毫秒)
     */
    private Double avgResponseTimeMs;

    /**
     * 总Token消耗
     */
    private Long totalTokens;

    /**
     * 测试结果总数
     */
    private Integer totalResults;

    /**
     * 参与测试的模型数量
     */
    private Integer modelCount;

    private static final long serialVersionUID = 1L;
}
