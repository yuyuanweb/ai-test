// Package handler 数据统计HTTP处理器
// @author <a href="https://codefather.cn">编程导航学习圈</a>
package handler

import (
	"ai-test-go/internal/service"
	"ai-test-go/pkg/common"
	"log"
	"net/http"
	"strconv"

	"github.com/gin-gonic/gin"
)

type StatisticsHandler struct {
	statisticsService *service.StatisticsService
}

func NewStatisticsHandler(statisticsService *service.StatisticsService) *StatisticsHandler {
	return &StatisticsHandler{
		statisticsService: statisticsService,
	}
}

// GetCostStatistics 获取成本统计数据
// @Summary      获取成本统计
// @Description  成本统计含总成本、今日/本周/本月、按模型分布、按日趋势
// @Tags         数据统计
// @Accept       json
// @Produce      json
// @Param        days  query     int  false  "统计天数，默认30"
// @Success      200   {object}  common.BaseResponse{data=vo.CostStatisticsVO}
// @Router       /statistics/cost [get]
func (h *StatisticsHandler) GetCostStatistics(c *gin.Context) {
	userID, exists := c.Get("userID")
	if !exists {
		c.JSON(http.StatusOK, common.ErrorWithDefaultMsg(common.NOT_LOGIN_ERROR))
		return
	}

	days := 30
	if d := c.Query("days"); d != "" {
		if v, err := strconv.Atoi(d); err == nil && v > 0 {
			days = v
		}
	}

	result, err := h.statisticsService.GetCostStatistics(userID.(int64), days)
	if err != nil {
		log.Printf("获取成本统计失败: %v", err)
		c.JSON(http.StatusOK, common.Error(common.SYSTEM_ERROR, "获取成本统计失败"))
		return
	}

	c.JSON(http.StatusOK, common.Success(result))
}

// GetRealtimeCost 获取实时成本监控数据
// @Summary      获取实时成本监控
// @Description  今日/本月消耗、Token与调用次数、预算状态与预警信息
// @Tags         数据统计
// @Accept       json
// @Produce      json
// @Success      200  {object}  common.BaseResponse{data=vo.RealtimeCostVO}
// @Router       /statistics/realtime [get]
func (h *StatisticsHandler) GetRealtimeCost(c *gin.Context) {
	userID, exists := c.Get("userID")
	if !exists {
		c.JSON(http.StatusOK, common.ErrorWithDefaultMsg(common.NOT_LOGIN_ERROR))
		return
	}

	result, err := h.statisticsService.GetRealtimeCost(userID.(int64))
	if err != nil {
		log.Printf("获取实时成本失败: %v", err)
		c.JSON(http.StatusOK, common.Error(common.SYSTEM_ERROR, "获取实时成本失败"))
		return
	}

	c.JSON(http.StatusOK, common.Success(result))
}

// GetUsageStatistics 获取使用统计数据
// @Summary      获取使用统计
// @Description  总/今日API调用、Token、按模型分布、按日趋势
// @Tags         数据统计
// @Accept       json
// @Produce      json
// @Param        days  query     int  false  "统计天数，默认30"
// @Success      200   {object}  common.BaseResponse{data=vo.UsageStatisticsVO}
// @Router       /statistics/usage [get]
func (h *StatisticsHandler) GetUsageStatistics(c *gin.Context) {
	userID, exists := c.Get("userID")
	if !exists {
		c.JSON(http.StatusOK, common.ErrorWithDefaultMsg(common.NOT_LOGIN_ERROR))
		return
	}

	days := 30
	if d := c.Query("days"); d != "" {
		if v, err := strconv.Atoi(d); err == nil && v > 0 {
			days = v
		}
	}

	result, err := h.statisticsService.GetUsageStatistics(userID.(int64), days)
	if err != nil {
		log.Printf("获取使用统计失败: %v", err)
		c.JSON(http.StatusOK, common.Error(common.SYSTEM_ERROR, "获取使用统计失败"))
		return
	}

	c.JSON(http.StatusOK, common.Success(result))
}

// GetPerformanceStatistics 获取性能统计数据
// @Summary      获取性能统计
// @Description  平均/最小/最大响应时间、按模型性能明细
// @Tags         数据统计
// @Accept       json
// @Produce      json
// @Success      200  {object}  common.BaseResponse{data=vo.PerformanceStatisticsVO}
// @Router       /statistics/performance [get]
func (h *StatisticsHandler) GetPerformanceStatistics(c *gin.Context) {
	userID, exists := c.Get("userID")
	if !exists {
		c.JSON(http.StatusOK, common.ErrorWithDefaultMsg(common.NOT_LOGIN_ERROR))
		return
	}

	result, err := h.statisticsService.GetPerformanceStatistics(userID.(int64))
	if err != nil {
		log.Printf("获取性能统计失败: %v", err)
		c.JSON(http.StatusOK, common.Error(common.SYSTEM_ERROR, "获取性能统计失败"))
		return
	}

	c.JSON(http.StatusOK, common.Success(result))
}
