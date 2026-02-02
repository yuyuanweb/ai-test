package com.yupi.template.model.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serial;
import java.io.Serializable;

/**
 * 图像生成流式响应块 VO
 *
 * @author <a href="https://codefather.cn">编程导航学习圈</a>
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "图像生成流式响应块")
public class ImageStreamChunkVO implements Serializable {

    /**
     * 事件类型：thinking(思考中), image(图片生成完成), done(全部完成), error(错误)
     */
    @Schema(description = "事件类型：thinking/image/done/error")
    private String type;

    /**
     * 思考内容（type=thinking 时使用）
     */
    @Schema(description = "思考内容")
    private String thinking;

    /**
     * 完整思考内容（累积）
     */
    @Schema(description = "完整思考内容")
    private String fullThinking;

    /**
     * 生成的图片信息（type=image 时使用）
     */
    @Schema(description = "生成的图片信息")
    private GeneratedImageVO image;

    /**
     * 会话ID
     */
    @Schema(description = "会话ID")
    private String conversationId;

    /**
     * 消息索引
     */
    @Schema(description = "消息索引")
    private Integer messageIndex;

    /**
     * 变体索引
     */
    @Schema(description = "变体索引")
    private Integer variantIndex;

    /**
     * 模型名称
     */
    @Schema(description = "模型名称")
    private String modelName;

    /**
     * 错误信息（type=error 时使用）
     */
    @Schema(description = "错误信息")
    private String error;

    @Serial
    private static final long serialVersionUID = 1L;
}
