// Package handler 提示词模板HTTP处理器
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

type PromptTemplateHandler struct {
	promptTemplateService *service.PromptTemplateService
}

func NewPromptTemplateHandler(promptTemplateService *service.PromptTemplateService) *PromptTemplateHandler {
	return &PromptTemplateHandler{
		promptTemplateService: promptTemplateService,
	}
}

// ListTemplates 获取模板列表
// @Summary      获取模板列表
// @Description  获取提示词模板列表
// @Tags         提示词模板
// @Accept       json
// @Produce      json
// @Param        strategy  query     string  false  "策略类型筛选"
// @Success      200       {object}  common.BaseResponse{data=[]vo.PromptTemplateVO}  "模板列表"
// @Router       /prompt/template/list [get]
func (h *PromptTemplateHandler) ListTemplates(c *gin.Context) {
	userID, exists := c.Get("userID")
	if !exists {
		c.JSON(http.StatusOK, common.ErrorWithDefaultMsg(common.NOT_LOGIN_ERROR))
		return
	}

	strategy := strings.TrimSpace(c.Query("strategy"))

	templates, err := h.promptTemplateService.ListTemplates(userID.(int64), strategy)
	if err != nil {
		if bizErr, ok := err.(*common.BusinessException); ok {
			c.JSON(http.StatusOK, common.Error(bizErr.Code, bizErr.Message))
		} else {
			log.Printf("获取模板列表失败: %v", err)
			c.JSON(http.StatusOK, common.Error(common.SYSTEM_ERROR, "系统内部异常"))
		}
		return
	}

	c.JSON(http.StatusOK, common.Success(templates))
}

// GetTemplate 根据ID获取模板
// @Summary      根据ID获取模板
// @Description  根据模板ID获取模板详情
// @Tags         提示词模板
// @Accept       json
// @Produce      json
// @Param        templateId  query     string  true  "模板ID"
// @Success      200         {object}  common.BaseResponse{data=vo.PromptTemplateVO}  "模板详情"
// @Router       /prompt/template/get [get]
func (h *PromptTemplateHandler) GetTemplate(c *gin.Context) {
	templateID := strings.TrimSpace(c.Query("templateId"))
	if templateID == "" {
		c.JSON(http.StatusOK, common.Error(common.PARAMS_ERROR, "模板ID不能为空"))
		return
	}

	userID, exists := c.Get("userID")
	if !exists {
		c.JSON(http.StatusOK, common.ErrorWithDefaultMsg(common.NOT_LOGIN_ERROR))
		return
	}

	template, err := h.promptTemplateService.GetTemplateByID(templateID, userID.(int64))
	if err != nil {
		if bizErr, ok := err.(*common.BusinessException); ok {
			c.JSON(http.StatusOK, common.Error(bizErr.Code, bizErr.Message))
		} else {
			log.Printf("获取模板失败: %v", err)
			c.JSON(http.StatusOK, common.Error(common.SYSTEM_ERROR, "系统内部异常"))
		}
		return
	}

	c.JSON(http.StatusOK, common.Success(template))
}

// CreateTemplate 创建模板
// @Summary      创建模板
// @Description  创建提示词模板
// @Tags         提示词模板
// @Accept       json
// @Produce      json
// @Param        request  body      dto.CreatePromptTemplateRequest  true  "创建模板请求"
// @Success      200      {object}  common.BaseResponse{data=string}  "创建成功，返回模板ID"
// @Router       /prompt/template/create [post]
func (h *PromptTemplateHandler) CreateTemplate(c *gin.Context) {
	var req dto.CreatePromptTemplateRequest
	if err := c.ShouldBindJSON(&req); err != nil {
		log.Printf("参数绑定失败: %v", err)
		c.JSON(http.StatusOK, common.Error(common.PARAMS_ERROR, "参数错误"))
		return
	}

	userID, exists := c.Get("userID")
	if !exists {
		c.JSON(http.StatusOK, common.ErrorWithDefaultMsg(common.NOT_LOGIN_ERROR))
		return
	}

	templateID, err := h.promptTemplateService.CreateTemplate(&req, userID.(int64))
	if err != nil {
		if bizErr, ok := err.(*common.BusinessException); ok {
			c.JSON(http.StatusOK, common.Error(bizErr.Code, bizErr.Message))
		} else {
			log.Printf("创建模板失败: %v", err)
			c.JSON(http.StatusOK, common.Error(common.SYSTEM_ERROR, "系统内部异常"))
		}
		return
	}

	c.JSON(http.StatusOK, common.Success(templateID))
}

// UpdateTemplate 更新模板
// @Summary      更新模板
// @Description  更新提示词模板
// @Tags         提示词模板
// @Accept       json
// @Produce      json
// @Param        request  body      dto.UpdatePromptTemplateRequest  true  "更新模板请求"
// @Success      200      {object}  common.BaseResponse{data=bool}  "更新成功"
// @Router       /prompt/template/update [post]
func (h *PromptTemplateHandler) UpdateTemplate(c *gin.Context) {
	var req dto.UpdatePromptTemplateRequest
	if err := c.ShouldBindJSON(&req); err != nil {
		log.Printf("参数绑定失败: %v", err)
		c.JSON(http.StatusOK, common.Error(common.PARAMS_ERROR, "参数错误"))
		return
	}

	userID, exists := c.Get("userID")
	if !exists {
		c.JSON(http.StatusOK, common.ErrorWithDefaultMsg(common.NOT_LOGIN_ERROR))
		return
	}

	result, err := h.promptTemplateService.UpdateTemplate(&req, userID.(int64))
	if err != nil {
		if bizErr, ok := err.(*common.BusinessException); ok {
			c.JSON(http.StatusOK, common.Error(bizErr.Code, bizErr.Message))
		} else {
			log.Printf("更新模板失败: %v", err)
			c.JSON(http.StatusOK, common.Error(common.SYSTEM_ERROR, "系统内部异常"))
		}
		return
	}

	c.JSON(http.StatusOK, common.Success(result))
}

// DeleteTemplate 删除模板
// @Summary      删除模板
// @Description  删除提示词模板
// @Tags         提示词模板
// @Accept       json
// @Produce      json
// @Param        request  body      dto.DeleteRequest  true  "删除模板请求"
// @Success      200      {object}  common.BaseResponse{data=bool}  "删除成功"
// @Router       /prompt/template/delete [post]
func (h *PromptTemplateHandler) DeleteTemplate(c *gin.Context) {
	var req dto.DeleteRequest
	if err := c.ShouldBindJSON(&req); err != nil {
		log.Printf("参数绑定失败: %v", err)
		c.JSON(http.StatusOK, common.Error(common.PARAMS_ERROR, "参数错误"))
		return
	}

	if req.ID == "" {
		c.JSON(http.StatusOK, common.Error(common.PARAMS_ERROR, "模板ID不能为空"))
		return
	}

	userID, exists := c.Get("userID")
	if !exists {
		c.JSON(http.StatusOK, common.ErrorWithDefaultMsg(common.NOT_LOGIN_ERROR))
		return
	}

	result, err := h.promptTemplateService.DeleteTemplate(req.ID, userID.(int64))
	if err != nil {
		if bizErr, ok := err.(*common.BusinessException); ok {
			c.JSON(http.StatusOK, common.Error(bizErr.Code, bizErr.Message))
		} else {
			log.Printf("删除模板失败: %v", err)
			c.JSON(http.StatusOK, common.Error(common.SYSTEM_ERROR, "系统内部异常"))
		}
		return
	}

	c.JSON(http.StatusOK, common.Success(result))
}

// IncrementUsage 增加使用次数
// @Summary      增加使用次数
// @Description  增加模板使用次数
// @Tags         提示词模板
// @Accept       json
// @Produce      json
// @Param        templateId  query     string  true  "模板ID"
// @Success      200         {object}  common.BaseResponse{data=bool}  "操作成功"
// @Router       /prompt/template/increment-usage [post]
func (h *PromptTemplateHandler) IncrementUsage(c *gin.Context) {
	templateID := strings.TrimSpace(c.Query("templateId"))
	if templateID == "" {
		c.JSON(http.StatusOK, common.Error(common.PARAMS_ERROR, "模板ID不能为空"))
		return
	}

	result, err := h.promptTemplateService.IncrementUsageCount(templateID)
	if err != nil {
		if bizErr, ok := err.(*common.BusinessException); ok {
			c.JSON(http.StatusOK, common.Error(bizErr.Code, bizErr.Message))
		} else {
			c.JSON(http.StatusOK, common.Success(false))
			return
		}
		return
	}

	c.JSON(http.StatusOK, common.Success(result))
}
