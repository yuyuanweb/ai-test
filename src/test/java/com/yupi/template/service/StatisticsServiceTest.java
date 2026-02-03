package com.yupi.template.service;

import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 统计服务测试
 *
 * @author <a href="https://codefather.cn">编程导航学习圈</a>
 */
@SpringBootTest
@Slf4j
class StatisticsServiceTest {

    @Resource
    private StatisticsService statisticsService;

    /**
     * 测试刷新用户模型使用统计数据
     * 从历史会话消息和测试结果中重新聚合数据到用户模型使用表
     */
    @Test
    void testRefreshUserModelUsageData() {
        log.info("开始执行刷新用户模型使用统计数据测试...");
        
        int count = statisticsService.refreshUserModelUsageData();
        
        log.info("刷新完成，共插入 {} 条记录", count);
        assertTrue(count >= 0, "刷新记录数应大于等于0");
    }
}
