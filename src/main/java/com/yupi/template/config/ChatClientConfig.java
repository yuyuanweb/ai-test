package com.yupi.template.config;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * ChatClient 配置类
 *
 * @author <a href="https://codefather.cn">编程导航学习圈</a>
 */
@Configuration
public class ChatClientConfig {

    /**
     * 配置 ChatClient Bean
     */
    @Bean
    public ChatClient chatClient(ChatClient.Builder builder) {
        return builder.build();
    }
}
