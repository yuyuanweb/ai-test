package com.yupi.template.model.dto.test;

import com.yupi.template.common.PageRequest;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.io.Serializable;

/**
 * 任务查询请求
 *
 * @author <a href="https://codefather.cn">编程导航学习圈</a>
 */
@Data
@EqualsAndHashCode(callSuper = true)
@Schema(description = "任务查询请求")
public class TaskQueryRequest extends PageRequest implements Serializable {

    /**
     * 状态筛选
     */
    @Schema(description = "状态")
    private String status;

    /**
     * 关键词搜索（任务名称）
     */
    @Schema(description = "关键词")
    private String keyword;

    /**
     * 分类筛选
     */
    @Schema(description = "分类")
    private String category;

    /**
     * 开始时间
     */
    @Schema(description = "开始时间")
    private String startTime;

    /**
     * 结束时间
     */
    @Schema(description = "结束时间")
    private String endTime;

    private static final long serialVersionUID = 1L;
}
