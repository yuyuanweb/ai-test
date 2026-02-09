// Package constant 缓存相关常量
// @author <a href="https://codefather.cn">编程导航学习圈</a>
package constant

const (
	ModelPricingKeyPrefix = "model:pricing:"
	ModelPricingTTLHours  = 24

	UserDailyCostKeyPrefix   = "user:cost:daily:"
	UserMonthlyCostKeyPrefix  = "user:cost:monthly:"
	UserDailyCostTTLHours     = 25
	UserMonthlyCostTTLDays    = 32
	StatisticsCostKeyPrefix   = "statistics:cost:"
	StatisticsUsageKeyPrefix  = "statistics:usage:"
	StatisticsPerformanceKeyPrefix = "statistics:performance:"
	StatisticsTTLMinutes     = 5
)
