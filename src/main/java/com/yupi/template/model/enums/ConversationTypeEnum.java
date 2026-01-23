package com.yupi.template.model.enums;

import lombok.Getter;

/**
 * 对话类型枚举
 *
 * @author <a href="https://codefather.cn">编程导航学习圈</a>
 */
@Getter
public enum ConversationTypeEnum {

    /**
     * Side-by-Side 并排对比
     */
    SIDE_BY_SIDE("side_by_side", "并排对比"),

    /**
     * Prompt Lab 提示词实验
     */
    PROMPT_LAB("prompt_lab", "提示词实验"),

    /**
     * Code Mode 代码模式
     */
    CODE_MODE("code_mode", "代码模式");

    private final String value;
    private final String text;

    ConversationTypeEnum(String value, String text) {
        this.value = value;
        this.text = text;
    }

    /**
     * 根据value获取枚举
     */
    public static ConversationTypeEnum getEnumByValue(String value) {
        if (value == null) {
            return null;
        }
        for (ConversationTypeEnum typeEnum : ConversationTypeEnum.values()) {
            if (typeEnum.getValue().equals(value)) {
                return typeEnum;
            }
        }
        return null;
    }
}

