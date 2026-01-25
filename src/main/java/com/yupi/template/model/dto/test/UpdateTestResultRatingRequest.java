package com.yupi.template.model.dto.test;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.io.Serializable;

/**
 * 更新测试结果评分请求
 *
 * @author <a href="https://codefather.cn">编程导航学习圈</a>
 */
@Data
@Schema(description = "更新测试结果评分请求")
public class UpdateTestResultRatingRequest implements Serializable {

    /**
     * 测试结果ID
     */
    @Schema(description = "测试结果ID", required = true)
    private String resultId;

    /**
     * 用户评分(1-5)
     */
    @Schema(description = "用户评分", example = "5")
    private Integer userRating;

    private static final long serialVersionUID = 1L;
}
