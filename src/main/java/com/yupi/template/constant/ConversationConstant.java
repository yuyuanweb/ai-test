package com.yupi.template.constant;

/**
 * 对话常量
 *
 * @author <a href="https://codefather.cn">编程导航学习圈</a>
 */
public interface ConversationConstant {

    /**
     * 最小模型数量
     */
    int MIN_MODELS_COUNT = 1;

    /**
     * 最大模型数量
     */
    int MAX_MODELS_COUNT = 8;

    /**
     * 对话标题最大长度
     */
    int MAX_TITLE_LENGTH = 30;

    /**
     * 默认温度
     */
    double DEFAULT_TEMPERATURE = 0.7;

    /**
     * 默认输入Token价格（每百万Token）
     */
    double DEFAULT_INPUT_PRICE_PER_MILLION = 0.15;

    /**
     * 默认输出Token价格（每百万Token）
     */
    double DEFAULT_OUTPUT_PRICE_PER_MILLION = 0.60;

    /**
     * 每百万Token的除数
     */
    double TOKENS_PER_MILLION = 1000000.0;
}


