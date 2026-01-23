package com.yupi.template.controller;

import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultHandlers.print;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * 模型接口测试
 *
 * @author <a href="https://codefather.cn">编程导航学习圈</a>
 */
@SpringBootTest
@AutoConfigureMockMvc
@Slf4j
class ModelControllerTest {

    @Autowired
    private MockMvc mockMvc;

    /**
     * 测试分页查询模型列表
     */
    @Test
    void testListModels() throws Exception {
        String requestBody = """
                {
                    "pageNum": 1,
                    "pageSize": 10
                }
                """;

        mockMvc.perform(post("/model/list")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody))
                .andDo(print())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data").exists())
                .andExpect(jsonPath("$.data.records").isArray())
                .andExpect(jsonPath("$.data.totalRow").exists());

        log.info("分页查询模型列表测试通过");
    }

    /**
     * 测试获取所有模型
     */
    @Test
    void testGetAllModels() throws Exception {
        mockMvc.perform(get("/model/all"))
                .andDo(print())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data").isArray());

        log.info("获取所有模型测试通过");
    }
}

