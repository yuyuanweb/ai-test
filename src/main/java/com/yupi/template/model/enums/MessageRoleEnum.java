package com.yupi.template.model.enums;

import lombok.Getter;

/**
 * 消息角色枚举
 *
 * @author <a href="https://codefather.cn">编程导航学习圈</a>
 */
@Getter
public enum MessageRoleEnum {

    /**
     * 用户
     */
    USER("user", "用户"),

    /**
     * AI助手
     */
    ASSISTANT("assistant", "助手");

    private final String value;
    private final String text;

    MessageRoleEnum(String value, String text) {
        this.value = value;
        this.text = text;
    }

    /**
     * 根据value获取枚举
     */
    public static MessageRoleEnum getEnumByValue(String value) {
        if (value == null) {
            return null;
        }
        for (MessageRoleEnum roleEnum : MessageRoleEnum.values()) {
            if (roleEnum.getValue().equals(value)) {
                return roleEnum;
            }
        }
        return null;
    }
}

