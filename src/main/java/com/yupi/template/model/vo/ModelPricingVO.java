package com.yupi.template.model.vo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.math.BigDecimal;

/**
 * 模型价格视图对象，用于缓存及成本计算
 *
 * @author <a href="https://codefather.cn">编程导航学习圈</a>
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ModelPricingVO implements Serializable {

    private BigDecimal inputPrice;

    private BigDecimal outputPrice;

    private static final long serialVersionUID = 1L;
}
