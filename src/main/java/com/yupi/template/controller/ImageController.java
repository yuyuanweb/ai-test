package com.yupi.template.controller;

import com.yupi.template.common.BaseResponse;
import com.yupi.template.common.ResultUtils;
import com.yupi.template.exception.ErrorCode;
import com.yupi.template.exception.ThrowUtils;
import com.yupi.template.model.dto.image.GenerateImageRequest;
import com.yupi.template.model.entity.User;
import com.yupi.template.model.vo.GeneratedImageVO;
import com.yupi.template.model.vo.ImageStreamChunkVO;
import com.yupi.template.service.ImageService;
import com.yupi.template.service.UserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Resource;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.MediaType;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Flux;

import java.util.List;

/**
 * 图片生成控制层
 *
 * @author <a href="https://codefather.cn">编程导航学习圈</a>
 */
@RestController
@RequestMapping("/image")
@Tag(name = "图片生成接口")
public class ImageController {

    @Resource
    private ImageService imageService;

    @Resource
    private UserService userService;

    /**
     * 生成图片
     *
     * @param request  请求参数
     * @param httpRequest HTTP 请求
     * @return 生成的图片结果列表
     */
    @PostMapping("/generate")
    @Operation(summary = "生成图片（文本 / 图生图）")
    public BaseResponse<List<GeneratedImageVO>> generateImage(@RequestBody @Valid GenerateImageRequest request,
                                                              HttpServletRequest httpRequest) {
        ThrowUtils.throwIf(request == null, ErrorCode.PARAMS_ERROR);
        User loginUser = userService.getLoginUser(httpRequest);
        List<GeneratedImageVO> resultList = imageService.generateImages(request, loginUser.getId());
        return ResultUtils.success(resultList);
    }

    /**
     * 流式生成图片（输出思考过程）
     *
     * @param request     请求参数
     * @param httpRequest HTTP 请求
     * @return SSE 流式响应
     */
    @PostMapping(value = "/generate/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    @Operation(summary = "流式生成图片（输出思考过程）")
    public Flux<ServerSentEvent<ImageStreamChunkVO>> generateImageStream(
            @RequestBody @Valid GenerateImageRequest request,
            HttpServletRequest httpRequest) {
        ThrowUtils.throwIf(request == null, ErrorCode.PARAMS_ERROR);
        User loginUser = userService.getLoginUser(httpRequest);
        return imageService.generateImagesStream(request, loginUser.getId());
    }
}

