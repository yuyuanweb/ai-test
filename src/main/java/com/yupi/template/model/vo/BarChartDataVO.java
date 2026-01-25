package com.yupi.template.model.vo;

import lombok.Data;

import java.io.Serializable;
import java.util.List;

/**
 * 柱状图数据VO
 *
 * @author <a href="https://codefather.cn">编程导航学习圈</a>
 */
@Data
public class BarChartDataVO implements Serializable {

    /**
     * X轴标签（模型名称列表）
     */
    private List<String> categories;

    /**
     * 数据系列列表
     */
    private List<BarSeriesVO> series;

    private static final long serialVersionUID = 1L;
}
