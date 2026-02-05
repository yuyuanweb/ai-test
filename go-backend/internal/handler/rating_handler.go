// Package handler 评分HTTP处理器
// @author <a href="https://codefather.cn">编程导航学习圈</a>
package handler

import (
	"ai-test-go/internal/model/dto"
	"ai-test-go/internal/service"
	"ai-test-go/pkg/common"
	"log"
	"net/http"
	"strconv"

	"github.com/gin-gonic/gin"
)

type RatingHandler struct {
	ratingService *service.RatingService
}

func NewRatingHandler(ratingService *service.RatingService) *RatingHandler {
	return &RatingHandler{
		ratingService: ratingService,
	}
}

// AddRating 添加或更新评分
// @Summary      添加或更新评分
// @Description  添加或更新用户对某轮对话的评分
// @Tags         评分接口
// @Accept       json
// @Produce      json
// @Param        request  body      dto.RatingAddRequest  true  "评分请求"
// @Success      200      {object}  common.BaseResponse{data=bool}  "成功"
// @Router       /rating/add [post]
func (h *RatingHandler) AddRating(c *gin.Context) {
	userID, exists := c.Get("userID")
	if !exists {
		c.JSON(http.StatusOK, common.Error(common.NOT_LOGIN_ERROR, "未登录"))
		return
	}

	var req dto.RatingAddRequest
	if err := c.ShouldBindJSON(&req); err != nil {
		log.Printf("参数绑定失败: %v", err)
		c.JSON(http.StatusOK, common.Error(common.PARAMS_ERROR, "参数错误"))
		return
	}

	if err := h.ratingService.SaveOrUpdateRating(&req, userID.(int64)); err != nil {
		if bizErr, ok := err.(*common.BusinessException); ok {
			c.JSON(http.StatusOK, common.Error(bizErr.Code, bizErr.Message))
		} else {
			log.Printf("保存评分失败: %v", err)
			c.JSON(http.StatusOK, common.Error(common.SYSTEM_ERROR, "系统内部异常"))
		}
		return
	}

	c.JSON(http.StatusOK, common.Success(true))
}

// GetRating 获取评分
// @Summary      获取评分
// @Description  获取用户对某轮对话的评分
// @Tags         评分接口
// @Accept       json
// @Produce      json
// @Param        conversationId  query     string  true  "对话ID"
// @Param        messageIndex    query     int     true  "消息序号"
// @Success      200             {object}  common.BaseResponse{data=vo.RatingVO}  "成功"
// @Router       /rating/get [get]
func (h *RatingHandler) GetRating(c *gin.Context) {
	userID, exists := c.Get("userID")
	if !exists {
		c.JSON(http.StatusOK, common.Error(common.NOT_LOGIN_ERROR, "未登录"))
		return
	}

	conversationID := c.Query("conversationId")
	messageIndexStr := c.Query("messageIndex")

	if conversationID == "" || messageIndexStr == "" {
		c.JSON(http.StatusOK, common.Error(common.PARAMS_ERROR, "参数不能为空"))
		return
	}

	messageIndex, err := strconv.Atoi(messageIndexStr)
	if err != nil {
		c.JSON(http.StatusOK, common.Error(common.PARAMS_ERROR, "消息序号格式错误"))
		return
	}

	rating, err := h.ratingService.GetRating(conversationID, messageIndex, userID.(int64))
	if err != nil {
		if bizErr, ok := err.(*common.BusinessException); ok {
			c.JSON(http.StatusOK, common.Error(bizErr.Code, bizErr.Message))
		} else {
			log.Printf("获取评分失败: %v", err)
			c.JSON(http.StatusOK, common.Error(common.SYSTEM_ERROR, "系统内部异常"))
		}
		return
	}

	c.JSON(http.StatusOK, common.Success(rating))
}

// ListRatingsByConversation 获取对话的所有评分
// @Summary      获取对话的所有评分
// @Description  获取用户对整个对话的所有评分记录
// @Tags         评分接口
// @Accept       json
// @Produce      json
// @Param        conversationId  query     string  true  "对话ID"
// @Success      200             {object}  common.BaseResponse{data=[]vo.RatingVO}  "成功"
// @Router       /rating/list [get]
func (h *RatingHandler) ListRatingsByConversation(c *gin.Context) {
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

	ratings, err := h.ratingService.GetRatingsByConversationID(conversationID, userID.(int64))
	if err != nil {
		if bizErr, ok := err.(*common.BusinessException); ok {
			c.JSON(http.StatusOK, common.Error(bizErr.Code, bizErr.Message))
		} else {
			log.Printf("获取评分列表失败: %v", err)
			c.JSON(http.StatusOK, common.Error(common.SYSTEM_ERROR, "系统内部异常"))
		}
		return
	}

	c.JSON(http.StatusOK, common.Success(ratings))
}

// DeleteRating 删除评分
// @Summary      删除评分
// @Description  删除用户对某轮对话的评分
// @Tags         评分接口
// @Accept       json
// @Produce      json
// @Param        conversationId  query     string  true  "对话ID"
// @Param        messageIndex    query     int     true  "消息序号"
// @Success      200             {object}  common.BaseResponse{data=bool}  "成功"
// @Router       /rating/delete [delete]
func (h *RatingHandler) DeleteRating(c *gin.Context) {
	userID, exists := c.Get("userID")
	if !exists {
		c.JSON(http.StatusOK, common.Error(common.NOT_LOGIN_ERROR, "未登录"))
		return
	}

	conversationID := c.Query("conversationId")
	messageIndexStr := c.Query("messageIndex")

	if conversationID == "" || messageIndexStr == "" {
		c.JSON(http.StatusOK, common.Error(common.PARAMS_ERROR, "参数不能为空"))
		return
	}

	messageIndex, err := strconv.Atoi(messageIndexStr)
	if err != nil {
		c.JSON(http.StatusOK, common.Error(common.PARAMS_ERROR, "消息序号格式错误"))
		return
	}

	if err := h.ratingService.DeleteRating(conversationID, messageIndex, userID.(int64)); err != nil {
		if bizErr, ok := err.(*common.BusinessException); ok {
			c.JSON(http.StatusOK, common.Error(bizErr.Code, bizErr.Message))
		} else {
			log.Printf("删除评分失败: %v", err)
			c.JSON(http.StatusOK, common.Error(common.SYSTEM_ERROR, "系统内部异常"))
		}
		return
	}

	c.JSON(http.StatusOK, common.Success(true))
}
