package com.yupi.template.model.vo;

import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;

/**
 * 用户统计数据
 *
 * @author <a href="https://codefather.cn">编程导航学习圈</a>
 */
@Data
public class UserStatisticsVO implements Serializable {

    /**
     * 模型总数
     */
    private Long totalModels;

    /**
     * 总Tokens使用量
     */
    private Long totalTokens;

    /**
     * 总花费 (USD)
     */
    private BigDecimal totalCost;

    private static final long serialVersionUID = 1L;
}
