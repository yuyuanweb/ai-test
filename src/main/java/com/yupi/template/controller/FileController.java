package com.yupi.template.controller;

import cn.hutool.core.io.FileUtil;
import cn.hutool.core.lang.RegexPool;
import cn.hutool.core.util.RandomUtil;
import cn.hutool.core.util.ReUtil;
import com.yupi.template.common.BaseResponse;
import com.yupi.template.common.ResultUtils;
import com.yupi.template.exception.BusinessException;
import com.yupi.template.exception.ErrorCode;
import com.yupi.template.exception.ThrowUtils;
import com.yupi.template.model.dto.file.Base64UploadFileRequest;
import com.yupi.template.model.dto.file.UploadFileRequest;
import com.yupi.template.model.dto.file.UploadUrlFileRequest;
import com.yupi.template.model.entity.User;
import com.yupi.template.service.FileService;
import com.yupi.template.service.UserService;
import io.swagger.v3.oas.annotations.Operation;
import jakarta.annotation.Resource;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.RandomStringUtils;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.Collections;
import java.util.List;

/**
 * 文件接口
 *
 * @author <a href="https://codefather.cn">编程导航学习圈</a>
 */
@RestController
@RequestMapping("/file")
@Slf4j
public class FileController {

    @Resource
    private FileService fileService;
    @Resource
    private UserService userService;



    /**
     * 文件上传（form_data 传参）
     *
     * @param multipartFile multipart 文件
     * @param biz           业务类别
     * @return {@link BaseResponse}<{@link String}>
     */
    @PostMapping("/upload/form_data")
    public BaseResponse<String> uploadFileByFormData(@RequestPart("file") MultipartFile multipartFile, HttpServletRequest request) {
        fileService.validFile(multipartFile);
        User loginUser = userService.getLoginUser(request);
        // 临时文件上传路径
        String tmpFilePath = RandomStringUtils.randomAlphanumeric(8);
        // 转换文件类型
        File file;
        try {
            file = File.createTempFile(tmpFilePath, null);
            multipartFile.transferTo(file);
        } catch (Exception e) {
            log.error("文件上传失败", e);
            throw new BusinessException(ErrorCode.SYSTEM_ERROR, "文件上传失败");
        }
        return ResultUtils.success(fileService.uploadFile(file, multipartFile.getOriginalFilename(), loginUser));
    }

    /**
     * 文件上传（url params 传参）
     *
     * @param multipartFile     multipart 文件
     * @param uploadFileRequest 上传文件请求
     * @return {@link BaseResponse}<{@link String}>
     */
    @PostMapping("/upload")
    public BaseResponse<String> uploadFile(@RequestPart("file") MultipartFile multipartFile,
                                           UploadFileRequest uploadFileRequest, HttpServletRequest request) {
        ThrowUtils.throwIf(uploadFileRequest == null, ErrorCode.PARAMS_ERROR);
        User loginUser = userService.getLoginUser(request);
        fileService.validFile(multipartFile);
        // 临时文件上传路径
        String tmpFilePath = RandomStringUtils.randomAlphanumeric(8);
        // 转换文件类型
        File file;
        try {
            file = File.createTempFile(tmpFilePath, null);
            multipartFile.transferTo(file);
        } catch (Exception e) {
            log.error("文件上传失败", e);
            throw new BusinessException(ErrorCode.SYSTEM_ERROR, "文件上传失败");
        }
        String uuid = RandomStringUtils.randomAlphanumeric(8);
        String multipartFileName = multipartFile.getOriginalFilename();
        // 如果是中文，重命名文件
        if (ReUtil.contains(RegexPool.CHINESE, multipartFileName)) {
            String ext = FileUtil.extName(multipartFileName);
            multipartFileName = RandomUtil.randomString(10) + "." + ext;
        }
        String filename = uuid + "_" + multipartFileName;
        return ResultUtils.success(fileService.uploadFile(file, filename, loginUser));
    }

    /**
     * base64 格式的文件上传
     *
     * @param base64UploadFileRequest base64 格式的数据 和 biz
     * @return {@link BaseResponse}<{@link String}>
     */
    @PostMapping("/upload/base64")
    public BaseResponse<String> uploadFileByBase64(@RequestBody Base64UploadFileRequest base64UploadFileRequest, HttpServletRequest request) {
        User loginUser = userService.getLoginUser(request);

        return ResultUtils.success(fileService.uploadFileByBase64(base64UploadFileRequest, loginUser));
    }

    /**
     * url 外链上传
     *
     * @param uploadFileRequest
     * @param request
     * @return
     */
    @PostMapping("/url/upload")
    @Operation(summary = "图片链接上传", description = "仅支持图片")
    public BaseResponse<String> uploadFileFromImgUrl(@RequestBody UploadUrlFileRequest uploadFileRequest, HttpServletRequest request) {
        String biz = uploadFileRequest.getBiz();
        String urlStr = uploadFileRequest.getUrl();
        // 白名单过滤
        URL url;
        try {
            url = new URL(urlStr);
        } catch (MalformedURLException e) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "链接异常");
        }

        boolean isCompress = uploadFileRequest.isCompress();
        User loginUser = userService.getLoginUser(request);
        return ResultUtils.success(fileService.uploadFileFromImgUrl(urlStr, loginUser, isCompress));
    }
}

