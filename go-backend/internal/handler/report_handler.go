// Package handler 报告HTTP处理器
// @author <a href="https://codefather.cn">编程导航学习圈</a>
package handler

import (
	"ai-test-go/internal/service"
	"ai-test-go/pkg/common"
	"net/http"

	"github.com/gin-gonic/gin"
)

type ReportHandler struct {
	reportService *service.ReportService
}

func NewReportHandler(reportService *service.ReportService) *ReportHandler {
	return &ReportHandler{
		reportService: reportService,
	}
}

// GenerateReport 生成测试报告
// @Summary      生成测试报告
// @Description  根据任务ID生成多维度对比报告，包含统计分析、雷达图和柱状图数据
// @Tags         报告
// @Accept       json
// @Produce      json
// @Param        taskId  query     string  true  "任务ID"
// @Success      200     {object}  common.BaseResponse{data=vo.ReportVO}  "生成成功"
// @Failure      400     {object}  common.BaseResponse  "参数错误"
// @Router       /report/generate [get]
func (h *ReportHandler) GenerateReport(c *gin.Context) {
	taskID := c.Query("taskId")
	if taskID == "" {
		c.JSON(http.StatusOK, common.Error(common.PARAMS_ERROR, "任务ID不能为空"))
		return
	}

	userID, exists := c.Get("userID")
	if !exists {
		c.JSON(http.StatusOK, common.ErrorWithDefaultMsg(common.NOT_LOGIN_ERROR))
		return
	}

	report, err := h.reportService.GenerateReport(taskID, userID.(int64))
	if err != nil {
		if bizErr, ok := err.(*common.BusinessException); ok {
			c.JSON(http.StatusOK, common.Error(bizErr.Code, bizErr.Message))
		} else {
			c.JSON(http.StatusOK, common.Error(common.SYSTEM_ERROR, "生成报告失败"))
		}
		return
	}

	c.JSON(http.StatusOK, common.Success(report))
}
