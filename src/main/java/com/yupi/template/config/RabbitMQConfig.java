package com.yupi.template.config;

import com.yupi.template.constant.RabbitMQConstant;
import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.core.Queue;

import java.util.HashMap;
import java.util.Map;
import org.springframework.amqp.rabbit.config.SimpleRabbitListenerContainerFactory;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * RabbitMQ配置类
 *
 * @author <a href="https://codefather.cn">编程导航学习圈</a>
 */
@Configuration
public class RabbitMQConfig {

    /**
     * 测试任务队列（优先级队列 + 最大长度，消息持久化）。
     * 若已存在同名队列且未配置 x-max-priority/x-max-length，需先删除该队列再启动。
     */
    @Bean
    public Queue testQueue() {
        Map<String, Object> args = new HashMap<>();
        args.put("x-max-priority", RabbitMQConstant.QUEUE_MAX_PRIORITY);
        args.put("x-max-length", RabbitMQConstant.QUEUE_MAX_LENGTH);
        return new Queue(RabbitMQConstant.TEST_QUEUE, true, false, false, args);
    }

    /**
     * 测试任务交换器
     */
    @Bean
    public DirectExchange testExchange() {
        return new DirectExchange(RabbitMQConstant.TEST_EXCHANGE, true, false);
    }

    /**
     * 绑定队列到交换器
     */
    @Bean
    public Binding testBinding() {
        return BindingBuilder.bind(testQueue())
                .to(testExchange())
                .with(RabbitMQConstant.TEST_ROUTING_KEY);
    }

    /**
     * JSON消息转换器
     */
    @Bean
    public MessageConverter messageConverter() {
        return new Jackson2JsonMessageConverter();
    }

    /**
     * RabbitTemplate配置
     */
    @Bean
    public RabbitTemplate rabbitTemplate(ConnectionFactory connectionFactory) {
        RabbitTemplate template = new RabbitTemplate(connectionFactory);
        template.setMessageConverter(messageConverter());
        template.setMandatory(true);
        return template;
    }

    /**
     * 监听器容器工厂配置（Worker 并发优化）
     * prefetch=5 每消费者预取数；4~7 个并发消费者
     */
    @Bean
    public SimpleRabbitListenerContainerFactory rabbitListenerContainerFactory(ConnectionFactory connectionFactory) {
        SimpleRabbitListenerContainerFactory factory = new SimpleRabbitListenerContainerFactory();
        factory.setConnectionFactory(connectionFactory);
        factory.setMessageConverter(messageConverter());
        factory.setPrefetchCount(5);
        factory.setConcurrentConsumers(4);
        factory.setMaxConcurrentConsumers(7);
        return factory;
    }
}

