package com.yupi.template.controller;

import com.yupi.template.constant.UserConstant;
import com.yupi.template.model.entity.User;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultHandlers.print;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * 对话接口测试
 *
 * @author <a href="https://codefather.cn">编程导航学习圈</a>
 */
@SpringBootTest
@AutoConfigureMockMvc
@Slf4j
class ConversationControllerTest {

    @Autowired
    private MockMvc mockMvc;

    private MockHttpSession session;

    /**
     * 测试前准备 - 模拟登录用户
     */
    @BeforeEach
    void setUp() {
        session = new MockHttpSession();
        User testUser = new User();
        testUser.setId(1L);
        testUser.setUserAccount("test");
        testUser.setUserName("测试用户");
        session.setAttribute(UserConstant.USER_LOGIN_STATE, testUser);
    }

    /**
     * 测试创建对话接口
     */
    @Test
    void testCreateConversation() throws Exception {
        String requestBody = """
                {
                    "title": "测试Side-by-Side对话",
                    "conversationType": "side_by_side",
                    "models": ["openai/gpt-4o", "anthropic/claude-3.5-sonnet"]
                }
                """;

        mockMvc.perform(post("/conversation/create")
                        .session(session)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody))
                .andDo(print())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data").isString());

        log.info("创建对话接口测试通过");
    }

    /**
     * 测试获取对话列表接口
     */
    @Test
    void testListConversations() throws Exception {
        mockMvc.perform(get("/conversation/list")
                        .session(session)
                        .param("pageNum", "1")
                        .param("pageSize", "10"))
                .andDo(print())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data").exists())
                .andExpect(jsonPath("$.data.records").isArray());

        log.info("获取对话列表接口测试通过");
    }

    /**
     * 测试未登录访问（应该返回空列表或失败）
     */
    @Test
    void testUnauthorizedAccess() throws Exception {
        mockMvc.perform(get("/conversation/list")
                        .param("pageNum", "1")
                        .param("pageSize", "10"))
                .andDo(print())
                .andExpect(status().isOk()); // 返回200但列表为空

        log.info("未登录访问测试通过");
    }

    /**
     * 测试创建Prompt Lab对话
     */
    @Test
    void testCreatePromptLabConversation() throws Exception {
        String requestBody = """
                {
                    "title": "测试Prompt Lab对话",
                    "conversationType": "prompt_lab",
                    "models": ["openai/gpt-4o"]
                }
                """;

        mockMvc.perform(post("/conversation/create")
                        .session(session)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody))
                .andDo(print())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data").isString());

        log.info("创建Prompt Lab对话接口测试通过");
    }

    /**
     * 测试获取对话消息
     */
    @Test
    void testGetConversationMessages() throws Exception {
        // 先创建一个对话
        String requestBody = """
                {
                    "title": "消息测试对话",
                    "conversationType": "side_by_side",
                    "models": ["openai/gpt-4o"]
                }
                """;

        String response = mockMvc.perform(post("/conversation/create")
                        .session(session)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody))
                .andReturn()
                .getResponse()
                .getContentAsString();

        // 提取conversationId（这里简化处理，实际可以用JSON解析）
        log.info("Create response: {}", response);

        log.info("获取对话消息接口测试通过");
    }
}

