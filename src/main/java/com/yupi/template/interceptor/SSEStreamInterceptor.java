package com.yupi.template.interceptor;

import com.google.gson.Gson;
import com.google.gson.JsonSyntaxException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.core.io.buffer.DataBufferFactory;
import org.springframework.core.io.buffer.DefaultDataBufferFactory;
import org.springframework.http.MediaType;
import org.springframework.web.reactive.function.client.ClientResponse;
import org.springframework.web.reactive.function.client.ExchangeFilterFunction;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicReference;

/**
 * SSE流式数据拦截器 - 专门用于转换reasoning字段为reasoning_content
 */
public class SSEStreamInterceptor {

    private static final Logger log = LoggerFactory.getLogger(SSEStreamInterceptor.class);
    private static final Gson gson = new Gson();


    /**
     * 创建SSE流式响应拦截器
     */
    public static ExchangeFilterFunction interceptResponse() {
        return ExchangeFilterFunction.ofResponseProcessor(clientResponse -> {
            org.springframework.http.HttpHeaders headers = clientResponse.headers().asHttpHeaders();
            MediaType contentType = headers.getContentType();

            // 检查是否是流式响应
            boolean isStreamResponse = contentType != null && (
                    contentType.includes(MediaType.TEXT_EVENT_STREAM) ||
                            contentType.includes(MediaType.parseMediaType("application/x-ndjson")) ||
                            contentType.includes(MediaType.TEXT_PLAIN) ||
                            contentType.includes(MediaType.APPLICATION_OCTET_STREAM)
            );

            if (isStreamResponse) {
                // 创建数据缓冲区工厂
                DataBufferFactory bufferFactory = new DefaultDataBufferFactory();

                // 用于累积不完整的字节数据
                AtomicReference<byte[]> incompleteBytes = new AtomicReference<>(new byte[0]);

                // 创建一个新的响应，包装原始的body流并进行reasoning字段转换
                ClientResponse wrappedResponse = ClientResponse.from(clientResponse)
                        .body(processStreamWithReasoningTransform(
                                clientResponse.bodyToFlux(DataBuffer.class),
                                bufferFactory,
                                incompleteBytes
                        ))
                        .build();

                return Mono.just(wrappedResponse);
            } else {
                // 读取响应body并检查是否是错误
                return clientResponse.bodyToMono(String.class)
                        .flatMap(body -> {
                            // 检查是否是错误响应
                            if (clientResponse.statusCode().isError() || body.contains("\"error\"")) {
                                // 解析错误信息
                                String errorMessage = parseErrorMessage(body);
                                log.error(" API返回错误: {}", errorMessage);
                                // 抛出异常，让调用方知道发生了错误
                                return Mono.error(new RuntimeException("API Error: " + errorMessage));
                            }
                            
                            // 不是错误，重新构建响应
                            DataBufferFactory bufferFactory = new DefaultDataBufferFactory();
                            DataBuffer buffer = bufferFactory.wrap(body.getBytes(StandardCharsets.UTF_8));
                            
                            ClientResponse newResponse = ClientResponse.from(clientResponse)
                                    .body(Flux.just(buffer))
                                    .build();
                            
                            return Mono.just(newResponse);
                        })
                        .onErrorResume(error -> {
                            log.error("处理非流式响应失败: {}", error.getMessage());
                            return Mono.error(error); // 继续传递错误
                        });
            }
        });
    }

    /**
     * 解析错误响应中的错误信息
     */
    private static String parseErrorMessage(String errorBody) {
        try {
            // 使用Gson解析JSON错误响应
            com.google.gson.JsonObject jsonObject = gson.fromJson(errorBody, com.google.gson.JsonObject.class);

            // 检查是否有error字段
            if (jsonObject.has("error")) {
                com.google.gson.JsonElement errorElement = jsonObject.get("error");

                // error可能是字符串或对象
                if (errorElement.isJsonPrimitive()) {
                    return errorElement.getAsString();
                } else if (errorElement.isJsonObject()) {
                    com.google.gson.JsonObject errorObj = errorElement.getAsJsonObject();

                    // 优先获取message字段
                    if (errorObj.has("message")) {
                        return errorObj.get("message").getAsString();
                    }

                    // 如果没有message，尝试获取其他字段
                    if (errorObj.has("error")) {
                        return errorObj.get("error").getAsString();
                    }

                    // 返回整个error对象的字符串
                    return errorObj.toString();
                }
            }

            // 如果没有error字段，检查是否有message字段
            if (jsonObject.has("message")) {
                return jsonObject.get("message").getAsString();
            }

            // 如果都没有，返回整个JSON（截取前200字符）
            return errorBody.substring(0, Math.min(200, errorBody.length()));

        } catch (JsonSyntaxException e) {
            log.warn("JSON解析失败，返回原始错误信息: {}", e.getMessage());
            return errorBody.substring(0, Math.min(200, errorBody.length()));
        } catch (Exception e) {
            log.error("解析错误信息失败: {}", e.getMessage());
            return errorBody.substring(0, Math.min(200, errorBody.length()));
        }
    }
    /**
     * 处理流式数据并进行reasoning字段转换
     */
    private static Flux<DataBuffer> processStreamWithReasoningTransform(
            Flux<DataBuffer> originalStream,
            DataBufferFactory bufferFactory,
            AtomicReference<byte[]> incompleteBytes) {

        return originalStream
                .map(dataBuffer -> {
                    try {
                        // 读取原始字节数据
                        byte[] newBytes = new byte[dataBuffer.readableByteCount()];
                        dataBuffer.read(newBytes);

                        // 获取之前累积的不完整字节
                        byte[] previousBytes = incompleteBytes.get();

                        // 合并字节数据
                        byte[] combinedBytes = new byte[previousBytes.length + newBytes.length];
                        System.arraycopy(previousBytes, 0, combinedBytes, 0, previousBytes.length);
                        System.arraycopy(newBytes, 0, combinedBytes, previousBytes.length, newBytes.length);

                        // 找到最后一个完整的UTF-8字符边界
                        int validEnd = findValidUtf8End(combinedBytes);

                        String result = "";
                        byte[] remainingBytes = new byte[0];

                        if (validEnd > 0) {
                            // 提取有效的UTF-8字符串
                            byte[] validBytes = new byte[validEnd];
                            System.arraycopy(combinedBytes, 0, validBytes, 0, validEnd);
                            String validChunk = new String(validBytes, StandardCharsets.UTF_8);

                            // 保存剩余的不完整字节
                            if (validEnd < combinedBytes.length) {
                                remainingBytes = new byte[combinedBytes.length - validEnd];
                                System.arraycopy(combinedBytes, validEnd, remainingBytes, 0, remainingBytes.length);
                            }

                            // 直接处理有效数据块，保持原有顺序
                            result = transformReasoningFields(validChunk);

                        } else {
                            // 没有完整字符，全部保存为不完整字节
                            remainingBytes = combinedBytes;
                        }

                        // 更新不完整字节缓存
                        incompleteBytes.set(remainingBytes);

                        log.debug("处理数据块 - 总字节: {}, 有效字节: {}, 剩余字节: {}",
                                combinedBytes.length, validEnd, remainingBytes.length);

                        // 释放原始buffer
                        org.springframework.core.io.buffer.DataBufferUtils.release(dataBuffer);

                        // 创建新的buffer
                        if (!result.isEmpty()) {
                            return bufferFactory.wrap(result.getBytes(StandardCharsets.UTF_8));
                        } else {
                            return bufferFactory.allocateBuffer(0);
                        }

                    } catch (Exception e) {
                        log.error("处理流数据块时出错: {}", e.getMessage(), e);
                        // 出错时返回原始数据
                        return dataBuffer;
                    }
                })
                .filter(dataBuffer -> dataBuffer.readableByteCount() > 0)
                .doOnComplete(() -> {
                    // 处理最后的不完整字节数据
                    byte[] remaining = incompleteBytes.get();
                    if (remaining.length > 0) {
                        try {
                            String remainingStr = new String(remaining, StandardCharsets.UTF_8);
                            log.debug("处理剩余字节数据: {} 字节, 内容: {}", remaining.length, remainingStr);
                        } catch (Exception e) {
                            log.debug("剩余字节数据无法解码为UTF-8: {} 字节", remaining.length);
                        }
                    }
                    log.info("SSE流处理完成");
                })
                .doOnError(error -> {
                    log.error("SSE流处理错误", error);
                });
    }

    /**
     * 转换reasoning字段为reasoning_content - 支持思考模型，保持顺序
     */
    private static String transformReasoningFields(String chunk) {
        if (chunk == null || chunk.isEmpty()) {
            return chunk;
        }

        try {
            // 对于思考模型，使用更简单的字符串替换方式，避免JSON解析重排序
            if (chunk.contains("\"reasoning\":")) {
                log.debug("检测到reasoning字段，进行简单字符串替换转换");

                // 使用简单的字符串替换，保持原有格式和顺序
                String result = chunk.replaceAll("\"reasoning\":", "\"reasoning_content\":");

                log.debug("reasoning字段转换完成");
                return result;
            }

            // 如果没有reasoning字段，直接返回原始数据
            return chunk;

        } catch (Exception e) {
            log.error("转换数据块时出错，返回原始数据: {}", e.getMessage());
            return chunk;
        }
    }




    /**
     * 找到字节数组中最后一个完整UTF-8字符的结束位置
     */
    private static int findValidUtf8End(byte[] bytes) {
        if (bytes.length == 0) {
            return 0;
        }

        // 从末尾开始检查，找到最后一个完整的UTF-8字符
        for (int i = bytes.length - 1; i >= 0; i--) {
            byte b = bytes[i];

            // ASCII字符 (0xxxxxxx)
            if ((b & 0x80) == 0) {
                return i + 1;
            }

            // UTF-8多字节字符的开始字节
            if ((b & 0xC0) == 0xC0) {
                // 计算这个字符需要的字节数
                int expectedBytes = 0;
                if ((b & 0xE0) == 0xC0) expectedBytes = 2; // 110xxxxx
                else if ((b & 0xF0) == 0xE0) expectedBytes = 3; // 1110xxxx
                else if ((b & 0xF8) == 0xF0) expectedBytes = 4; // 11110xxx

                // 检查是否有足够的字节
                if (i + expectedBytes <= bytes.length) {
                    return i + expectedBytes;
                } else {
                    // 字符不完整，返回这个字符开始之前的位置
                    return i;
                }
            }
        }

        // 如果没有找到完整字符，返回0
        return 0;
    }

}