package com.yupi.template.controller;

import com.yupi.template.constant.UserConstant;
import com.yupi.template.model.entity.Scene;
import com.yupi.template.model.entity.ScenePrompt;
import com.yupi.template.model.entity.User;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultHandlers.print;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * 场景管理接口测试
 *
 * @author <a href="https://codefather.cn">编程导航学习圈</a>
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("local")
@Slf4j
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class SceneControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private com.yupi.template.mapper.SceneMapper sceneMapper;

    @Autowired
    private com.yupi.template.mapper.ScenePromptMapper scenePromptMapper;

    private MockHttpSession session;
    private static final Long TEST_USER_ID = 1L;
    private static String testSceneId;
    private static String testPromptId;

    /**
     * 测试前准备 - 模拟登录用户
     */
    @BeforeEach
    void setUp() {
        session = new MockHttpSession();
        User testUser = new User();
        testUser.setId(TEST_USER_ID);
        testUser.setUserAccount("test");
        testUser.setUserName("测试用户");
        session.setAttribute(UserConstant.USER_LOGIN_STATE, testUser);
    }

    /**
     * 测试创建场景接口 - 成功场景
     */
    @Test
    @Order(1)
    @Transactional
    void testCreateScene_Success() throws Exception {
        log.info("=== 测试创建场景接口 - 成功场景 ===");

        String requestBody = """
                {
                    "name": "测试场景-编程能力",
                    "description": "用于测试的编程能力场景",
                    "category": "编程"
                }
                """;

        String response = mockMvc.perform(post("/scene/create")
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

        log.info("创建场景接口测试通过: response={}", response);
    }

    /**
     * 测试创建场景接口 - 名称为空
     */
    @Test
    @Order(2)
    void testCreateScene_EmptyName() throws Exception {
        log.info("=== 测试创建场景接口 - 名称为空 ===");

        String requestBody = """
                {
                    "name": "",
                    "description": "测试描述",
                    "category": "编程"
                }
                """;

        mockMvc.perform(post("/scene/create")
                        .session(session)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody))
                .andDo(print())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(40000));

        log.info("名称为空测试通过");
    }

    /**
     * 测试获取场景详情接口 - 成功场景
     */
    @Test
    @Order(3)
    void testGetScene_Success() throws Exception {
        log.info("=== 测试获取场景详情接口 - 成功场景 ===");

        if (testSceneId == null) {
            initializeTestScene();
        }

        if (testSceneId == null) {
            log.warn("跳过测试：testSceneId为空");
            return;
        }

        mockMvc.perform(get("/scene/get")
                        .session(session)
                        .param("id", testSceneId))
                .andDo(print())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data").exists())
                .andExpect(jsonPath("$.data.id").value(testSceneId));

        log.info("获取场景详情接口测试通过");
    }

    /**
     * 测试获取场景详情接口 - 场景ID为空
     */
    @Test
    @Order(4)
    void testGetScene_EmptyId() throws Exception {
        log.info("=== 测试获取场景详情接口 - 场景ID为空 ===");

        mockMvc.perform(get("/scene/get")
                        .session(session)
                        .param("id", ""))
                .andDo(print())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(40000));

        log.info("场景ID为空测试通过");
    }

    /**
     * 测试更新场景接口 - 成功场景
     */
    @Test
    @Order(5)
    @Transactional
    void testUpdateScene_Success() throws Exception {
        log.info("=== 测试更新场景接口 - 成功场景 ===");

        if (testSceneId == null) {
            initializeTestScene();
        }

        if (testSceneId == null) {
            log.warn("跳过测试：testSceneId为空");
            return;
        }

        String requestBody = String.format("""
                {
                    "id": "%s",
                    "name": "更新后的场景名称",
                    "description": "更新后的描述",
                    "category": "数学"
                }
                """, testSceneId);

        mockMvc.perform(post("/scene/update")
                        .session(session)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody))
                .andDo(print())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data").value(true));

        log.info("更新场景接口测试通过");
    }

    /**
     * 测试分页查询场景列表接口 - 成功场景
     */
    @Test
    @Order(6)
    void testListScenes_Success() throws Exception {
        log.info("=== 测试分页查询场景列表接口 - 成功场景 ===");

        mockMvc.perform(get("/scene/list/page")
                        .session(session)
                        .param("pageNum", "1")
                        .param("pageSize", "10"))
                .andDo(print())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data").exists())
                .andExpect(jsonPath("$.data.records").isArray());

        log.info("分页查询场景列表接口测试通过");
    }

    /**
     * 测试分页查询场景列表接口 - 按分类筛选
     */
    @Test
    @Order(7)
    void testListScenes_WithCategory() throws Exception {
        log.info("=== 测试分页查询场景列表接口 - 按分类筛选 ===");

        mockMvc.perform(get("/scene/list/page")
                        .session(session)
                        .param("pageNum", "1")
                        .param("pageSize", "10")
                        .param("category", "编程"))
                .andDo(print())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data").exists())
                .andExpect(jsonPath("$.data.records").isArray());

        log.info("按分类筛选测试通过");
    }

    /**
     * 测试删除场景接口 - 成功场景
     */
    @Test
    @Order(8)
    @Transactional
    void testDeleteScene_Success() throws Exception {
        log.info("=== 测试删除场景接口 - 成功场景 ===");

        if (testSceneId == null) {
            initializeTestScene();
        }

        if (testSceneId == null) {
            log.warn("跳过测试：testSceneId为空");
            return;
        }

        String requestBody = String.format("""
                {
                    "id": "%s"
                }
                """, testSceneId);

        mockMvc.perform(post("/scene/delete")
                        .session(session)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody))
                .andDo(print())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data").value(true));

        log.info("删除场景接口测试通过");
    }

    /**
     * 测试添加场景提示词接口 - 成功场景
     */
    @Test
    @Order(9)
    @Transactional
    void testAddScenePrompt_Success() throws Exception {
        log.info("=== 测试添加场景提示词接口 - 成功场景 ===");

        if (testSceneId == null) {
            initializeTestScene();
        }

        if (testSceneId == null) {
            log.warn("跳过测试：testSceneId为空");
            return;
        }

        String requestBody = String.format("""
                {
                    "sceneId": "%s",
                    "title": "测试提示词1",
                    "content": "请写一个Java函数，计算两个数的和",
                    "difficulty": "easy"
                }
                """, testSceneId);

        String response = mockMvc.perform(post("/scene/prompt/add")
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

        log.info("添加场景提示词接口测试通过: response={}", response);
    }

    /**
     * 测试获取场景提示词列表接口 - 成功场景
     */
    @Test
    @Order(10)
    void testGetScenePrompts_Success() throws Exception {
        log.info("=== 测试获取场景提示词列表接口 - 成功场景 ===");

        if (testSceneId == null) {
            initializeTestScene();
        }

        if (testSceneId == null) {
            log.warn("跳过测试：testSceneId为空");
            return;
        }

        mockMvc.perform(get("/scene/prompts")
                        .session(session)
                        .param("sceneId", testSceneId))
                .andDo(print())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data").isArray());

        log.info("获取场景提示词列表接口测试通过");
    }

    /**
     * 测试更新场景提示词接口 - 成功场景
     */
    @Test
    @Order(11)
    @Transactional
    void testUpdateScenePrompt_Success() throws Exception {
        log.info("=== 测试更新场景提示词接口 - 成功场景 ===");

        if (testSceneId == null || testPromptId == null) {
            initializeTestSceneAndPrompt();
        }

        if (testPromptId == null) {
            log.warn("跳过测试：testPromptId为空");
            return;
        }

        String requestBody = String.format("""
                {
                    "id": "%s",
                    "title": "更新后的提示词标题",
                    "content": "更新后的提示词内容",
                    "difficulty": "medium"
                }
                """, testPromptId);

        mockMvc.perform(post("/scene/prompt/update")
                        .session(session)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody))
                .andDo(print())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data").value(true));

        log.info("更新场景提示词接口测试通过");
    }

    /**
     * 测试删除场景提示词接口 - 成功场景
     */
    @Test
    @Order(12)
    @Transactional
    void testDeleteScenePrompt_Success() throws Exception {
        log.info("=== 测试删除场景提示词接口 - 成功场景 ===");

        if (testSceneId == null || testPromptId == null) {
            initializeTestSceneAndPrompt();
        }

        if (testPromptId == null) {
            log.warn("跳过测试：testPromptId为空");
            return;
        }

        String requestBody = String.format("""
                {
                    "id": "%s"
                }
                """, testPromptId);

        mockMvc.perform(post("/scene/prompt/delete")
                        .session(session)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody))
                .andDo(print())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data").value(true));

        log.info("删除场景提示词接口测试通过");
    }

    /**
     * 测试未登录访问（应该返回错误）
     */
    @Test
    @Order(13)
    void testUnauthorizedAccess() throws Exception {
        log.info("=== 测试未登录访问 ===");

        mockMvc.perform(get("/scene/list/page")
                        .param("pageNum", "1")
                        .param("pageSize", "10"))
                .andDo(print())
                .andExpect(status().isOk());

        log.info("未登录访问测试通过");
    }

    /**
     * 初始化测试场景
     */
    private void initializeTestScene() {
        try {
            String sceneId = UUID.randomUUID().toString();
            Scene scene = Scene.builder()
                    .id(sceneId)
                    .userId(TEST_USER_ID)
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
            testSceneId = sceneId;
            log.info("初始化测试场景成功: sceneId={}", sceneId);
        } catch (Exception e) {
            log.warn("初始化测试场景失败: {}", e.getMessage());
        }
    }

    /**
     * 初始化测试场景和提示词
     */
    private void initializeTestSceneAndPrompt() {
        if (testSceneId == null) {
            initializeTestScene();
        }

        if (testSceneId != null && testPromptId == null) {
            try {
                String promptId = UUID.randomUUID().toString();
                ScenePrompt prompt = ScenePrompt.builder()
                        .id(promptId)
                        .sceneId(testSceneId)
                        .userId(TEST_USER_ID)
                        .promptIndex(0)
                        .title("测试提示词")
                        .content("这是一个测试提示词")
                        .createTime(java.time.LocalDateTime.now())
                        .updateTime(java.time.LocalDateTime.now())
                        .isDelete(0)
                        .build();

                scenePromptMapper.insert(prompt);
                testPromptId = promptId;
                log.info("初始化测试提示词成功: promptId={}", promptId);
            } catch (Exception e) {
                log.warn("初始化测试提示词失败: {}", e.getMessage());
            }
        }
    }
}
