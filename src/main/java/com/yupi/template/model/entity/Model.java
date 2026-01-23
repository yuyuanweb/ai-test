package com.yupi.template.model.entity;

import com.mybatisflex.annotation.Column;
import com.mybatisflex.annotation.Id;
import com.mybatisflex.annotation.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 模型信息实体
 *
 * @author <a href="https://codefather.cn">编程导航学习圈</a>
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Table("model")
public class Model implements Serializable {

    /**
     * 模型ID（OpenRouter格式）
     */
    @Id
    @Column("id")
    private String id;

    /**
     * 模型显示名称
     */
    @Column("name")
    private String name;

    /**
     * 模型描述
     */
    @Column("description")
    private String description;

    /**
     * 提供商
     */
    @Column("provider")
    private String provider;

    /**
     * 上下文长度
     */
    @Column("contextLength")
    private Integer contextLength;

    /**
     * 输入价格（每百万tokens，美元）
     */
    @Column("inputPrice")
    private BigDecimal inputPrice;

    /**
     * 输出价格（每百万tokens，美元）
     */
    @Column("outputPrice")
    private BigDecimal outputPrice;

    /**
     * 是否推荐
     */
    @Column("recommended")
    private Integer recommended;

    /**
     * 是否国内模型
     */
    @Column("isChina")
    private Integer isChina;

    /**
     * 标签（JSON数组字符串）
     */
    @Column("tags")
    private String tags;

    /**
     * OpenRouter原始数据（JSON）
     */
    @Column("rawData")
    private String rawData;

    /**
     * 创建时间
     */
    @Column("createTime")
    private LocalDateTime createTime;

    /**
     * 更新时间
     */
    @Column("updateTime")
    private LocalDateTime updateTime;

    /**
     * 逻辑删除
     */
    @Column("isDelete")
    private Integer isDelete;

    private static final long serialVersionUID = 1L;
}

