package com.yupi.template.model.dto.image;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serial;
import java.io.Serializable;
import java.util.List;

/**
 * OpenRouter 图像生成请求体
 *
 * @author <a href="https://codefather.cn">编程导航学习圈</a>
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OpenRouterImageRequestBody implements Serializable {

    /**
     * 模型名称
     */
    private String model;

    /**
     * 输出模态（text, image）
     */
    private List<String> modalities;

    /**
     * 是否流式输出
     */
    private Boolean stream;

    /**
     * 消息列表
     */
    private List<Message> messages;

    /**
     * 生成图片数量
     */
    private Integer n;

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 消息对象
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Message implements Serializable {
        
        /**
         * 角色（user/assistant/system）
         */
        private String role;

        /**
         * 消息内容（可以是字符串或内容列表）
         */
        private Object content;

        @Serial
        private static final long serialVersionUID = 1L;
    }

    /**
     * 多模态内容项
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ContentItem implements Serializable {
        
        /**
         * 内容类型（text/image_url）
         */
        private String type;

        /**
         * 文本内容（type=text 时使用）
         */
        private String text;

        /**
         * 图片URL（type=image_url 时使用）
         */
        private ImageUrl image_url;

        @Serial
        private static final long serialVersionUID = 1L;
    }

    /**
     * 图片URL对象
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ImageUrl implements Serializable {
        
        /**
         * 图片URL
         */
        private String url;

        @Serial
        private static final long serialVersionUID = 1L;
    }
}
