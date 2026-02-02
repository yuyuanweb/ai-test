package com.yupi.template.model.vo;

import lombok.Builder;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;

/**
 * 图片上传响应
 *
 * @author <a href="https://codefather.cn">编程导航学习圈</a>
 */
@Data
@Builder
public class UploadImageVO implements Serializable {

    /**
     * 可访问 URL
     */
    private String url;

    /**
     * 原始文件名
     */
    private String originalFilename;

    /**
     * 文件大小（字节）
     */
    private Long size;

    /**
     * Content-Type
     */
    private String contentType;

    @Serial
    private static final long serialVersionUID = 1L;
}

