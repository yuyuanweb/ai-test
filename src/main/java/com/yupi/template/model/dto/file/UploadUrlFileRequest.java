package com.yupi.template.model.dto.file;

import lombok.Data;

import java.io.Serializable;

/**
 * 文件上传请求
 *
 * @author https://github.com/liyupi
 */
@Data
public class UploadUrlFileRequest implements Serializable {

    /**
     * 图片地址
     */
    private String url;

    /**
     * 业务
     */
    private String biz;

    /**
     * 是否压缩
     */
    private boolean compress = false;

    private static final long serialVersionUID = 1L;
}