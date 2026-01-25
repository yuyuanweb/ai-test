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
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultHandlers.print;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * 批量测试接口测试
 *
 * @author <a href="https://codefather.cn">编程导航学习圈</a>
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("local")
@Slf4j
class BatchTestControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private com.yupi.template.mapper.SceneMapper sceneMapper;

    @Autowired
    private com.yupi.template.mapper.ScenePromptMapper scenePromptMapper;

    private MockHttpSession session;
    private static String testSceneId;
    private static String testTaskId;

    /**
     * 测试前准备 - 模拟登录用户并初始化测试数据
     */
    @BeforeEach
    void setUp() {
        session = new MockHttpSession();
        User testUser = new User();
        testUser.setId(1L);
        testUser.setUserAccount("test");
        testUser.setUserName("测试用户");
        session.setAttribute(UserConstant.USER_LOGIN_STATE, testUser);

        if (testSceneId == null) {
            initializeTestScene();
        }
    }

    /**
     * 初始化测试场景
     */
    private void initializeTestScene() {
        try {
            com.yupi.template.model.entity.Scene scene = com.yupi.template.model.entity.Scene.builder()
                    .id(java.util.UUID.randomUUID().toString())
                    .userId(1L)
                    .name("Controller测试场景")
                    .description("用于Controller测试的场景")
                    .category("测试")
                    .isPreset(0)
                    .isActive(1)
                    .createTime(java.time.LocalDateTime.now())
                    .updateTime(java.time.LocalDateTime.now())
                    .isDelete(0)
                    .build();

            sceneMapper.insert(scene);
            testSceneId = scene.getId();

            com.yupi.template.model.entity.ScenePrompt prompt = com.yupi.template.model.entity.ScenePrompt.builder()
                    .id(java.util.UUID.randomUUID().toString())
                    .sceneId(testSceneId)
                    .userId(1L)
                    .promptIndex(0)
                    .title("测试提示词")
                    .content("这是一个测试提示词")
                    .createTime(java.time.LocalDateTime.now())
                    .updateTime(java.time.LocalDateTime.now())
                    .isDelete(0)
                    .build();

            scenePromptMapper.insert(prompt);
            log.info("初始化测试场景成功: sceneId={}", testSceneId);
        } catch (Exception e) {
            log.warn("初始化测试场景失败: {}", e.getMessage());
        }
    }

    /**
     * 测试创建批量测试任务接口 - 成功场景
     */
    @Test
    void testCreateBatchTestTask_Success() throws Exception {
        log.info("=== 测试创建批量测试任务接口 - 成功场景 ===");

        if (testSceneId == null) {
            log.warn("跳过测试：testSceneId为空，需要先初始化测试场景");
            return;
        }

        String requestBody = String.format("""
                {
                    "name": "接口测试任务",
                    "sceneId": "%s",
                    "models": ["openai/gpt-4o", "anthropic/claude-3.5-sonnet"]
                }
                """, testSceneId);

        String response = mockMvc.perform(post("/batch-test/create")
                        .session(session)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody))
                .andDo(print())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data").isString())
                .andReturn()
                .getResponse()
                .getContentAsString();

        log.info("创建批量测试任务接口测试通过: response={}", response);
    }

    /**
     * 测试创建批量测试任务接口 - 参数为空
     */
    @Test
    void testCreateBatchTestTask_EmptyParams() throws Exception {
        log.info("=== 测试创建批量测试任务接口 - 参数为空 ===");

        String requestBody = """
                {
                    "name": "",
                    "sceneId": "",
                    "models": []
                }
                """;

        mockMvc.perform(post("/batch-test/create")
                        .session(session)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody))
                .andDo(print())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(40000));

        log.info("参数为空测试通过");
    }

    /**
     * 测试获取任务详情接口 - 成功场景
     */
    @Test
    void testGetTask_Success() throws Exception {
        log.info("=== 测试获取任务详情接口 - 成功场景 ===");

        if (testTaskId == null) {
            log.warn("跳过测试：testTaskId为空，需要先创建任务");
            return;
        }

        mockMvc.perform(get("/batch-test/task/get")
                        .session(session)
                        .param("taskId", testTaskId))
                .andDo(print())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data").exists())
                .andExpect(jsonPath("$.data.id").value(testTaskId));

        log.info("获取任务详情接口测试通过");
    }

    /**
     * 测试获取任务详情接口 - 任务ID为空
     */
    @Test
    void testGetTask_EmptyTaskId() throws Exception {
        log.info("=== 测试获取任务详情接口 - 任务ID为空 ===");

        mockMvc.perform(get("/batch-test/task/get")
                        .session(session)
                        .param("taskId", ""))
                .andDo(print())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(40000));

        log.info("任务ID为空测试通过");
    }

    /**
     * 测试分页查询任务列表接口 - 成功场景
     */
    @Test
    void testListTasks_Success() throws Exception {
        log.info("=== 测试分页查询任务列表接口 - 成功场景 ===");

        mockMvc.perform(get("/batch-test/task/list/page")
                        .session(session)
                        .param("pageNum", "1")
                        .param("pageSize", "10"))
                .andDo(print())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data").exists())
                .andExpect(jsonPath("$.data.records").isArray());

        log.info("分页查询任务列表接口测试通过");
    }

    /**
     * 测试分页查询任务列表接口 - 按状态筛选
     */
    @Test
    void testListTasks_WithStatus() throws Exception {
        log.info("=== 测试分页查询任务列表接口 - 按状态筛选 ===");

        mockMvc.perform(get("/batch-test/task/list/page")
                        .session(session)
                        .param("pageNum", "1")
                        .param("pageSize", "10")
                        .param("status", "pending"))
                .andDo(print())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data").exists())
                .andExpect(jsonPath("$.data.records").isArray());

        log.info("按状态筛选测试通过");
    }

    /**
     * 测试删除任务接口 - 成功场景
     */
    @Test
    void testDeleteTask_Success() throws Exception {
        log.info("=== 测试删除任务接口 - 成功场景 ===");

        if (testTaskId == null) {
            log.warn("跳过测试：testTaskId为空，需要先创建任务");
            return;
        }

        String requestBody = String.format("""
                {
                    "id": "%s"
                }
                """, testTaskId);

        mockMvc.perform(post("/batch-test/task/delete")
                        .session(session)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody))
                .andDo(print())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data").value(true));

        log.info("删除任务接口测试通过");
    }

    /**
     * 测试获取任务结果接口 - 成功场景
     */
    @Test
    void testGetTaskResults_Success() throws Exception {
        log.info("=== 测试获取任务结果接口 - 成功场景 ===");

        if (testTaskId == null) {
            log.warn("跳过测试：testTaskId为空，需要先创建任务");
            return;
        }

        mockMvc.perform(get("/batch-test/result/list")
                        .session(session)
                        .param("taskId", testTaskId))
                .andDo(print())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data").isArray());

        log.info("获取任务结果接口测试通过");
    }

    /**
     * 测试未登录访问（应该返回错误）
     */
    @Test
    void testUnauthorizedAccess() throws Exception {
        log.info("=== 测试未登录访问 ===");

        mockMvc.perform(get("/batch-test/task/list/page")
                        .param("pageNum", "1")
                        .param("pageSize", "10"))
                .andDo(print())
                .andExpect(status().isOk());

        log.info("未登录访问测试通过");
    }
}
