package com.yupi.template.service.impl;

import cn.hutool.core.codec.Base64;
import cn.hutool.core.io.FileUtil;
import cn.hutool.core.text.CharSequenceUtil;
import cn.hutool.core.util.ObjectUtil;
import cn.hutool.core.util.RandomUtil;
import cn.hutool.core.util.URLUtil;
import cn.hutool.http.HttpResponse;
import cn.hutool.http.HttpUtil;
import cn.hutool.http.Method;
import com.yupi.template.exception.BusinessException;
import com.yupi.template.exception.ErrorCode;
import com.yupi.template.exception.ThrowUtils;
import com.yupi.template.model.dto.file.Base64UploadFileRequest;
import com.yupi.template.model.entity.User;
import com.yupi.template.service.FileService;
import com.yupi.template.utils.TencentCosUtil;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.Arrays;
import java.util.HashMap;

/**
 * 文件上传服务实现类
 *
 * @author <a href="https://codefather.cn">编程导航学习圈</a>
 */
@Service
@Slf4j
public class FileServiceImpl implements FileService {

    @Resource
    private TencentCosUtil tencentCosManager;

    private static final String COS_HOST = "https://yupi-1300582479.cos.ap-guangzhou.myqcloud.com";

    private static final long ONE_K = 1024L;

    private static final long ONE_M = 1024 * 1024L;

    private static final long MAX_FILE_SIZE = 5 * ONE_M;

    private static final long MAX_IMAGE_SIZE = 10 * ONE_M;

    /**
     * content-type 对应后缀
     */
    private final static HashMap<String, String> CONTENT_TYPE_TO_SUFFIX = new HashMap<String, String>() {{
        put("image/jpeg", ".jpg");
        put("image/png", ".png");
        put("image/gif", ".gif");
        put("image/bmp", ".bmp");
        put("image/x-icon", ".ico");
        put("image/tiff", ".tif");
//        put("image/svg+xml", ".svg");
        put("image/webp", ".webp");
    }};

    /**
     * 上传文件
     *
     * @param file
     * @param loginUser
     * @return
     */
    @Override
    public String uploadFile(File file,
                             String fileName,
                             User loginUser) {
        // 校验文件信息
        ThrowUtils.throwIf(ObjectUtil.hasEmpty(file, fileName), ErrorCode.PARAMS_ERROR);
        // 文件目录：根据业务、用户来划分
        String filepath = String.format("/aitest/%s/%s", loginUser.getId(), fileName);
        try {
            filepath = this.handleCompressAndUpload(false, false, file, filepath);
            // 返回可访问地址
            return filepath;
        } catch (Exception e) {
            log.error("文件上传失败", e);
            throw new BusinessException(ErrorCode.SYSTEM_ERROR, "上传失败");
        } finally {
            boolean delete = file.delete();
            if (!delete) {
                log.error("file delete error, filepath = {}", filepath);
            }
        }
    }

    /**
     * 上传 Base64 文件
     *
     * @param base64UploadFileRequest
     * @param loginUser
     * @return
     */
    @Override
    public String uploadFileByBase64(Base64UploadFileRequest base64UploadFileRequest, User loginUser) {
        String fileBase64 = base64UploadFileRequest.getFileBase64();
        String uuid = RandomUtil.randomString(15);
        String filename = uuid + ".webp";
        // todo 替换项目名
        String filepath = String.format("/project_name/%s/%s", loginUser.getId(), filename);
        File file = null;
        FileOutputStream fos = null;
        try {
            file = File.createTempFile(filepath, null);
            byte[] byteData = Base64.decode(fileBase64);
            final long ONE_M = 1024 * 1024L;
            if (byteData.length > 5 * ONE_M) {
                throw new BusinessException(ErrorCode.PARAMS_ERROR, "文件大小不能超过 5M");
            }
            fos = new FileOutputStream(file);
            fos.write(byteData, 0, byteData.length);
            fos.flush();

            tencentCosManager.putObject(filepath, file);

            // 返回可访问地址
            return COS_HOST + filepath;
        } catch (IOException e) {
            log.error("文件上传失败", e);
            throw new BusinessException(ErrorCode.SYSTEM_ERROR, "文件上传失败");
        } finally {
            if (file != null) {
                boolean delete = file.delete();
                if (!delete) {
                    log.error("file delete error, filepath = {}", filepath);
                }
            }
            if (fos != null) {
                try {
                    fos.close();
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
            }
        }
    }

    /**
     * 校验文件
     *
     * @param multipartFile     multipart 文件
     * @param fileUploadBizEnum 文件上传业务类型枚举
     */
    @Override
    public void validFile(MultipartFile multipartFile) {
        // 文件大小
        long fileSize = multipartFile.getSize();
        // 文件后缀
        String fileSuffix = FileUtil.getSuffix(multipartFile.getOriginalFilename());
        if (fileSize > MAX_FILE_SIZE) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "文件大小不能超过 5M");
        }
        if (!Arrays.asList("jpeg", "jpg", "png", "webp").contains(fileSuffix)) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "文件类型错误");
        }
    }

    @Override
    public void validImageFile(MultipartFile multipartFile) {
        ThrowUtils.throwIf(multipartFile == null, ErrorCode.PARAMS_ERROR);
        long fileSize = multipartFile.getSize();
        if (fileSize > MAX_IMAGE_SIZE) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "图片大小不能超过 10M");
        }

        String fileSuffix = FileUtil.getSuffix(multipartFile.getOriginalFilename());
        if (fileSuffix != null) {
            fileSuffix = fileSuffix.toLowerCase();
        }
        if (Arrays.asList("jpeg", "jpg", "png", "gif", "webp").contains(fileSuffix)) {
            return;
        }

        String contentType = multipartFile.getContentType();
        if (contentType != null) {
            String normalizedContentType = contentType.toLowerCase();
            if (CONTENT_TYPE_TO_SUFFIX.containsKey(normalizedContentType)) {
                return;
            }
        }
        throw new BusinessException(ErrorCode.PARAMS_ERROR, "图片类型错误");
    }

    @Override
    public String uploadFileFromImgUrl(String url, User loginUser, boolean isCompress) {

        HttpResponse response = null;
        try {
            response = HttpUtil.createGet(url).method(Method.HEAD).timeout(2000).execute();
        } catch (Exception e) {
            log.error("图片下载失败", e);
            throw new BusinessException(ErrorCode.SYSTEM_ERROR, "图片下载失败");
        }
        validUrlFile(response);
        String contentType = response.header("Content-Type").toLowerCase();
        String extension = CONTENT_TYPE_TO_SUFFIX.get(contentType);
        String uuid = RandomUtil.randomString(16);
        String filename = uuid + extension;
        String filepath = String.format("/aitest/%s/%s", loginUser.getId(), filename);

        File file = null;
        try {
            file = File.createTempFile(filepath, null);
            HttpUtil.downloadFile(url, file, 3000);
            filepath = this.handleCompressAndUpload(isCompress, false, file, filepath);
            // 返回可访问地址
            return filepath;
        } catch (Exception e) {
            log.error("上传失败", e);
            throw new BusinessException(ErrorCode.SYSTEM_ERROR, "图片上传失败");
        } finally {
            if (file != null) {
                boolean delete = file.delete();
                if (!delete) {
                    log.error("file delete error, filepath = {}", filepath);
                }
            }
        }
    }

    @Override
    public String uploadImage(File file, String fileName, User loginUser) {
        ThrowUtils.throwIf(ObjectUtil.hasEmpty(file, fileName, loginUser), ErrorCode.PARAMS_ERROR);
        String filepath = String.format("/aitest/%s/images/%s", loginUser.getId(), fileName);
        try {
            filepath = this.handleCompressAndUpload(false, false, file, filepath);
            return filepath;
        } catch (Exception e) {
            log.error("图片上传失败", e);
            throw new BusinessException(ErrorCode.SYSTEM_ERROR, "图片上传失败");
        } finally {
            boolean delete = file.delete();
            if (!delete) {
                log.error("file delete error, filepath = {}", filepath);
            }
        }
    }

    /**
     * 处理压缩参数并上传文件
     *
     * @param compress      是否压缩
     * @param withWaterMark 是否携带水印
     * @param file          文件
     * @param filepath      文件路径
     * @return 文件地址
     */
    @Override
    public String handleCompressAndUpload(boolean compress, boolean withWaterMark, File file, String filepath) {
        try {
            // 20kb 以下压缩效果较差，不压缩
            if (compress && file != null && file.length() > 20 * ONE_K) {
                filepath = URLUtil.encode(CharSequenceUtil.subBefore(filepath, ".", true) + ".webp");
                filepath = tencentCosManager.putObject(filepath, file, true, withWaterMark);
            } else {
                filepath = tencentCosManager.putObject(filepath, file, false, withWaterMark);
            }
            return COS_HOST + filepath;
        } catch (Exception e) {
            log.error("文件上传失败", e);
            throw new BusinessException(ErrorCode.SYSTEM_ERROR, "压缩处理失败");
        }
    }


    /**
     * 校验 URL
     *
     * @param response url head 信息
     */
    private void validUrlFile(HttpResponse response) {
        if (response.getStatus() != 200) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "文件下载失败");
        }

        // 只允许下载图片
        String contentType = response.header("Content-Type");
        ThrowUtils.throwIf(!CONTENT_TYPE_TO_SUFFIX.containsKey(contentType.toLowerCase()),
                ErrorCode.PARAMS_ERROR, "文件类型错误");

        // 限制文件大小
        long contentLength = Long.parseLong(response.header("Content-Length"));
        final long FIVE_M = 5 * 1024 * 1024L;

        ThrowUtils.throwIf(contentLength > FIVE_M, ErrorCode.PARAMS_ERROR, "图片大小不能超过 5M");
    }
}
