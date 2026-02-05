// Package handler 对话HTTP处理器
// @author <a href="https://codefather.cn">编程导航学习圈</a>
package handler

import (
	"ai-test-go/internal/model/dto"
	"ai-test-go/internal/model/vo"
	"ai-test-go/internal/service"
	"ai-test-go/pkg/common"
	"encoding/json"
	"fmt"
	"log"
	"net/http"
	"strconv"

	"github.com/gin-gonic/gin"
)

type ConversationHandler struct {
	conversationService *service.ConversationService
}

func NewConversationHandler(conversationService *service.ConversationService) *ConversationHandler {
	return &ConversationHandler{
		conversationService: conversationService,
	}
}

// CreateConversation 创建对话
// @Summary      创建对话
// @Description  创建新的对话记录
// @Tags         对话接口
// @Accept       json
// @Produce      json
// @Param        request  body      dto.CreateConversationRequest  true  "创建对话请求"
// @Success      200      {object}  common.BaseResponse{data=string}  "成功"
// @Router       /conversation/create [post]
func (h *ConversationHandler) CreateConversation(c *gin.Context) {
	userID, exists := c.Get("userID")
	if !exists {
		c.JSON(http.StatusOK, common.Error(common.NOT_LOGIN_ERROR, "未登录"))
		return
	}

	var req dto.CreateConversationRequest
	if err := c.ShouldBindJSON(&req); err != nil {
		log.Printf("参数绑定失败: %v", err)
		c.JSON(http.StatusOK, common.Error(common.PARAMS_ERROR, "参数错误"))
		return
	}

	conversationID, err := h.conversationService.CreateConversation(&req, userID.(int64))
	if err != nil {
		if bizErr, ok := err.(*common.BusinessException); ok {
			c.JSON(http.StatusOK, common.Error(bizErr.Code, bizErr.Message))
		} else {
			log.Printf("创建对话失败: %v", err)
			c.JSON(http.StatusOK, common.Error(common.SYSTEM_ERROR, "系统内部异常"))
		}
		return
	}

	c.JSON(http.StatusOK, common.Success(conversationID))
}

// ChatStream 基础对话（流式响应）
// @Summary      基础对话
// @Description  单模型对话（流式）
// @Tags         对话接口
// @Accept       json
// @Produce      text/event-stream
// @Param        request  body      dto.ChatRequest  true  "对话请求"
// @Success      200      {object}  vo.StreamChunkVO  "成功"
// @Router       /conversation/chat/stream [post]
func (h *ConversationHandler) ChatStream(c *gin.Context) {
	userID, exists := c.Get("userID")
	if !exists {
		c.JSON(http.StatusOK, common.Error(common.NOT_LOGIN_ERROR, "未登录"))
		return
	}

	var req dto.ChatRequest
	if err := c.ShouldBindJSON(&req); err != nil {
		log.Printf("参数绑定失败: %v", err)
		c.JSON(http.StatusOK, common.Error(common.PARAMS_ERROR, "参数错误"))
		return
	}

	log.Printf("Chat stream request: user=%d, model=%s", userID, req.Model)

	c.Header("Content-Type", "text/event-stream")
	c.Header("Cache-Control", "no-cache")
	c.Header("Connection", "keep-alive")
	c.Header("X-Accel-Buffering", "no")

	err := h.conversationService.ChatStream(&req, userID.(int64), func(chunk vo.StreamChunkVO) error {
		data, err := json.Marshal(chunk)
		if err != nil {
			return err
		}

		_, err = fmt.Fprintf(c.Writer, "data: %s\n\n", data)
		if err != nil {
			return err
		}
		c.Writer.Flush()
		return nil
	})

	if err != nil {
		log.Printf("对话流式调用失败: %v", err)
	}
}

// SideBySideStream Side-by-Side多模型并排对比（流式响应）
// @Summary      Side-by-Side多模型并排对比
// @Description  多模型并行对比（流式）
// @Tags         对话接口
// @Accept       json
// @Produce      text/event-stream
// @Param        request  body      dto.SideBySideRequest  true  "对比请求"
// @Success      200      {object}  vo.StreamChunkVO  "成功"
// @Router       /conversation/side-by-side/stream [post]
func (h *ConversationHandler) SideBySideStream(c *gin.Context) {
	userID, exists := c.Get("userID")
	if !exists {
		c.JSON(http.StatusOK, common.Error(common.NOT_LOGIN_ERROR, "未登录"))
		return
	}

	var req dto.SideBySideRequest
	if err := c.ShouldBindJSON(&req); err != nil {
		log.Printf("参数绑定失败: %v", err)
		c.JSON(http.StatusOK, common.Error(common.PARAMS_ERROR, "参数错误"))
		return
	}

	log.Printf("Side-by-Side stream request: user=%d, models=%v", userID, req.Models)

	c.Header("Content-Type", "text/event-stream")
	c.Header("Cache-Control", "no-cache")
	c.Header("Connection", "keep-alive")
	c.Header("X-Accel-Buffering", "no")

	err := h.conversationService.SideBySideStream(&req, userID.(int64), func(chunk vo.StreamChunkVO) error {
		data, err := json.Marshal(chunk)
		if err != nil {
			return err
		}

		_, err = fmt.Fprintf(c.Writer, "data: %s\n\n", data)
		if err != nil {
			return err
		}
		c.Writer.Flush()
		return nil
	})

	if err != nil {
		log.Printf("Side-by-Side流式调用失败: %v", err)
	}
}

// PromptLabStream Prompt Lab单模型多提示词对比（流式响应）
// @Summary      Prompt Lab单模型多提示词对比
// @Description  单模型多提示词对比（流式）
// @Tags         对话接口
// @Accept       json
// @Produce      text/event-stream
// @Param        request  body      dto.PromptLabRequest  true  "对比请求"
// @Success      200      {object}  vo.StreamChunkVO  "成功"
// @Router       /conversation/prompt-lab/stream [post]
func (h *ConversationHandler) PromptLabStream(c *gin.Context) {
	userID, exists := c.Get("userID")
	if !exists {
		c.JSON(http.StatusOK, common.Error(common.NOT_LOGIN_ERROR, "未登录"))
		return
	}

	var req dto.PromptLabRequest
	if err := c.ShouldBindJSON(&req); err != nil {
		log.Printf("参数绑定失败: %v", err)
		c.JSON(http.StatusOK, common.Error(common.PARAMS_ERROR, "参数错误"))
		return
	}

	log.Printf("Prompt Lab stream request: user=%d, model=%s, variants=%d", userID, req.Model, len(req.PromptVariants))

	c.Header("Content-Type", "text/event-stream")
	c.Header("Cache-Control", "no-cache")
	c.Header("Connection", "keep-alive")
	c.Header("X-Accel-Buffering", "no")

	err := h.conversationService.PromptLabStream(&req, userID.(int64), func(chunk vo.StreamChunkVO) error {
		data, err := json.Marshal(chunk)
		if err != nil {
			return err
		}

		_, err = fmt.Fprintf(c.Writer, "data: %s\n\n", data)
		if err != nil {
			return err
		}
		c.Writer.Flush()
		return nil
	})

	if err != nil {
		log.Printf("Prompt Lab流式调用失败: %v", err)
	}
}

// GetConversation 获取对话详情
// @Summary      获取对话详情
// @Description  获取对话详情
// @Tags         对话接口
// @Accept       json
// @Produce      json
// @Param        conversationId  query     string  true  "对话ID"
// @Success      200             {object}  common.BaseResponse{data=model.Conversation}  "成功"
// @Router       /conversation/get [get]
func (h *ConversationHandler) GetConversation(c *gin.Context) {
	userID, exists := c.Get("userID")
	if !exists {
		c.JSON(http.StatusOK, common.Error(common.NOT_LOGIN_ERROR, "未登录"))
		return
	}

	conversationID := c.Query("conversationId")
	if conversationID == "" {
		c.JSON(http.StatusOK, common.Error(common.PARAMS_ERROR, "对话ID不能为空"))
		return
	}

	conversation, err := h.conversationService.GetConversation(conversationID, userID.(int64))
	if err != nil {
		if bizErr, ok := err.(*common.BusinessException); ok {
			c.JSON(http.StatusOK, common.Error(bizErr.Code, bizErr.Message))
		} else {
			log.Printf("获取对话失败: %v", err)
			c.JSON(http.StatusOK, common.Error(common.SYSTEM_ERROR, "系统内部异常"))
		}
		return
	}

	c.JSON(http.StatusOK, common.Success(conversation))
}

// ListConversations 获取对话列表（分页）
// @Summary      获取对话列表
// @Description  分页获取用户的对话列表
// @Tags         对话接口
// @Accept       json
// @Produce      json
// @Param        pageNum             query     int   false  "页码" default(1)
// @Param        pageSize            query     int   false  "每页大小" default(10)
// @Param        codePreviewEnabled  query     int   false  "是否启用代码预览"
// @Success      200                 {object}  common.BaseResponse{data=dto.PageResponse}  "成功"
// @Router       /conversation/list [get]
func (h *ConversationHandler) ListConversations(c *gin.Context) {
	userID, exists := c.Get("userID")
	if !exists {
		c.JSON(http.StatusOK, common.Error(common.NOT_LOGIN_ERROR, "未登录"))
		return
	}

	pageNum, _ := strconv.ParseInt(c.DefaultQuery("pageNum", "1"), 10, 64)
	pageSize, _ := strconv.ParseInt(c.DefaultQuery("pageSize", "10"), 10, 64)

	var codePreviewEnabled *bool
	if codePreviewStr := c.Query("codePreviewEnabled"); codePreviewStr != "" {
		val, _ := strconv.Atoi(codePreviewStr)
		enabled := val == 1
		codePreviewEnabled = &enabled
	}

	conversations, total, err := h.conversationService.ListConversations(userID.(int64), pageNum, pageSize, codePreviewEnabled)
	if err != nil {
		if bizErr, ok := err.(*common.BusinessException); ok {
			c.JSON(http.StatusOK, common.Error(bizErr.Code, bizErr.Message))
		} else {
			log.Printf("查询对话列表失败: %v", err)
			c.JSON(http.StatusOK, common.Error(common.SYSTEM_ERROR, "系统内部异常"))
		}
		return
	}

	pageResp := dto.PageResponse{
		Total:    total,
		PageNum:  pageNum,
		PageSize: pageSize,
		Records:  conversations,
	}

	c.JSON(http.StatusOK, common.Success(pageResp))
}

// GetConversationMessages 获取对话的所有消息
// @Summary      获取对话消息
// @Description  获取对话的所有消息记录
// @Tags         对话接口
// @Accept       json
// @Produce      json
// @Param        conversationId  query     string  true  "对话ID"
// @Success      200             {object}  common.BaseResponse{data=[]model.ConversationMessage}  "成功"
// @Router       /conversation/messages [get]
func (h *ConversationHandler) GetConversationMessages(c *gin.Context) {
	userID, exists := c.Get("userID")
	if !exists {
		c.JSON(http.StatusOK, common.Error(common.NOT_LOGIN_ERROR, "未登录"))
		return
	}

	conversationID := c.Query("conversationId")
	if conversationID == "" {
		c.JSON(http.StatusOK, common.Error(common.PARAMS_ERROR, "对话ID不能为空"))
		return
	}

	messages, err := h.conversationService.GetConversationMessages(conversationID, userID.(int64))
	if err != nil {
		if bizErr, ok := err.(*common.BusinessException); ok {
			c.JSON(http.StatusOK, common.Error(bizErr.Code, bizErr.Message))
		} else {
			log.Printf("获取对话消息失败: %v", err)
			c.JSON(http.StatusOK, common.Error(common.SYSTEM_ERROR, "系统内部异常"))
		}
		return
	}

	c.JSON(http.StatusOK, common.Success(messages))
}

// DeleteConversation 删除对话
// @Summary      删除对话
// @Description  删除对话记录
// @Tags         对话接口
// @Accept       json
// @Produce      json
// @Param        request  body      dto.DeleteConversationRequest  true  "删除请求"
// @Success      200      {object}  common.BaseResponse{data=bool}  "成功"
// @Router       /conversation/delete [post]
func (h *ConversationHandler) DeleteConversation(c *gin.Context) {
	userID, exists := c.Get("userID")
	if !exists {
		c.JSON(http.StatusOK, common.Error(common.NOT_LOGIN_ERROR, "未登录"))
		return
	}

	var req dto.DeleteConversationRequest
	if err := c.ShouldBindJSON(&req); err != nil {
		log.Printf("参数绑定失败: %v", err)
		c.JSON(http.StatusOK, common.Error(common.PARAMS_ERROR, "参数错误"))
		return
	}

	if err := h.conversationService.DeleteConversation(req.ID, userID.(int64)); err != nil {
		if bizErr, ok := err.(*common.BusinessException); ok {
			c.JSON(http.StatusOK, common.Error(bizErr.Code, bizErr.Message))
		} else {
			log.Printf("删除对话失败: %v", err)
			c.JSON(http.StatusOK, common.Error(common.SYSTEM_ERROR, "系统内部异常"))
		}
		return
	}

	c.JSON(http.StatusOK, common.Success(true))
}

// CodeModeStream 代码模式流式对话
// @Summary      代码模式流式对话
// @Description  代码模式流式对话，生成HTML代码并支持预览
// @Tags         对话接口
// @Accept       json
// @Produce      text/event-stream
// @Param        request  body      dto.CodeModeRequest  true  "代码模式请求"
// @Success      200      {object}  vo.StreamChunkVO     "流式响应"
// @Router       /conversation/code-mode/stream [post]
func (h *ConversationHandler) CodeModeStream(c *gin.Context) {
	userID, exists := c.Get("userID")
	if !exists {
		c.JSON(http.StatusOK, common.Error(common.NOT_LOGIN_ERROR, "未登录"))
		return
	}

	var req dto.CodeModeRequest
	if err := c.ShouldBindJSON(&req); err != nil {
		log.Printf("参数绑定失败: %v", err)
		c.JSON(http.StatusOK, common.Error(common.PARAMS_ERROR, "参数错误"))
		return
	}

	c.Header("Content-Type", "text/event-stream")
	c.Header("Cache-Control", "no-cache")
	c.Header("Connection", "keep-alive")
	c.Header("X-Accel-Buffering", "no")

	flusher, ok := c.Writer.(http.Flusher)
	if !ok {
		log.Println("不支持流式响应")
		c.JSON(http.StatusOK, common.Error(common.SYSTEM_ERROR, "不支持流式响应"))
		return
	}

	err := h.conversationService.CodeModeStream(&req, userID.(int64), func(chunk vo.StreamChunkVO) error {
		data, _ := json.Marshal(chunk)
		fmt.Fprintf(c.Writer, "data: %s\n\n", data)
		flusher.Flush()
		return nil
	})

	if err != nil {
		if bizErr, ok := err.(*common.BusinessException); ok {
			errorData, _ := json.Marshal(map[string]interface{}{
				"hasError": true,
				"error":    bizErr.Message,
				"done":     true,
			})
			fmt.Fprintf(c.Writer, "data: %s\n\n", errorData)
			flusher.Flush()
		} else {
			log.Printf("代码模式流式调用失败: %v", err)
			errorData, _ := json.Marshal(map[string]interface{}{
				"hasError": true,
				"error":    "系统内部异常",
				"done":     true,
			})
			fmt.Fprintf(c.Writer, "data: %s\n\n", errorData)
			flusher.Flush()
		}
	}
}

// CodeModePromptLabStream 代码模式提示词实验室流式对话
// @Summary      代码模式提示词实验室流式对话
// @Description  代码模式提示词实验室流式对话，测试不同提示词生成HTML代码的效果
// @Tags         对话接口
// @Accept       json
// @Produce      text/event-stream
// @Param        request  body      dto.CodeModePromptLabRequest  true  "代码模式提示词实验请求"
// @Success      200      {object}  vo.StreamChunkVO              "流式响应"
// @Router       /conversation/code-mode/prompt-lab/stream [post]
func (h *ConversationHandler) CodeModePromptLabStream(c *gin.Context) {
	userID, exists := c.Get("userID")
	if !exists {
		c.JSON(http.StatusOK, common.Error(common.NOT_LOGIN_ERROR, "未登录"))
		return
	}

	var req dto.CodeModePromptLabRequest
	if err := c.ShouldBindJSON(&req); err != nil {
		log.Printf("参数绑定失败: %v", err)
		c.JSON(http.StatusOK, common.Error(common.PARAMS_ERROR, "参数错误"))
		return
	}

	c.Header("Content-Type", "text/event-stream")
	c.Header("Cache-Control", "no-cache")
	c.Header("Connection", "keep-alive")
	c.Header("X-Accel-Buffering", "no")

	flusher, ok := c.Writer.(http.Flusher)
	if !ok {
		log.Println("不支持流式响应")
		c.JSON(http.StatusOK, common.Error(common.SYSTEM_ERROR, "不支持流式响应"))
		return
	}

	err := h.conversationService.CodeModePromptLabStream(&req, userID.(int64), func(chunk vo.StreamChunkVO) error {
		data, _ := json.Marshal(chunk)
		fmt.Fprintf(c.Writer, "data: %s\n\n", data)
		flusher.Flush()
		return nil
	})

	if err != nil {
		if bizErr, ok := err.(*common.BusinessException); ok {
			errorData, _ := json.Marshal(map[string]interface{}{
				"hasError": true,
				"error":    bizErr.Message,
				"done":     true,
			})
			fmt.Fprintf(c.Writer, "data: %s\n\n", errorData)
			flusher.Flush()
		} else {
			log.Printf("代码模式提示词实验室流式调用失败: %v", err)
			errorData, _ := json.Marshal(map[string]interface{}{
				"hasError": true,
				"error":    "系统内部异常",
				"done":     true,
			})
			fmt.Fprintf(c.Writer, "data: %s\n\n", errorData)
			flusher.Flush()
		}
	}
}
