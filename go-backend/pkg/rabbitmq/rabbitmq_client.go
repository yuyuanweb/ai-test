// Package rabbitmq RabbitMQ客户端封装
// @author <a href="https://codefather.cn">编程导航学习圈</a>
package rabbitmq

import (
	"ai-test-go/internal/config"
	"ai-test-go/internal/constant"
	"context"
	"encoding/json"
	"time"

	amqp "github.com/rabbitmq/amqp091-go"
)

type RabbitMQClient struct {
	conn    *amqp.Connection
	channel *amqp.Channel
}

func NewRabbitMQClient() (*RabbitMQClient, error) {
	if config.RabbitMQConn == nil || config.RabbitMQChannel == nil {
		return nil, nil
	}
	return &RabbitMQClient{
		conn:    config.RabbitMQConn,
		channel: config.RabbitMQChannel,
	}, nil
}

func (c *RabbitMQClient) PublishMessage(ctx context.Context, message interface{}, priority uint8) error {
	if c.channel == nil {
		return nil
	}

	body, err := json.Marshal(message)
	if err != nil {
		return err
	}

	return c.channel.PublishWithContext(
		ctx,
		constant.TestExchange,
		constant.TestRoutingKey,
		false,
		false,
		amqp.Publishing{
			ContentType:  "application/json",
			Body:         body,
			DeliveryMode: amqp.Persistent,
			Priority:     priority,
			Timestamp:    time.Now(),
		},
	)
}

func (c *RabbitMQClient) Close() {
	if c.channel != nil {
		c.channel.Close()
	}
	if c.conn != nil {
		c.conn.Close()
	}
}

func CalculatePriority(totalSubtasks int) uint8 {
	if totalSubtasks <= constant.TotalSubtasksThresholdHigh {
		return constant.PriorityHigh
	} else if totalSubtasks <= constant.TotalSubtasksThresholdNormal {
		return constant.PriorityNormal
	}
	return constant.PriorityLow
}
