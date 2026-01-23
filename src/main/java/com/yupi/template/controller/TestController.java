package com.yupi.template.controller;

import com.yupi.template.common.BaseResponse;
import com.yupi.template.common.ResultUtils;
import com.yupi.template.model.vo.StreamChunkVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.context.annotation.Profile;
import org.springframework.http.MediaType;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Flux;

import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

/**
 * 测试接口 - 用于验证流式响应功能
 *
 * @author <a href="https://codefather.cn">编程导航学习圈</a>
 */
@RestController
@RequestMapping("/test")
@Slf4j
@Tag(name = "测试接口")
@Profile({"local", "dev"})
public class TestController {

    @Resource
    private ChatClient chatClient;


    /**
     * 测试AI调用（非流式）
     */
    @PostMapping("/ai/simple")
    @Operation(summary = "测试AI调用（非流式）")
    public BaseResponse<String> testAiSimple(@RequestParam String prompt) {
        log.info("Testing simple AI call with prompt: {}", prompt);

        String response = chatClient.prompt()
                .user(prompt)
                .call()
                .content();
        return ResultUtils.success(response);
    }

    /**
     * 测试AI流式调用
     */
    @PostMapping(value = "/ai/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    @Operation(summary = "测试AI流式调用")
    public Flux<ServerSentEvent<StreamChunkVO>> testAiStream(
            @RequestParam String prompt,
            @RequestParam(required = false, defaultValue = "qwen/qwen-plus") String model
    ) {
        log.info("Testing stream AI call with prompt: {}, model: {}", prompt, model);
        
        AtomicLong startTime = new AtomicLong(System.currentTimeMillis());
        AtomicReference<String> fullContent = new AtomicReference<>("");
        AtomicInteger outputTokens = new AtomicInteger(0);
        
        return chatClient.prompt()
                .user(prompt)
                .options(OpenAiChatOptions.builder()
                        .model(model)
                        .temperature(0.7)
                        .build())
                .stream()
                .content()
                .map(content -> {
                    fullContent.updateAndGet(prev -> prev + content);
                    outputTokens.addAndGet(content.length() / 4);
                    long elapsedMs = System.currentTimeMillis() - startTime.get();
                    
                    StreamChunkVO chunkVO = StreamChunkVO.builder()
                            .modelName(model)
                            .content(content)
                            .fullContent(fullContent.get())
                            .outputTokens(outputTokens.get())
                            .elapsedMs(elapsedMs)
                            .done(false)
                            .hasError(false)
                            .build();
                    
                    return ServerSentEvent.<StreamChunkVO>builder()
                            .data(chunkVO)
                            .build();
                })
                .concatWithValues(
                        ServerSentEvent.<StreamChunkVO>builder()
                                .data(StreamChunkVO.builder()
                                        .modelName(model)
                                        .fullContent(fullContent.get())
                                        .outputTokens(outputTokens.get())
                                        .responseTimeMs((int)(System.currentTimeMillis() - startTime.get()))
                                        .done(true)
                                        .hasError(false)
                                        .build())
                                .build()
                )
                .onErrorResume(error -> {
                    // 捕获错误并返回错误信息
                    log.error("Stream error for model {}: {}", model, error.getMessage(), error);
                    
                    StreamChunkVO errorVO = StreamChunkVO.builder()
                            .modelName(model)
                            .error(error.getMessage())
                            .hasError(true)
                            .done(true)
                            .build();
                    
                    return Flux.just(ServerSentEvent.<StreamChunkVO>builder()
                            .data(errorVO)
                            .build());
                })
                .doOnComplete(() -> log.info("Stream completed for model: {}", model));
    }

}

