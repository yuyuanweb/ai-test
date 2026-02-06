// Package service 进度推送服务层
// @author <a href="https://codefather.cn">编程导航学习圈</a>
package service

import (
	"ai-test-go/internal/model/vo"
	"ai-test-go/pkg/logger"
	"fmt"
)

type StompSender interface {
	SendToTopic(topic string, message interface{}) error
}

type ProgressNotificationService struct {
	stompHandler StompSender
}

func NewProgressNotificationService() *ProgressNotificationService {
	return &ProgressNotificationService{}
}

func (s *ProgressNotificationService) SetStompHandler(handler StompSender) {
	s.stompHandler = handler
}

func (s *ProgressNotificationService) SendProgress(taskID string, progress *vo.TaskProgressVO) {
	if s.stompHandler == nil {
		logger.Log.Warn("STOMP处理器未初始化，无法推送进度")
		return
	}

	topic := fmt.Sprintf("/topic/task/%s", taskID)
	if err := s.stompHandler.SendToTopic(topic, progress); err != nil {
		logger.Log.Errorf("推送任务进度失败: taskId=%s, error=%v", taskID, err)
		return
	}

	logger.Log.Infof("推送任务进度: taskId=%s, percentage=%d%%", taskID, progress.Percentage)
}
