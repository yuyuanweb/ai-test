package com.yupi.template.model.dto.file;

import com.yupi.template.utils.EncodeUtil;
import lombok.Data;

/**
 * @author pine
 */
@Data
public class WaterMarkParam {
    private final static String WATER_MARK = "AI模型测试";


    /**
     * 默认编码水印文本
     */
    private static final String DEFAULT_ENCODED_TEXT = EncodeUtil.safeBase64Encode(WATER_MARK);

    /**
     * 水印内容经过安全的 base64 编码
     */
    private String encodedText;

    /**
     * 水印字体，需要经过 URL 安全的 Base64 编码
     */
    private String encodedFont;

    /**
     * 水印文字字体大小，单位为磅，缺省值13
     */
    private String fontSize;

    /**
     * 字体颜色，缺省为灰色，需设置为十六进制 RGB 格式（例如 #FF0000），需经过 URL 安全的 Base64 编码
     */
    private String encodedColor;

    /**
     * 文字透明度，取值1 - 100，默认90（90%不透明度）
     */
    private String dissolve;

    /**
     * 文字水印位置，九宫格位置（参见九宫格方位图），默认值 SouthEas
     */
    private String gravity;

    /**
     * 水平（横轴）边距，单位为像素，缺省值为0
     */
    private String dx;

    /**
     * 垂直（纵轴）边距，单位为像素，默认值为0
     */
    private String dy;

    /**
     * 文字阴影效果，有效值为[0,100]，默认为0，表示无阴影
     */
    private String shadow;

    public WaterMarkParam() {
        this(1d);
    }

    public WaterMarkParam(Double rate) {
        this.encodedText = DEFAULT_ENCODED_TEXT;
        // 宋体
        this.encodedFont = "dGFob21hLnR0Zg";
        this.fontSize = String.valueOf(Math.round(72 * rate));
        // #ffffff
        this.encodedColor = "I2ZmZmZmZg";
        this.dissolve = "100";
        this.gravity = "southeast";
        this.dx = String.valueOf(Math.round(36 * rate));
        this.dy = String.valueOf(Math.round(36 * rate));
        this.shadow = "40";
    }
}
