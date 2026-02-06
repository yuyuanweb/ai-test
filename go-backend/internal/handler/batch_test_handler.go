// Package handler 批量测试HTTP处理器
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

type BatchTestHandler struct {
	batchTestService *service.BatchTestService
	userService      *service.UserService
}

func NewBatchTestHandler(batchTestService *service.BatchTestService, userService *service.UserService) *BatchTestHandler {
	return &BatchTestHandler{
		batchTestService: batchTestService,
		userService:      userService,
	}
}

// CreateBatchTestTask 创建批量测试任务
// @Summary      创建批量测试任务
// @Description  创建批量测试任务接口
// @Tags         批量测试
// @Accept       json
// @Produce      json
// @Param        request  body      dto.CreateBatchTestRequest  true  "创建批量测试任务请求"
// @Success      200      {object}  common.BaseResponse{data=string}  "创建成功，返回任务ID"
// @Failure      400      {object}  common.BaseResponse  "参数错误"
// @Router       /batch-test/create [post]
func (h *BatchTestHandler) CreateBatchTestTask(c *gin.Context) {
	var req dto.CreateBatchTestRequest
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

	taskID, err := h.batchTestService.CreateBatchTestTask(&req, userID.(int64))
	if err != nil {
		if bizErr, ok := err.(*common.BusinessException); ok {
			c.JSON(http.StatusOK, common.Error(bizErr.Code, bizErr.Message))
		} else {
			log.Printf("创建批量测试任务失败: %v", err)
			c.JSON(http.StatusOK, common.Error(common.SYSTEM_ERROR, "系统内部异常"))
		}
		return
	}

	c.JSON(http.StatusOK, common.Success(taskID))
}

// GetTask 获取任务详情
// @Summary      获取任务详情
// @Description  获取任务详情接口
// @Tags         批量测试
// @Accept       json
// @Produce      json
// @Param        taskId  query     string  true  "任务ID"
// @Success      200     {object}  common.BaseResponse{data=model.TestTask}  "获取成功"
// @Failure      400     {object}  common.BaseResponse  "参数错误"
// @Router       /batch-test/task/get [get]
func (h *BatchTestHandler) GetTask(c *gin.Context) {
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

	task, err := h.batchTestService.GetTask(taskID, userID.(int64))
	if err != nil {
		if bizErr, ok := err.(*common.BusinessException); ok {
			c.JSON(http.StatusOK, common.Error(bizErr.Code, bizErr.Message))
		} else {
			log.Printf("获取任务失败: %v", err)
			c.JSON(http.StatusOK, common.Error(common.SYSTEM_ERROR, "系统内部异常"))
		}
		return
	}

	c.JSON(http.StatusOK, common.Success(task))
}

// ListTasks 分页查询任务列表
// @Summary      分页查询任务列表
// @Description  分页查询任务列表接口
// @Tags         批量测试
// @Accept       json
// @Produce      json
// @Param        request  body      dto.TaskQueryRequest  true  "查询请求"
// @Success      200      {object}  common.BaseResponse{data=dto.PageResponse}  "获取成功"
// @Failure      400      {object}  common.BaseResponse  "参数错误"
// @Router       /batch-test/task/list/page [post]
func (h *BatchTestHandler) ListTasks(c *gin.Context) {
	var req dto.TaskQueryRequest
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

	tasks, total, err := h.batchTestService.ListTasks(userID.(int64), &req)
	if err != nil {
		if bizErr, ok := err.(*common.BusinessException); ok {
			c.JSON(http.StatusOK, common.Error(bizErr.Code, bizErr.Message))
		} else {
			log.Printf("获取任务列表失败: %v", err)
			c.JSON(http.StatusOK, common.Error(common.SYSTEM_ERROR, "系统内部异常"))
		}
		return
	}

	pageResponse := dto.NewPageResponse(tasks, total, req.PageNum, req.PageSize)
	c.JSON(http.StatusOK, common.Success(pageResponse))
}

// DeleteTask 删除任务
// @Summary      删除任务
// @Description  删除任务接口
// @Tags         批量测试
// @Accept       json
// @Produce      json
// @Param        request  body      dto.DeleteRequest  true  "删除请求"
// @Success      200      {object}  common.BaseResponse{data=bool}  "删除成功"
// @Failure      400      {object}  common.BaseResponse  "参数错误"
// @Router       /batch-test/task/delete [post]
func (h *BatchTestHandler) DeleteTask(c *gin.Context) {
	var req dto.DeleteRequest
	if err := c.ShouldBindJSON(&req); err != nil {
		log.Printf("参数绑定失败: %v", err)
		c.JSON(http.StatusOK, common.Error(common.PARAMS_ERROR, "参数错误"))
		return
	}

	if req.ID == "" {
		c.JSON(http.StatusOK, common.Error(common.PARAMS_ERROR, "任务ID不能为空"))
		return
	}

	userID, exists := c.Get("userID")
	if !exists {
		c.JSON(http.StatusOK, common.ErrorWithDefaultMsg(common.NOT_LOGIN_ERROR))
		return
	}

	err := h.batchTestService.DeleteTask(req.ID, userID.(int64))
	if err != nil {
		if bizErr, ok := err.(*common.BusinessException); ok {
			c.JSON(http.StatusOK, common.Error(bizErr.Code, bizErr.Message))
		} else {
			log.Printf("删除任务失败: %v", err)
			c.JSON(http.StatusOK, common.Error(common.SYSTEM_ERROR, "系统内部异常"))
		}
		return
	}

	c.JSON(http.StatusOK, common.Success(true))
}

// GetTaskResults 获取任务的测试结果
// @Summary      获取任务的测试结果
// @Description  获取任务的测试结果列表
// @Tags         批量测试
// @Accept       json
// @Produce      json
// @Param        taskId  query     string  true  "任务ID"
// @Success      200     {object}  common.BaseResponse{data=[]model.TestResult}  "获取成功"
// @Failure      400     {object}  common.BaseResponse  "参数错误"
// @Router       /batch-test/result/list [get]
func (h *BatchTestHandler) GetTaskResults(c *gin.Context) {
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

	results, err := h.batchTestService.GetTaskResults(taskID, userID.(int64))
	if err != nil {
		if bizErr, ok := err.(*common.BusinessException); ok {
			c.JSON(http.StatusOK, common.Error(bizErr.Code, bizErr.Message))
		} else {
			log.Printf("获取测试结果失败: %v", err)
			c.JSON(http.StatusOK, common.Error(common.SYSTEM_ERROR, "系统内部异常"))
		}
		return
	}

	c.JSON(http.StatusOK, common.Success(results))
}

// UpdateTestResultRating 更新测试结果评分
// @Summary      更新测试结果评分
// @Description  更新测试结果评分接口
// @Tags         批量测试
// @Accept       json
// @Produce      json
// @Param        request  body      dto.UpdateTestResultRatingRequest  true  "更新评分请求"
// @Success      200      {object}  common.BaseResponse{data=bool}  "更新成功"
// @Failure      400      {object}  common.BaseResponse  "参数错误"
// @Router       /batch-test/result/rating [post]
func (h *BatchTestHandler) UpdateTestResultRating(c *gin.Context) {
	var req dto.UpdateTestResultRatingRequest
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

	err := h.batchTestService.UpdateTestResultRating(req.ResultID, req.UserRating, userID.(int64))
	if err != nil {
		if bizErr, ok := err.(*common.BusinessException); ok {
			c.JSON(http.StatusOK, common.Error(bizErr.Code, bizErr.Message))
		} else {
			log.Printf("更新评分失败: %v", err)
			c.JSON(http.StatusOK, common.Error(common.SYSTEM_ERROR, "系统内部异常"))
		}
		return
	}

	c.JSON(http.StatusOK, common.Success(true))
}
