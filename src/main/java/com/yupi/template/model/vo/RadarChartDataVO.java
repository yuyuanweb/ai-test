package com.yupi.template.model.vo;

import lombok.Data;

import java.io.Serializable;
import java.util.List;

/**
 * 雷达图数据VO
 *
 * @author <a href="https://codefather.cn">编程导航学习圈</a>
 */
@Data
public class RadarChartDataVO implements Serializable {

    /**
     * 维度名称列表（如：准确性、完整性、速度、成本效率等）
     */
    private List<String> dimensions;

    /**
     * 各模型的数据系列
     */
    private List<RadarSeriesVO> series;

    private static final long serialVersionUID = 1L;
}
