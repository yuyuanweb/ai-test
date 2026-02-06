// Package config RabbitMQ配置
// @author <a href="https://codefather.cn">编程导航学习圈</a>
package config

import (
	"ai-test-go/internal/constant"
	"ai-test-go/pkg/logger"
	"fmt"

	amqp "github.com/rabbitmq/amqp091-go"
)

var RabbitMQConn *amqp.Connection
var RabbitMQChannel *amqp.Channel

func InitRabbitMQ() error {
	if AppConfig.RabbitMQ.Host == "" {
		logger.Log.Warn("RabbitMQ未配置，跳过初始化")
		return nil
	}

	url := fmt.Sprintf("amqp://%s:%s@%s:%d/",
		AppConfig.RabbitMQ.Username,
		AppConfig.RabbitMQ.Password,
		AppConfig.RabbitMQ.Host,
		AppConfig.RabbitMQ.Port,
	)

	conn, err := amqp.Dial(url)
	if err != nil {
		return fmt.Errorf("连接RabbitMQ失败: %w", err)
	}
	RabbitMQConn = conn

	ch, err := conn.Channel()
	if err != nil {
		return fmt.Errorf("创建RabbitMQ Channel失败: %w", err)
	}
	RabbitMQChannel = ch

	args := amqp.Table{
		"x-max-priority": constant.QueueMaxPriority,
		"x-max-length":   constant.QueueMaxLength,
	}
	_, err = ch.QueueDeclare(
		constant.TestQueue,
		true,
		false,
		false,
		false,
		args,
	)
	if err != nil {
		return fmt.Errorf("声明队列失败: %w", err)
	}

	err = ch.ExchangeDeclare(
		constant.TestExchange,
		"direct",
		true,
		false,
		false,
		false,
		nil,
	)
	if err != nil {
		return fmt.Errorf("声明交换器失败: %w", err)
	}

	err = ch.QueueBind(
		constant.TestQueue,
		constant.TestRoutingKey,
		constant.TestExchange,
		false,
		nil,
	)
	if err != nil {
		return fmt.Errorf("绑定队列失败: %w", err)
	}

	logger.Log.Info("RabbitMQ初始化成功")
	return nil
}

func CloseRabbitMQ() {
	if RabbitMQChannel != nil {
		RabbitMQChannel.Close()
	}
	if RabbitMQConn != nil {
		RabbitMQConn.Close()
	}
}
