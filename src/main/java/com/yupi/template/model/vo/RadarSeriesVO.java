package com.yupi.template.model.vo;

import lombok.Data;

import java.io.Serializable;
import java.util.List;

/**
 * 雷达图系列数据VO
 *
 * @author <a href="https://codefather.cn">编程导航学习圈</a>
 */
@Data
public class RadarSeriesVO implements Serializable {

    /**
     * 模型名称
     */
    private String modelName;

    /**
     * 各维度的数值（与dimensions对应）
     */
    private List<Double> values;

    private static final long serialVersionUID = 1L;
}
