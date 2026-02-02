package com.yupi.template.controller;

import cn.hutool.core.io.FileUtil;
import cn.hutool.core.util.RandomUtil;
import com.yupi.template.common.BaseResponse;
import com.yupi.template.common.ResultUtils;
import com.yupi.template.exception.BusinessException;
import com.yupi.template.exception.ErrorCode;
import com.yupi.template.model.entity.User;
import com.yupi.template.model.vo.UploadImageVO;
import com.yupi.template.service.FileService;
import com.yupi.template.service.UserService;
import jakarta.annotation.Resource;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;

/**
 * 上传接口
 *
 * @author <a href="https://codefather.cn">编程导航学习圈</a>
 */
@RestController
@RequestMapping("/upload")
@Slf4j
public class UploadController {

    @Resource
    private FileService fileService;

    @Resource
    private UserService userService;

    /**
     * 图片上传（用于多模态输入）
     */
    @PostMapping("/image")
    public BaseResponse<UploadImageVO> uploadImage(@RequestPart("file") MultipartFile multipartFile,
                                                   HttpServletRequest request) {
        User loginUser = userService.getLoginUser(request);
        fileService.validImageFile(multipartFile);

        String tmpFilePath = RandomUtil.randomString(8);
        File file;
        try {
            file = File.createTempFile(tmpFilePath, null);
            multipartFile.transferTo(file);
        } catch (Exception e) {
            log.error("图片上传失败", e);
            throw new BusinessException(ErrorCode.SYSTEM_ERROR, "图片上传失败");
        }

        String originalFilename = multipartFile.getOriginalFilename();
        String suffix = FileUtil.getSuffix(originalFilename);
        if (suffix == null || suffix.trim().isEmpty()) {
            String contentType = multipartFile.getContentType();
            if (contentType != null) {
                if ("image/jpeg".equalsIgnoreCase(contentType)) {
                    suffix = "jpg";
                } else if ("image/png".equalsIgnoreCase(contentType)) {
                    suffix = "png";
                } else if ("image/gif".equalsIgnoreCase(contentType)) {
                    suffix = "gif";
                } else if ("image/webp".equalsIgnoreCase(contentType)) {
                    suffix = "webp";
                }
            }
        }
        if (suffix == null || suffix.trim().isEmpty()) {
            suffix = "jpg";
        }

        String fileName = RandomUtil.randomString(16) + "." + suffix.toLowerCase();
        String url = fileService.uploadImage(file, fileName, loginUser);

        UploadImageVO uploadImageVO = UploadImageVO.builder()
                .url(url)
                .originalFilename(originalFilename)
                .size(multipartFile.getSize())
                .contentType(multipartFile.getContentType())
                .build();
        return ResultUtils.success(uploadImageVO);
    }
}

