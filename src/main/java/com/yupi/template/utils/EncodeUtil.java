
package com.yupi.template.utils;

import cn.hutool.core.codec.Base64;

/**
 * @author pine
 */
public class EncodeUtil {
    /**
     * 在数据万象的处理操作中，有很多参数需要进行 URL 安全的 BASE64 编码，例如文字水印的文字内容、颜色、字体设置和图片水印的水印图链接。URL 安全的 BASE64 编码具体规则为：
     * 1. 将普通 BASE64 编码结果中的加号（+）替换成连接号（-）；
     * 2. 将编码结果中的正斜线（/）替换成下划线（_）；
     * 3. 将编码结果中的“=”去掉。
     *
     * @param source 源
     * @return {@link String}
     */
    public static String safeBase64Encode(String source){
        String encode = Base64.encode(source);
        return encode.replaceAll("\\+", "-")
                .replaceAll("/", "_")
                .replaceAll("=", "");
    }
}
