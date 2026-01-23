package com.yupi.template.service;

import cn.hutool.json.JSONUtil;
import com.google.gson.Gson;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;
import reactor.core.publisher.Flux;

import java.time.Duration;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

/**
 * AI流式测试 - 测试reasoning字段转换和实际AI流式调用
 */
@SpringBootTest
public class AIStreamTest {

    @Autowired
    @Qualifier("openAiChatModel")
    private ChatModel chatModel;

    private static final Gson gson = new Gson();

    @Test
    void testAIStreamWithReasoningPrompt() {
        System.out.println("\n🌊 === AI流式调用测试（带推理提示） ===");
        
        // 创建带推理提示的消息
        String message = "hi你好，你是谁，介绍下你自己，500字";
        Prompt prompt = new Prompt(message);
        
        System.out.println("📤 发送AI流式请求:");
        System.out.println("消息: " + message);
        
        long startTime = System.currentTimeMillis();
        AtomicReference<String> fullContent = new AtomicReference<>("");
        AtomicInteger chunkCount = new AtomicInteger(0);
        AtomicReference<String> firstChunk = new AtomicReference<>("");
        AtomicReference<String> lastChunk = new AtomicReference<>("");
        
        try {
            Flux<ChatResponse> flux = chatModel.stream(prompt);
            
            flux.doOnNext(chatResponse -> {
                        String content = chatResponse.getResult().getOutput().getText();
                        Object reasoningContent = chatResponse.getResult().getOutput().getMetadata();
                        System.out.println("思考 "+ JSONUtil.toJsonStr(reasoningContent));
                        System.out.println("输出内容 "+ content);
                        fullContent.updateAndGet(current -> current + content);
                        int count = chunkCount.incrementAndGet();
                        
                        if (count == 1) {
                            firstChunk.set(content);
                        }
                        lastChunk.set(content);
                        
                        long elapsed = System.currentTimeMillis() - startTime;
                        System.out.println("📦 流式块 #" + count + " (+" + elapsed + "ms): '" + content + "'");
                    })
                    .doOnComplete(() -> {
                        long duration = System.currentTimeMillis() - startTime;
                        System.out.println("✅ AI流式完成，总耗时: " + duration + "ms");
                    })
                    .doOnError(error -> {
                        System.err.println("❌ AI流式处理错误: " + error.getMessage());
                    })
                    .blockLast(Duration.ofSeconds(60)); // 60秒超时
                    
        } catch (Exception e) {
            System.err.println("❌ AI流式调用异常: " + e.getMessage());
        }

        System.out.println("  - 完整内容: " + fullContent.get());


    }

}
