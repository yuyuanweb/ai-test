package com.yupi.template.model.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.util.Map;

/**
 * Battle模式模型映射关系VO
 *
 * @author <a href="https://codefather.cn">编程导航学习圈</a>
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Battle模式模型映射关系")
public class BattleModelMappingVO implements Serializable {

    /**
     * 匿名标识到真实模型名称的映射
     * 例如：{"模型A": "openai/gpt-4o", "模型B": "anthropic/claude-3.5-sonnet"}
     */
    @Schema(description = "匿名标识到真实模型名称的映射")
    private Map<String, String> mapping;

    private static final long serialVersionUID = 1L;
}
