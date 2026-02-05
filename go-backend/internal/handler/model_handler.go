// Package handler 模型HTTP处理器
// @author <a href="https://codefather.cn">编程导航学习圈</a>
package handler

import (
	"ai-test-go/internal/model/dto"
	"ai-test-go/internal/service"
	"ai-test-go/pkg/common"
	"log"
	"net/http"

	"github.com/gin-gonic/gin"
)

type ModelHandler struct {
	modelService *service.ModelService
}

func NewModelHandler(modelService *service.ModelService) *ModelHandler {
	return &ModelHandler{
		modelService: modelService,
	}
}

// ListModels 分页查询模型列表
// @Summary      分页查询模型列表
// @Description  分页查询模型列表（支持搜索和筛选）
// @Tags         模型接口
// @Accept       json
// @Produce      json
// @Param        request  body      dto.ModelQueryRequest  true  "查询请求"
// @Success      200      {object}  common.BaseResponse{data=dto.PageResponse}  "成功"
// @Router       /model/list [post]
func (h *ModelHandler) ListModels(c *gin.Context) {
	var req dto.ModelQueryRequest
	if err := c.ShouldBindJSON(&req); err != nil {
		log.Printf("参数绑定失败: %v", err)
		c.JSON(http.StatusOK, common.Error(common.PARAMS_ERROR, "参数错误"))
		return
	}

	var userID *int64
	if uid, exists := c.Get("userID"); exists {
		id := uid.(int64)
		userID = &id
	}

	page, err := h.modelService.ListModels(&req, userID)
	if err != nil {
		if bizErr, ok := err.(*common.BusinessException); ok {
			c.JSON(http.StatusOK, common.Error(bizErr.Code, bizErr.Message))
		} else {
			log.Printf("查询模型列表失败: %v", err)
			c.JSON(http.StatusOK, common.Error(common.SYSTEM_ERROR, "系统内部异常"))
		}
		return
	}

	c.JSON(http.StatusOK, common.Success(page))
}

// GetAllModels 获取所有模型列表
// @Summary      获取所有模型列表
// @Description  获取所有模型列表（国内优先）
// @Tags         模型接口
// @Accept       json
// @Produce      json
// @Success      200  {object}  common.BaseResponse{data=[]vo.ModelVO}  "成功"
// @Router       /model/all [get]
func (h *ModelHandler) GetAllModels(c *gin.Context) {
	var userID *int64
	if uid, exists := c.Get("userID"); exists {
		id := uid.(int64)
		userID = &id
	}

	models, err := h.modelService.GetAllModels(userID)
	if err != nil {
		if bizErr, ok := err.(*common.BusinessException); ok {
			c.JSON(http.StatusOK, common.Error(bizErr.Code, bizErr.Message))
		} else {
			log.Printf("查询所有模型失败: %v", err)
			c.JSON(http.StatusOK, common.Error(common.SYSTEM_ERROR, "系统内部异常"))
		}
		return
	}

	c.JSON(http.StatusOK, common.Success(models))
}
