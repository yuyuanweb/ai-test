package com.yupi.template.model.dto.test;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;

/**
 * 删除批量测试任务请求
 *
 * @author <a href="https://codefather.cn">编程导航学习圈</a>
 */
@Data
public class DeleteTaskRequest implements Serializable {

    private String id;

    @Serial
    private static final long serialVersionUID = 1L;
}
