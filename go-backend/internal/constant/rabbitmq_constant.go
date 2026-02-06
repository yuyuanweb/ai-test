// Package constant RabbitMQ常量定义
// @author <a href="https://codefather.cn">编程导航学习圈</a>
package constant

const (
	TestQueue      = "ai-model-batch-test.queue"
	TestExchange   = "ai-model-batch-test.exchange"
	TestRoutingKey = "ai-model-batch-test.routing.key"

	QueueMaxPriority = 10
	QueueMaxLength   = 10000

	PriorityHigh   = 10
	PriorityNormal = 5
	PriorityLow    = 1

	TotalSubtasksThresholdHigh   = 10
	TotalSubtasksThresholdNormal = 50
)
