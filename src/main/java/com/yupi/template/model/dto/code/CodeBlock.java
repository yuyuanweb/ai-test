package com.yupi.template.model.dto.code;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

/**
 * 代码块 DTO
 *
 * @author <a href="https://codefather.cn">编程导航学习圈</a>
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "代码块")
public class CodeBlock implements Serializable {

    /**
     * 代码语言
     */
    @Schema(description = "代码语言")
    private String language;

    /**
     * 代码内容
     */
    @Schema(description = "代码内容")
    private String code;

    /**
     * 起始位置
     */
    @Schema(description = "起始位置")
    private Integer startIndex;

    /**
     * 结束位置
     */
    @Schema(description = "结束位置")
    private Integer endIndex;

    /**
     * 清理后的HTML（仅HTML代码块有值）
     */
    @Schema(description = "清理后的HTML")
    private String sanitizedHtml;

    private static final long serialVersionUID = 1L;
}

