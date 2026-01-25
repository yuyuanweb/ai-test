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
}

