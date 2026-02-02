package com.yupi.template.utils;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.img.ImgUtil;
import cn.hutool.core.io.FileUtil;
import cn.hutool.core.text.CharSequenceUtil;
import cn.hutool.core.util.ReUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONUtil;
import com.qcloud.cos.COSClient;
import com.qcloud.cos.model.PutObjectRequest;
import com.qcloud.cos.model.PutObjectResult;
import com.qcloud.cos.model.ciModel.common.ImageProcessRequest;
import com.qcloud.cos.model.ciModel.persistence.CIUploadResult;
import com.qcloud.cos.model.ciModel.persistence.PicOperations;
import com.yupi.template.config.TencentCosConfig;
import com.yupi.template.exception.BusinessException;
import com.yupi.template.exception.ErrorCode;
import com.yupi.template.model.dto.file.WaterMarkParam;
import jakarta.annotation.Resource;
import jakarta.validation.constraints.NotNull;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.RandomStringUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Component;

import java.awt.image.BufferedImage;
import java.io.File;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;

/**
 * 操作 Tencent Cos 对象存储
 *
 * @author yupi
 */
@Slf4j
@Component
public class TencentCosUtil {

    @Resource
    private TencentCosConfig tencentCosConfig;

    @Resource
    private COSClient cosClient;
    /**
     * 1kb
     */
    final long ONE_K = 1024L;

    /**
     * 支持生成缩略图的格式
     */
    private final static HashSet<String> SUPPORT_THUMBNAIL_EXT = new HashSet<String>() {{
        add("jpg");
        add("jpeg");
        add("png");
        add("bmp");
        add("webp");
        add("tiff");
        add("gif");
    }};

    public PutObjectResult putObject(String key, String localFilePath) {
        PutObjectRequest putObjectRequest = new PutObjectRequest(tencentCosConfig.getBucket(), key,
                new File(localFilePath));
        return cosClient.putObject(putObjectRequest);
    }

    public void deleteObject(String key) {
        cosClient.deleteObject(tencentCosConfig.getBucket(), key);
    }

    public PutObjectResult putObject(String key, File file) {
        PutObjectRequest putObjectRequest = new PutObjectRequest(tencentCosConfig.getBucket(), key,
                file);
        return cosClient.putObject(putObjectRequest);
    }


    /**
     * 构造缩略图的处理参数
     */
    private @NotNull PicOperations getThumbnailPicOperations(String thumbnailKey, int width, int height) {
        PicOperations picOperations = new PicOperations();
        picOperations.setIsPicInfo(1);

        List<PicOperations.Rule> ruleList = new LinkedList<>();
        PicOperations.Rule rule1 = new PicOperations.Rule();
        rule1.setBucket(tencentCosConfig.getBucket());
        rule1.setFileId(thumbnailKey);
        // 转成缩略图
        // /thumbnail/<Width>x<Height>!
        rule1.setRule(String.format("imageMogr2/thumbnail/%sx%s!", width, height));
        ruleList.add(rule1);
        picOperations.setRules(ruleList);
        return picOperations;
    }

    /**
     * 对云上数据进行图片处理水印
     *
     * @param key            key
     * @param waterMarkParam 水印参数
     * @return {@link String}
     */
    public String putObjectWithWaterMarkOnProcessImage(String key, WaterMarkParam waterMarkParam) {
        if (StrUtil.isBlank(key)) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "文件的 key 不能为空");
        }
        key = key.replace("https://pic.code-nav.cn", "");
        ImageProcessRequest imageReq = new ImageProcessRequest(tencentCosConfig.getBucket(), key);


        String ruleStr = "watermark/2/text/{encodedText}/font/{encodedFont}/fontsize/{fontSize}/fill/{encodedColor}/dissolve/{dissolve}/gravity/{gravity}/dx/{dx}/dy/{dy}/shadow/{shadow}";
        String formatRule = ruleStr.replace("{encodedText}", waterMarkParam.getEncodedText())
                .replace("{encodedFont}", waterMarkParam.getEncodedFont())
                .replace("{fontSize}", waterMarkParam.getFontSize())
                .replace("{encodedColor}", waterMarkParam.getEncodedColor())
                .replace("{dissolve}", waterMarkParam.getDissolve())
                .replace("{gravity}", waterMarkParam.getGravity())
                .replace("{dx}", waterMarkParam.getDx())
                .replace("{dy}", waterMarkParam.getDy())
                .replace("{shadow}", waterMarkParam.getShadow());

        String extName = FileUtil.extName(key);
        key = key.replace("." + extName, "_mianshiya." + extName);
        key = replace(key);
        List<PicOperations.Rule> ruleList = new LinkedList<>();
        PicOperations.Rule rule = new PicOperations.Rule();
        rule.setBucket(tencentCosConfig.getBucket());
        rule.setFileId(key);
        rule.setRule(formatRule);
        ruleList.add(rule);
        PicOperations picOperations = new PicOperations();
        picOperations.setIsPicInfo(0);
        picOperations.setRules(ruleList);
        imageReq.setPicOperations(picOperations);
        CIUploadResult result = cosClient.processImage(imageReq);
        log.info("result {}", JSONUtil.toJsonStr(result));

        return key;
    }


    /**
     * 上传图片到腾讯云对象存储<br>
     * 当图片参数<200*200时不加水印，避免影响图片清晰度<br>
     * 否则加水印<br>
     *
     * @param key  key
     * @param file 文件
     * @return {@link String}
     */
    public String uploadImage2Cos(String key, File file) {
        WaterMarkParam waterMarkParam = new WaterMarkParam(0.3d);
        BufferedImage bufferedImage = ImgUtil.read(file);
        if (bufferedImage.getHeight() < 200 || bufferedImage.getWidth() < 200) {
            log.info("图片宽高[{},{}]，不加水印", bufferedImage.getWidth(), bufferedImage.getHeight());
            putObject(key, file);
            return key;
        } else {
            log.info("图片宽高[{},{}]，加水印", bufferedImage.getWidth(), bufferedImage.getHeight());
            return putObjectWithWaterMark(key, file, waterMarkParam);
        }

    }


    /**
     * 上传带水印的图片
     *      todo 可以调研在本地加水印然后上传，降低成本
     *
     * @param key            key
     * @param file           文件
     * @param waterMarkParam 水印参数
     * @return {@link String}
     */
    public String putObjectWithWaterMark(String key, File file, WaterMarkParam waterMarkParam) {
        if (StrUtil.isBlank(key)) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "文件的 key 不能为空");
        }
        key = StrUtil.prependIfMissing(key, "/");
        PutObjectRequest putObjectRequest = new PutObjectRequest(tencentCosConfig.getBucket(), key,
                file);


        String extName = FileUtil.extName(key);
        key = key.replace("." + extName, "_mianshiya." + extName);
        List<PicOperations.Rule> ruleList = new LinkedList<>();
        PicOperations.Rule rule = new PicOperations.Rule();
        rule.setBucket(tencentCosConfig.getBucket());
        rule.setFileId(key);
        rule.setRule(getWaterMarkRuleStr(waterMarkParam));
        ruleList.add(rule);
        PicOperations picOperations = new PicOperations();
        picOperations.setIsPicInfo(0);
        picOperations.setRules(ruleList);
        putObjectRequest.setPicOperations(picOperations);
        cosClient.putObject(putObjectRequest);

        return key;
    }

    private static String replace(String url) {
        String ch = "[\u2E80-\u2EFF\u2F00-\u2FDF\u31C0-\u31EF\u3400-\u4DBF\u4E00-\u9FFF\uF900-\uFAFF\uD840\uDC00-\uD869\uDEDF\uD869\uDF00-\uD86D\uDF3F\uD86D\uDF40-\uD86E\uDC1F\uD86E\uDC20-\uD873\uDEAF\uD87E\uDC00-\uD87E\uDE1F]+";
        String uuid = RandomStringUtils.randomAlphanumeric(8);
        List<String> list = ReUtil.findAll(ch, url, 0);
        if (CollUtil.isNotEmpty(list)) {
            for (String c : list) {
                url = url.replace(c, uuid);
            }
        }
        return url;
    }

    public String putObject(String key, File file, boolean compress, boolean withWaterMark) {
        PutObjectRequest putObjectRequest = new PutObjectRequest(tencentCosConfig.getBucket(), key,
                file);
        String ext = CharSequenceUtil.subAfter(key, ".", true);
        String ruleStr = "";

        if (withWaterMark) {
            double rate = .3d;
            if (file != null && file.length() < 100 * ONE_K) {
                rate = .15d;
            }
            WaterMarkParam waterMarkParam = new WaterMarkParam(rate);
            ruleStr += this.getWaterMarkRuleStr(waterMarkParam);
            String extName = FileUtil.extName(key);
            key = key.replace("." + extName, "_mianshiya." + extName);
        }

        // 压缩逻辑，转成 webp 格式
        if (compress && SUPPORT_THUMBNAIL_EXT.contains(ext)) {
            ruleStr += "|" + "imageMogr2/format/webp";

        }
        if (StringUtils.isNotBlank(ruleStr)) {
            // 规则不为空时执行图片操作
            PicOperations picOperations = new PicOperations();
            List<PicOperations.Rule> ruleList = new LinkedList<>();
            PicOperations.Rule rule = new PicOperations.Rule();
            rule.setBucket(tencentCosConfig.getBucket());
            rule.setFileId(key);
            rule.setRule(ruleStr);
            ruleList.add(rule);
            picOperations.setRules(ruleList);
            putObjectRequest.setPicOperations(picOperations);
        }
        cosClient.putObject(putObjectRequest);
        return key;
    }

    /**
     * 获取水印规则
     *
     * @param waterMarkParam 水印参数
     * @return {@link String }
     */
    private String getWaterMarkRuleStr(WaterMarkParam waterMarkParam) {
        String ruleStr = "watermark/2/text/{encodedText}/font/{encodedFont}/fontsize/{fontSize}/fill/{encodedColor}/dissolve/{dissolve}/gravity/{gravity}/dx/{dx}/dy/{dy}/shadow/{shadow}";
        return ruleStr.replace("{encodedText}", waterMarkParam.getEncodedText())
                .replace("{encodedFont}", waterMarkParam.getEncodedFont())
                .replace("{fontSize}", waterMarkParam.getFontSize())
                .replace("{encodedColor}", waterMarkParam.getEncodedColor())
                .replace("{dissolve}", waterMarkParam.getDissolve())
                .replace("{gravity}", waterMarkParam.getGravity())
                .replace("{dx}", waterMarkParam.getDx())
                .replace("{dy}", waterMarkParam.getDy())
                .replace("{shadow}", waterMarkParam.getShadow());
    }


}
