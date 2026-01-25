package com.yupi.template.model.vo;

import lombok.Data;

import java.io.Serializable;
import java.util.List;

/**
 * 柱状图系列数据VO
 *
 * @author <a href="https://codefather.cn">编程导航学习圈</a>
 */
@Data
public class BarSeriesVO implements Serializable {

    /**
     * 系列名称（如：响应时间、Token消耗、成本等）
     */
    private String name;

    /**
     * 数据值列表（与categories对应）
     */
    private List<Double> data;

    /**
     * 单位（如：ms、tokens、USD等）
     */
    private String unit;

    private static final long serialVersionUID = 1L;
}
