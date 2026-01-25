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
 * 用户-模型使用统计实体
 *
 * @author <a href="https://codefather.cn">编程导航学习圈</a>
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Table("user_model_usage")
public class UserModelUsage implements Serializable {

    @Id
    @Column("id")
    private String id;

    @Column("userId")
    private Long userId;

    @Column("modelName")
    private String modelName;

    @Column("totalTokens")
    private Long totalTokens;

    @Column("totalCost")
    private BigDecimal totalCost;

    @Column("createTime")
    private LocalDateTime createTime;

    @Column("updateTime")
    private LocalDateTime updateTime;

    @Column(value = "isDelete", isLogicDelete = true)
    private Integer isDelete;

    private static final long serialVersionUID = 1L;
}
