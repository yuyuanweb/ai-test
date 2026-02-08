// Package handler 提示词优化HTTP处理器
// @author <a href="https://codefather.cn">编程导航学习圈</a>
package handler

import (
	"ai-test-go/internal/model/dto"
	"ai-test-go/internal/service"
	"ai-test-go/pkg/common"
	"log"
	"net/http"
	"strings"

	"github.com/gin-gonic/gin"
)

type PromptOptimizationHandler struct {
	promptOptimizationService *service.PromptOptimizationService
}

func NewPromptOptimizationHandler(promptOptimizationService *service.PromptOptimizationService) *PromptOptimizationHandler {
	return &PromptOptimizationHandler{
		promptOptimizationService: promptOptimizationService,
	}
}

// OptimizePrompt 分析并优化提示词
// @Summary      分析并优化提示词
// @Description  使用AI分析提示词并提供优化建议
// @Tags         提示词优化
// @Accept       json
// @Produce      json
// @Param        request  body      dto.PromptOptimizationRequest  true  "优化请求"
// @Success      200      {object}  common.BaseResponse{data=vo.PromptOptimizationVO}  "优化结果"
// @Router       /prompt/optimization/analyze [post]
func (h *PromptOptimizationHandler) OptimizePrompt(c *gin.Context) {
	var req dto.PromptOptimizationRequest
	if err := c.ShouldBindJSON(&req); err != nil {
		log.Printf("参数绑定失败: %v", err)
		c.JSON(http.StatusOK, common.Error(common.PARAMS_ERROR, "参数错误"))
		return
	}

	if req.OriginalPrompt == "" {
		c.JSON(http.StatusOK, common.Error(common.PARAMS_ERROR, "原始提示词不能为空"))
		return
	}

	userID, exists := c.Get("userID")
	if !exists {
		c.JSON(http.StatusOK, common.ErrorWithDefaultMsg(common.NOT_LOGIN_ERROR))
		return
	}

	log.Printf("提示词优化请求: user=%d, promptLen=%d, hasResponse=%v",
		userID.(int64), len(req.OriginalPrompt), strings.TrimSpace(req.AiResponse) != "")

	result, err := h.promptOptimizationService.OptimizePrompt(
		req.OriginalPrompt,
		req.AiResponse,
		req.EvaluationModel,
		userID.(int64),
	)
	if err != nil {
		if bizErr, ok := err.(*common.BusinessException); ok {
			c.JSON(http.StatusOK, common.Error(bizErr.Code, bizErr.Message))
		} else {
			log.Printf("提示词优化失败: %v", err)
			c.JSON(http.StatusOK, common.Error(common.SYSTEM_ERROR, "系统内部异常"))
		}
		return
	}

	c.JSON(http.StatusOK, common.Success(result))
}
