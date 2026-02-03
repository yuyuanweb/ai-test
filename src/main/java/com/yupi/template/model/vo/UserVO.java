package com.yupi.template.model.vo;

import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 脱敏后的用户信息
 */
@Data
public class UserVO implements Serializable {

    /**
     * id
     */
    private Long id;
    
    /**
     * 账号
     */
    private String userAccount;

    /**
     * 用户昵称
     */
    private String userName;

    /**
     * 用户头像
     */
    private String userAvatar;

    /**
     * 用户简介
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

    /**
     * 创建时间
     */
    private LocalDateTime createTime;

    private static final long serialVersionUID = 1L;
}