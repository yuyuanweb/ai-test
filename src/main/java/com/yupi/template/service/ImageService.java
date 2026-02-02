package com.yupi.template.service;

import com.yupi.template.model.dto.image.GenerateImageRequest;
import com.yupi.template.model.vo.GeneratedImageVO;
import com.yupi.template.model.vo.ImageStreamChunkVO;
import org.springframework.http.codec.ServerSentEvent;
import reactor.core.publisher.Flux;

import java.util.List;

/**
 * 图片生成服务
 *
 * @author <a href="https://codefather.cn">编程导航学习圈</a>
 */
public interface ImageService {

    /**
     * 生成图片
     *
     * @param request 图片生成请求
     * @param userId  用户 ID
     * @return 生成图片结果列表（包含 conversationId，如果保存到会话）
     */
    List<GeneratedImageVO> generateImages(GenerateImageRequest request, Long userId);

    /**
     * 流式生成图片（输出思考过程）
     *
     * @param request 图片生成请求
     * @param userId  用户 ID
     * @return SSE 流式响应
     */
    Flux<ServerSentEvent<ImageStreamChunkVO>> generateImagesStream(GenerateImageRequest request, Long userId);
}

