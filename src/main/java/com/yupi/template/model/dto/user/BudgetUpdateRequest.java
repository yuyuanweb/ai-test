package com.yupi.template.model.dto.user;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.math.BigDecimal;

/**
 * 预算更新请求
 *
 * @author <a href="https://codefather.cn">编程导航学习圈</a>
 */
@Data
public class BudgetUpdateRequest implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 日预算限额(USD)，null或0表示不限制
     */
    private BigDecimal dailyBudget;

    /**
     * 月预算限额(USD)，null或0表示不限制
     */
    private BigDecimal monthlyBudget;

    /**
     * 预算预警阈值(百分比，1-99)，默认80
     */
    private Integer alertThreshold;
}
