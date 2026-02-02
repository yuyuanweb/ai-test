package com.yupi.template.model.dto.conversation;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;

/**
 * 删除对话请求
 *
 * <a href="https://codefather.cn">编程导航学习圈</a>
 */
@Data
public class DeleteConversationRequest implements Serializable {

    /**
     * 对话ID（UUID字符串）
     */
    private String id;

    @Serial
    private static final long serialVersionUID = 1L;
}
