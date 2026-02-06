// Package handler 场景HTTP处理器
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

type SceneHandler struct {
	sceneService *service.SceneService
	userService  *service.UserService
}

func NewSceneHandler(sceneService *service.SceneService, userService *service.UserService) *SceneHandler {
	return &SceneHandler{
		sceneService: sceneService,
		userService:  userService,
	}
}

// CreateScene 创建场景
// @Summary      创建场景
// @Description  创建场景接口
// @Tags         场景管理
// @Accept       json
// @Produce      json
// @Param        request  body      dto.CreateSceneRequest  true  "创建场景请求"
// @Success      200      {object}  common.BaseResponse{data=string}  "创建成功，返回场景ID"
// @Failure      400      {object}  common.BaseResponse  "参数错误"
// @Router       /scene/create [post]
func (h *SceneHandler) CreateScene(c *gin.Context) {
	var req dto.CreateSceneRequest
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

	sceneID, err := h.sceneService.CreateScene(&req, userID.(int64))
	if err != nil {
		if bizErr, ok := err.(*common.BusinessException); ok {
			c.JSON(http.StatusOK, common.Error(bizErr.Code, bizErr.Message))
		} else {
			log.Printf("创建场景失败: %v", err)
			c.JSON(http.StatusOK, common.Error(common.SYSTEM_ERROR, "系统内部异常"))
		}
		return
	}

	c.JSON(http.StatusOK, common.Success(sceneID))
}

// UpdateScene 更新场景
// @Summary      更新场景
// @Description  更新场景接口
// @Tags         场景管理
// @Accept       json
// @Produce      json
// @Param        request  body      dto.UpdateSceneRequest  true  "更新场景请求"
// @Success      200      {object}  common.BaseResponse{data=bool}  "更新成功"
// @Failure      400      {object}  common.BaseResponse  "参数错误"
// @Router       /scene/update [post]
func (h *SceneHandler) UpdateScene(c *gin.Context) {
	var req dto.UpdateSceneRequest
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

	err := h.sceneService.UpdateScene(&req, userID.(int64))
	if err != nil {
		if bizErr, ok := err.(*common.BusinessException); ok {
			c.JSON(http.StatusOK, common.Error(bizErr.Code, bizErr.Message))
		} else {
			log.Printf("更新场景失败: %v", err)
			c.JSON(http.StatusOK, common.Error(common.SYSTEM_ERROR, "系统内部异常"))
		}
		return
	}

	c.JSON(http.StatusOK, common.Success(true))
}

// DeleteScene 删除场景
// @Summary      删除场景
// @Description  删除场景接口
// @Tags         场景管理
// @Accept       json
// @Produce      json
// @Param        request  body      dto.DeleteRequest  true  "删除场景请求"
// @Success      200      {object}  common.BaseResponse{data=bool}  "删除成功"
// @Failure      400      {object}  common.BaseResponse  "参数错误"
// @Router       /scene/delete [post]
func (h *SceneHandler) DeleteScene(c *gin.Context) {
	var req dto.DeleteRequest
	if err := c.ShouldBindJSON(&req); err != nil {
		log.Printf("参数绑定失败: %v", err)
		c.JSON(http.StatusOK, common.Error(common.PARAMS_ERROR, "参数错误"))
		return
	}

	if req.ID == "" {
		c.JSON(http.StatusOK, common.Error(common.PARAMS_ERROR, "场景ID不能为空"))
		return
	}

	userID, exists := c.Get("userID")
	if !exists {
		c.JSON(http.StatusOK, common.ErrorWithDefaultMsg(common.NOT_LOGIN_ERROR))
		return
	}

	err := h.sceneService.DeleteScene(req.ID, userID.(int64))
	if err != nil {
		if bizErr, ok := err.(*common.BusinessException); ok {
			c.JSON(http.StatusOK, common.Error(bizErr.Code, bizErr.Message))
		} else {
			log.Printf("删除场景失败: %v", err)
			c.JSON(http.StatusOK, common.Error(common.SYSTEM_ERROR, "系统内部异常"))
		}
		return
	}

	c.JSON(http.StatusOK, common.Success(true))
}

// GetScene 获取场景详情
// @Summary      获取场景详情
// @Description  获取场景详情接口
// @Tags         场景管理
// @Accept       json
// @Produce      json
// @Param        id  query     string  true  "场景ID"
// @Success      200 {object}  common.BaseResponse{data=model.Scene}  "获取成功"
// @Failure      400 {object}  common.BaseResponse  "参数错误"
// @Router       /scene/get [get]
func (h *SceneHandler) GetScene(c *gin.Context) {
	sceneID := c.Query("id")
	if sceneID == "" {
		c.JSON(http.StatusOK, common.Error(common.PARAMS_ERROR, "场景ID不能为空"))
		return
	}

	userID, exists := c.Get("userID")
	if !exists {
		c.JSON(http.StatusOK, common.ErrorWithDefaultMsg(common.NOT_LOGIN_ERROR))
		return
	}

	scene, err := h.sceneService.GetScene(sceneID, userID.(int64))
	if err != nil {
		if bizErr, ok := err.(*common.BusinessException); ok {
			c.JSON(http.StatusOK, common.Error(bizErr.Code, bizErr.Message))
		} else {
			log.Printf("获取场景失败: %v", err)
			c.JSON(http.StatusOK, common.Error(common.SYSTEM_ERROR, "系统内部异常"))
		}
		return
	}

	c.JSON(http.StatusOK, common.Success(scene))
}

// ListScenes 获取场景列表
// @Summary      获取场景列表
// @Description  获取场景列表接口（包括用户自己的和预设场景）
// @Tags         场景管理
// @Accept       json
// @Produce      json
// @Success      200 {object}  common.BaseResponse{data=[]model.Scene}  "获取成功"
// @Failure      400 {object}  common.BaseResponse  "参数错误"
// @Router       /scene/list [get]
func (h *SceneHandler) ListScenes(c *gin.Context) {
	userID, exists := c.Get("userID")
	if !exists {
		c.JSON(http.StatusOK, common.ErrorWithDefaultMsg(common.NOT_LOGIN_ERROR))
		return
	}

	scenes, err := h.sceneService.ListScenes(userID.(int64), true)
	if err != nil {
		if bizErr, ok := err.(*common.BusinessException); ok {
			c.JSON(http.StatusOK, common.Error(bizErr.Code, bizErr.Message))
		} else {
			log.Printf("获取场景列表失败: %v", err)
			c.JSON(http.StatusOK, common.Error(common.SYSTEM_ERROR, "系统内部异常"))
		}
		return
	}

	c.JSON(http.StatusOK, common.Success(scenes))
}

// ListScenesPage 分页查询场景列表
// @Summary      分页查询场景列表
// @Description  分页查询场景列表（包括用户自己的和预设场景）
// @Tags         场景管理
// @Accept       json
// @Produce      json
// @Param        pageNum   query     int     false  "页码"  default(1)
// @Param        pageSize  query     int     false  "每页大小"  default(10)
// @Param        category  query     string  false  "分类"
// @Param        isPreset  query     bool    false  "是否为预设场景"
// @Success      200       {object}  common.BaseResponse{data=dto.PageResponse}  "获取成功"
// @Failure      400       {object}  common.BaseResponse  "参数错误"
// @Router       /scene/list/page [get]
func (h *SceneHandler) ListScenesPage(c *gin.Context) {
	userID, exists := c.Get("userID")
	if !exists {
		c.JSON(http.StatusOK, common.ErrorWithDefaultMsg(common.NOT_LOGIN_ERROR))
		return
	}

	pageNum := 1
	if pn := c.Query("pageNum"); pn != "" {
		if val, err := strconv.Atoi(pn); err == nil && val > 0 {
			pageNum = val
		}
	}

	pageSize := 10
	if ps := c.Query("pageSize"); ps != "" {
		if val, err := strconv.Atoi(ps); err == nil && val > 0 {
			pageSize = val
		}
	}

	category := c.Query("category")

	var isPreset *bool
	if ip := c.Query("isPreset"); ip != "" {
		if val, err := strconv.ParseBool(ip); err == nil {
			isPreset = &val
		}
	}

	pageResp, err := h.sceneService.ListScenesPage(userID.(int64), pageNum, pageSize, category, isPreset)
	if err != nil {
		if bizErr, ok := err.(*common.BusinessException); ok {
			c.JSON(http.StatusOK, common.Error(bizErr.Code, bizErr.Message))
		} else {
			log.Printf("分页查询场景列表失败: %v", err)
			c.JSON(http.StatusOK, common.Error(common.SYSTEM_ERROR, "系统内部异常"))
		}
		return
	}

	c.JSON(http.StatusOK, common.Success(pageResp))
}

// GetScenePrompts 获取场景的所有提示词
// @Summary      获取场景提示词列表
// @Description  获取场景的所有提示词
// @Tags         场景管理
// @Accept       json
// @Produce      json
// @Param        sceneId  query     string  true  "场景ID"
// @Success      200      {object}  common.BaseResponse{data=[]model.ScenePrompt}  "获取成功"
// @Failure      400      {object}  common.BaseResponse  "参数错误"
// @Router       /scene/prompts [get]
func (h *SceneHandler) GetScenePrompts(c *gin.Context) {
	sceneID := c.Query("sceneId")
	if sceneID == "" {
		c.JSON(http.StatusOK, common.Error(common.PARAMS_ERROR, "场景ID不能为空"))
		return
	}

	userID, exists := c.Get("userID")
	if !exists {
		c.JSON(http.StatusOK, common.ErrorWithDefaultMsg(common.NOT_LOGIN_ERROR))
		return
	}

	prompts, err := h.sceneService.GetScenePrompts(sceneID, userID.(int64))
	if err != nil {
		if bizErr, ok := err.(*common.BusinessException); ok {
			c.JSON(http.StatusOK, common.Error(bizErr.Code, bizErr.Message))
		} else {
			log.Printf("获取提示词列表失败: %v", err)
			c.JSON(http.StatusOK, common.Error(common.SYSTEM_ERROR, "系统内部异常"))
		}
		return
	}

	c.JSON(http.StatusOK, common.Success(prompts))
}

// AddScenePrompt 添加提示词到场景
// @Summary      添加场景提示词
// @Description  添加提示词到场景
// @Tags         场景管理
// @Accept       json
// @Produce      json
// @Param        request  body      dto.AddScenePromptRequest  true  "添加提示词请求"
// @Success      200      {object}  common.BaseResponse{data=string}  "添加成功，返回提示词ID"
// @Failure      400      {object}  common.BaseResponse  "参数错误"
// @Router       /scene/prompt/add [post]
func (h *SceneHandler) AddScenePrompt(c *gin.Context) {
	var req dto.AddScenePromptRequest
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

	promptID, err := h.sceneService.AddScenePrompt(&req, userID.(int64))
	if err != nil {
		if bizErr, ok := err.(*common.BusinessException); ok {
			c.JSON(http.StatusOK, common.Error(bizErr.Code, bizErr.Message))
		} else {
			log.Printf("添加提示词失败: %v", err)
			c.JSON(http.StatusOK, common.Error(common.SYSTEM_ERROR, "系统内部异常"))
		}
		return
	}

	c.JSON(http.StatusOK, common.Success(promptID))
}

// UpdateScenePrompt 更新场景提示词
// @Summary      更新场景提示词
// @Description  更新场景提示词
// @Tags         场景管理
// @Accept       json
// @Produce      json
// @Param        request  body      dto.UpdateScenePromptRequest  true  "更新提示词请求"
// @Success      200      {object}  common.BaseResponse{data=bool}  "更新成功"
// @Failure      400      {object}  common.BaseResponse  "参数错误"
// @Router       /scene/prompt/update [post]
func (h *SceneHandler) UpdateScenePrompt(c *gin.Context) {
	var req dto.UpdateScenePromptRequest
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

	err := h.sceneService.UpdateScenePrompt(&req, userID.(int64))
	if err != nil {
		if bizErr, ok := err.(*common.BusinessException); ok {
			c.JSON(http.StatusOK, common.Error(bizErr.Code, bizErr.Message))
		} else {
			log.Printf("更新提示词失败: %v", err)
			c.JSON(http.StatusOK, common.Error(common.SYSTEM_ERROR, "系统内部异常"))
		}
		return
	}

	c.JSON(http.StatusOK, common.Success(true))
}

// DeleteScenePrompt 删除场景提示词
// @Summary      删除场景提示词
// @Description  删除场景提示词
// @Tags         场景管理
// @Accept       json
// @Produce      json
// @Param        request  body      dto.DeleteRequest  true  "删除提示词请求"
// @Success      200      {object}  common.BaseResponse{data=bool}  "删除成功"
// @Failure      400      {object}  common.BaseResponse  "参数错误"
// @Router       /scene/prompt/delete [post]
func (h *SceneHandler) DeleteScenePrompt(c *gin.Context) {
	var req dto.DeleteRequest
	if err := c.ShouldBindJSON(&req); err != nil {
		log.Printf("参数绑定失败: %v", err)
		c.JSON(http.StatusOK, common.Error(common.PARAMS_ERROR, "参数错误"))
		return
	}

	if req.ID == "" {
		c.JSON(http.StatusOK, common.Error(common.PARAMS_ERROR, "提示词ID不能为空"))
		return
	}

	userID, exists := c.Get("userID")
	if !exists {
		c.JSON(http.StatusOK, common.ErrorWithDefaultMsg(common.NOT_LOGIN_ERROR))
		return
	}

	err := h.sceneService.DeleteScenePrompt(req.ID, userID.(int64))
	if err != nil {
		if bizErr, ok := err.(*common.BusinessException); ok {
			c.JSON(http.StatusOK, common.Error(bizErr.Code, bizErr.Message))
		} else {
			log.Printf("删除提示词失败: %v", err)
			c.JSON(http.StatusOK, common.Error(common.SYSTEM_ERROR, "系统内部异常"))
		}
		return
	}

	c.JSON(http.StatusOK, common.Success(true))
}
