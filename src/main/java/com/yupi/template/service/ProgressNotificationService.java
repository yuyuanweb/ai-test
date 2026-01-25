package com.yupi.template.service;

import com.yupi.template.model.vo.TaskProgressVO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;

import jakarta.annotation.Resource;

/**
 * 进度推送服务
 *
 * @author <a href="https://codefather.cn">编程导航学习圈</a>
 */
@Service
@Slf4j
public class ProgressNotificationService {

    @Resource
    private SimpMessagingTemplate messagingTemplate;

    /**
     * 推送任务进度
     *
     * @param taskId 任务ID
     * @param progress 进度信息
     */
    public void sendProgress(String taskId, TaskProgressVO progress) {
        try {
            messagingTemplate.convertAndSend("/topic/task/" + taskId, progress);
            log.debug("推送任务进度: taskId={}, percentage={}%", taskId, progress.getPercentage());
        } catch (Exception e) {
            log.error("推送任务进度失败: taskId={}", taskId, e);
        }
    }
}

