package com.yupi.template.service;


import com.yupi.template.model.dto.file.Base64UploadFileRequest;
import com.yupi.template.model.entity.User;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;

/**
 * 文件上传服务
 *
 * @author <a href="https://codefather.cn">编程导航学习圈</a>
 */
public interface FileService {

    /**
     * 上传文件
     *
     * @param file
     * @param fileName
     * @param fileUploadBizEnum
     * @param loginUser
     * @return
     */
    String uploadFile(File file,
                      String fileName,
                      User loginUser);

    /**
     * 上传 Base64 文件
     *
     * @param base64UploadFileRequest
     * @param loginUser
     * @return
     */

    String uploadFileByBase64(Base64UploadFileRequest base64UploadFileRequest, User loginUser);


    void validFile(MultipartFile multipartFile);

    /**
     * 校验图片文件（用于多模态输入）
     *
     * @param multipartFile multipart 文件
     */
    void validImageFile(MultipartFile multipartFile);

    /**
     * 上传 url图片
     */
    String uploadFileFromImgUrl(String url, User loginUser, boolean isCompress);

    /**
     * 上传图片文件（用于多模态输入）
     *
     * @param file     临时文件
     * @param fileName 文件名
     * @param loginUser 登录用户
     * @return 可访问 URL
     */
    String uploadImage(File file, String fileName, User loginUser);

    /**
     * 处理压缩参数并上传文件
     *
     * @param compress      是否压缩
     * @param withWaterMark 是否携带水印
     * @param file          文件
     * @param filepath      文件路径
     * @return 文件地址
     */
    String handleCompressAndUpload(boolean compress, boolean withWaterMark, File file, String filepath);
}
