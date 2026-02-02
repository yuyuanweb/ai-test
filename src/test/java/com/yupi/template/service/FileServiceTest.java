package com.yupi.template.service;

import com.yupi.template.model.entity.User;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.io.File;

/**
 * 批量测试服务测试
 *
 * @author <a href="https://codefather.cn">编程导航学习圈</a>
 */
@SpringBootTest
@ActiveProfiles("local")
@Slf4j
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class FileServiceTest {

    @Resource
    private FileService fileService;
    @Resource
    private UserService userService;

    /**
     * 测试创建批量测试任务 - 成功场景
     */
    @Test
    @Order(1)
    @Transactional
    void testFileUpload() {
        User loginUser = userService.getById(1L);
        String a = fileService.uploadFile(new File("/Users/gulihua/Downloads/29KfXalt_image_mianshiya.png"), "MySQL.png", loginUser);
        System.out.println(a);
    }

}
