package com.yupi.template.constant;

/**
 * RabbitMQ常量
 *
 * @author <a href="https://codefather.cn">编程导航学习圈</a>
 */
public interface RabbitMQConstant {

    /**
     * AI大模型批量测试任务队列名称
     */
    String TEST_QUEUE = "ai-model-batch-test.queue";

    /**
     * AI大模型批量测试任务交换器名称
     */
    String TEST_EXCHANGE = "ai-model-batch-test.exchange";

    /**
     * AI大模型批量测试任务路由键
     */
    String TEST_ROUTING_KEY = "ai-model-batch-test.routing.key";

    /**
     * 优先级队列最大优先级（0–10，越大越优先）
     */
    int QUEUE_MAX_PRIORITY = 10;

    /**
     * 队列最大消息数，超出时丢弃最老消息，防止堆积 OOM
     */
    int QUEUE_MAX_LENGTH = 10000;

    /**
     * 高优先级：子任务数少，尽快执行
     */
    int PRIORITY_HIGH = 10;

    /**
     * 普通优先级
     */
    int PRIORITY_NORMAL = 5;

    /**
     * 低优先级：大任务
     */
    int PRIORITY_LOW = 1;

    /**
     * 子任务数小于等于此值视为高优先级
     */
    int TOTAL_SUBTASKS_THRESHOLD_HIGH = 10;

    /**
     * 子任务数小于等于此值视为普通优先级
     */
    int TOTAL_SUBTASKS_THRESHOLD_NORMAL = 50;
}

