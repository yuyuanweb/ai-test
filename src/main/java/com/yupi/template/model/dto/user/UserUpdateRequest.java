package com.yupi.template.model.dto.user;

import lombok.Data;

import java.io.Serializable;

/**
 * 用户更新请求
 */
@Data
public class UserUpdateRequest implements Serializable {

    /**
     * id
     */
    private Long id;

    /**
     * 用户昵称
     */
    private String userName;

    /**
     * 用户头像
     */
    private String userAvatar;

    /**
     * 简介
     */
    private String userProfile;

    /**
     * 用户角色：user/admin
     */
    private String userRole;

    /**
     * 日预算限额(USD)
     */
    private java.math.BigDecimal dailyBudget;

    /**
     * 月预算限额(USD)
     */
    private java.math.BigDecimal monthlyBudget;

    /**
     * 预算预警阈值(百分比，默认80%)
     */
    private Integer budgetAlertThreshold;

    private static final long serialVersionUID = 1L;
}