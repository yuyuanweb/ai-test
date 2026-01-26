package com.yupi.template.model.dto.prompt;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.io.Serializable;
import java.util.List;

/**
 * 提示词优化建议
 *
 * @author <a href="https://codefather.cn">编程导航学习圈</a>
 */
public record OptimizationSuggestion(
        List<String> issues,
        @JsonProperty("optimized_prompt")
        String optimizedPrompt,
        List<String> improvements
) implements Serializable {
}
