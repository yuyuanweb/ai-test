package com.yupi.template.job;

import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

/**
 * 同步模型任务测试
 *
 * @author <a href="https://codefather.cn">编程导航学习圈</a>
 */
@SpringBootTest
@Slf4j
class SyncModelJobTest {

    @Resource
    private SyncModelJob syncModelJob;

    /**
     * 手动执行一次同步任务
     */
    @Test
    void testSyncModels() {
        log.info("手动执行模型同步任务");
        syncModelJob.syncModels();
        log.info("模型同步任务执行完成");
    }
}

