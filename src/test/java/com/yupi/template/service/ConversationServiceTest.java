package com.yupi.template.service;

import cn.hutool.json.JSONUtil;
import com.mybatisflex.core.paginate.Page;
import com.yupi.template.model.dto.conversation.CreateConversationRequest;
import com.yupi.template.model.entity.Conversation;
import com.yupi.template.model.entity.ConversationMessage;
import com.yupi.template.model.enums.ConversationTypeEnum;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 对话服务测试
 *
 * @author <a href="https://codefather.cn">编程导航学习圈</a>
 */
@SpringBootTest
@Slf4j
class ConversationServiceTest {

    @Resource
    private ConversationService conversationService;

    /**
     * 测试用户ID
     */
    private static final Long TEST_USER_ID = 1L;

    /**
     * 测试创建对话
     */
    @Test
    void testCreateConversation() {
        CreateConversationRequest request = new CreateConversationRequest();
        request.setTitle("测试对话");
        request.setConversationType(ConversationTypeEnum.SIDE_BY_SIDE.getValue());
        request.setModels(Arrays.asList("openai/gpt-4o", "anthropic/claude-3.5-sonnet"));

        String conversationId = conversationService.createConversation(request, TEST_USER_ID);
        
        assertNotNull(conversationId, "对话ID不应为空");
        log.info("创建对话成功: {}", conversationId);

        // 验证对话详情
        Conversation conversation = conversationService.getConversation(conversationId, TEST_USER_ID);
        assertNotNull(conversation, "对话不应为空");
        assertEquals("测试对话", conversation.getTitle(), "标题应匹配");
        assertEquals(ConversationTypeEnum.SIDE_BY_SIDE.getValue(), conversation.getConversationType(), "类型应匹配");
        
        List<String> models = JSONUtil.toList(conversation.getModels(), String.class);
        assertEquals(2, models.size(), "模型数量应为2");
        assertTrue(models.contains("openai/gpt-4o"), "应包含gpt-4o");
        assertTrue(models.contains("anthropic/claude-3.5-sonnet"), "应包含claude");
        
        log.info("对话详情验证通过: {}", conversation);
    }

    /**
     * 测试获取对话列表
     */
    @Test
    void testListConversations() {
        // 先创建几个对话
        for (int i = 0; i < 3; i++) {
            CreateConversationRequest request = new CreateConversationRequest();
            request.setTitle("测试对话" + i);
            request.setConversationType(ConversationTypeEnum.SIDE_BY_SIDE.getValue());
            request.setModels(Arrays.asList("openai/gpt-4o"));
            conversationService.createConversation(request, TEST_USER_ID);
        }

        // 查询列表
        Page<Conversation> page = conversationService.listConversations(TEST_USER_ID, 1, 10, null);
        
        assertNotNull(page, "分页结果不应为空");
        assertTrue(page.getTotalRow() >= 3, "至少应有3条记录");
        log.info("对话列表查询成功: total={}, records={}", page.getTotalRow(), page.getRecords().size());
    }

    /**
     * 测试获取对话消息
     */
    @Test
    void testGetConversationMessages() {
        // 创建对话
        CreateConversationRequest request = new CreateConversationRequest();
        request.setTitle("消息测试对话");
        request.setConversationType(ConversationTypeEnum.SIDE_BY_SIDE.getValue());
        request.setModels(Arrays.asList("openai/gpt-4o"));
        String conversationId = conversationService.createConversation(request, TEST_USER_ID);

        // 查询消息（新创建的对话应该没有消息）
        List<ConversationMessage> messages = conversationService.getConversationMessages(conversationId, TEST_USER_ID);
        
        assertNotNull(messages, "消息列表不应为空");
        assertEquals(0, messages.size(), "新对话应该没有消息");
        log.info("对话消息查询成功: conversationId={}, messageCount={}", conversationId, messages.size());
    }

    /**
     * 测试删除对话
     */
    @Test
    void testDeleteConversation() {
        // 创建对话
        CreateConversationRequest request = new CreateConversationRequest();
        request.setTitle("待删除对话");
        request.setConversationType(ConversationTypeEnum.SIDE_BY_SIDE.getValue());
        request.setModels(Arrays.asList("openai/gpt-4o"));
        String conversationId = conversationService.createConversation(request, TEST_USER_ID);

        // 删除对话
        boolean deleted = conversationService.deleteConversation(conversationId, TEST_USER_ID);
        assertTrue(deleted, "删除应该成功");
        log.info("对话删除成功: {}", conversationId);

        // 验证已删除（应该查不到）
        Conversation conversation = conversationService.getConversation(conversationId, TEST_USER_ID);
        assertNull(conversation, "已删除的对话应查询不到");
    }

    /**
     * 测试参数校验 - 单个模型
     */
    @Test
    void testValidation_MinModels() {
        // 创建对话时1个模型应该成功（已支持1-8个模型）
        CreateConversationRequest request = new CreateConversationRequest();
        request.setTitle("测试对话");
        request.setConversationType(ConversationTypeEnum.SIDE_BY_SIDE.getValue());
        request.setModels(Arrays.asList("openai/gpt-4o")); // 1个模型

        // Side-by-Side支持1-8个模型，这里应该正常创建
        String conversationId = conversationService.createConversation(request, TEST_USER_ID);
        assertNotNull(conversationId, "对话创建应该成功");
        log.info("参数校验测试通过（单个模型）");
    }

    /**
     * 测试参数校验 - 对话类型无效
     */
    @Test
    void testValidation_InvalidType() {
        CreateConversationRequest request = new CreateConversationRequest();
        request.setTitle("测试对话");
        request.setConversationType("invalid_type");
        request.setModels(Arrays.asList("openai/gpt-4o"));

        assertThrows(Exception.class, () -> {
            conversationService.createConversation(request, TEST_USER_ID);
        }, "无效的对话类型应抛出异常");
        
        log.info("参数校验测试通过（无效类型）");
    }
}

