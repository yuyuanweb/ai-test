// Package handler 用户HTTP处理器
// @author <a href="https://codefather.cn">编程导航学习圈</a>
package handler

import (
	"ai-test-go/internal/config"
	"ai-test-go/internal/model/dto"
	"ai-test-go/internal/service"
	"ai-test-go/pkg/common"
	"ai-test-go/pkg/utils"
	"fmt"
	"log"
	"net/http"

	"github.com/gin-gonic/gin"
)

type UserHandler struct {
	userService *service.UserService
}

func NewUserHandler(userService *service.UserService) *UserHandler {
	return &UserHandler{
		userService: userService,
	}
}

// UserRegister 用户注册
// @Summary      用户注册
// @Description  用户注册接口
// @Tags         用户管理
// @Accept       json
// @Produce      json
// @Param        request  body      dto.UserRegisterRequest  true  "注册请求"
// @Success      200      {object}  common.BaseResponse{data=int64}  "注册成功，返回用户ID"
// @Failure      400      {object}  common.BaseResponse  "参数错误"
// @Router       /user/register [post]
func (h *UserHandler) UserRegister(c *gin.Context) {
	var req dto.UserRegisterRequest
	if err := c.ShouldBindJSON(&req); err != nil {
		log.Printf("参数绑定失败: %v", err)
		c.JSON(http.StatusOK, common.Error(common.PARAMS_ERROR, "参数错误"))
		return
	}

	userID, err := h.userService.UserRegister(&req)
	if err != nil {
		if bizErr, ok := err.(*common.BusinessException); ok {
			c.JSON(http.StatusOK, common.Error(bizErr.Code, bizErr.Message))
		} else {
			log.Printf("注册失败: %v", err)
			c.JSON(http.StatusOK, common.Error(common.SYSTEM_ERROR, "系统内部异常"))
		}
		return
	}

	c.JSON(http.StatusOK, common.Success(userID))
}

// UserLogin 用户登录
// @Summary      用户登录
// @Description  用户登录接口
// @Tags         用户管理
// @Accept       json
// @Produce      json
// @Param        request  body      dto.UserLoginRequest  true  "登录请求"
// @Success      200      {object}  common.BaseResponse{data=vo.LoginUserVO}  "登录成功"
// @Failure      400      {object}  common.BaseResponse  "参数错误"
// @Router       /user/login [post]
func (h *UserHandler) UserLogin(c *gin.Context) {
	var req dto.UserLoginRequest
	if err := c.ShouldBindJSON(&req); err != nil {
		log.Printf("参数绑定失败: %v", err)
		c.JSON(http.StatusOK, common.Error(common.PARAMS_ERROR, "参数错误"))
		return
	}

	loginUserVO, user, err := h.userService.UserLogin(&req)
	if err != nil {
		if bizErr, ok := err.(*common.BusinessException); ok {
			c.JSON(http.StatusOK, common.Error(bizErr.Code, bizErr.Message))
		} else {
			log.Printf("登录失败: %v", err)
			c.JSON(http.StatusOK, common.Error(common.SYSTEM_ERROR, "系统内部异常"))
		}
		return
	}

	sessionID := utils.GenerateSessionID()
	if err := utils.SaveSession(c.Request.Context(), sessionID, user); err != nil {
		log.Printf("保存Redis Session失败: %v, 使用内存存储", err)
		utils.SaveMemorySession(sessionID, user)
	}
	
	cookieName := config.AppConfig.Session.CookieName
	maxAge := config.AppConfig.Session.MaxAge
	c.SetCookie(
		cookieName,
		sessionID,
		maxAge,
		"/",
		"",
		false,
		true,
	)
	c.SetSameSite(http.SameSiteStrictMode)
	log.Printf("用户登录成功，SessionID: %s, UserID: %d", sessionID, user.ID)

	c.JSON(http.StatusOK, common.Success(loginUserVO))
}

// GetLoginUser 获取当前登录用户
// @Summary      获取当前登录用户
// @Description  获取当前登录用户信息
// @Tags         用户管理
// @Accept       json
// @Produce      json
// @Success      200  {object}  common.BaseResponse{data=vo.LoginUserVO}  "成功"
// @Failure      401  {object}  common.BaseResponse  "未登录"
// @Router       /user/get/login [get]
func (h *UserHandler) GetLoginUser(c *gin.Context) {
	userIDInterface, exists := c.Get("userID")
	if !exists {
		c.JSON(http.StatusOK, common.Error(common.NOT_LOGIN_ERROR, "未登录"))
		return
	}

	userID, ok := userIDInterface.(int64)
	if !ok {
		c.JSON(http.StatusOK, common.Error(common.NOT_LOGIN_ERROR, "未登录"))
		return
	}

	user, err := h.userService.GetLoginUser(userID)
	if err != nil {
		if bizErr, ok := err.(*common.BusinessException); ok {
			c.JSON(http.StatusOK, common.Error(bizErr.Code, bizErr.Message))
		} else {
			log.Printf("获取登录用户失败: %v", err)
			c.JSON(http.StatusOK, common.Error(common.SYSTEM_ERROR, "系统内部异常"))
		}
		return
	}

	loginUserVO := h.userService.GetLoginUserVO(user)
	c.JSON(http.StatusOK, common.Success(loginUserVO))
}

// UserLogout 用户注销
// @Summary      用户注销
// @Description  用户注销接口
// @Tags         用户管理
// @Accept       json
// @Produce      json
// @Success      200  {object}  common.BaseResponse{data=bool}  "注销成功"
// @Router       /user/logout [post]
func (h *UserHandler) UserLogout(c *gin.Context) {
	cookieName := config.AppConfig.Session.CookieName
	sessionID, err := c.Cookie(cookieName)
	if err == nil && sessionID != "" {
		if err := utils.DeleteSession(c.Request.Context(), sessionID); err != nil {
			log.Printf("删除Redis Session失败: %v", err)
		}
		utils.DeleteMemorySession(sessionID)
	}

	c.SetCookie(
		cookieName,
		"",
		-1,
		"/",
		"",
		false,
		true,
	)

	log.Printf("用户注销成功，SessionID: %s", sessionID)
	c.JSON(http.StatusOK, common.Success(true))
}

// AddUser 创建用户（仅管理员）
// @Summary      创建用户
// @Description  管理员创建用户
// @Tags         用户管理
// @Accept       json
// @Produce      json
// @Param        request  body      dto.UserAddRequest  true  "创建用户请求"
// @Success      200      {object}  common.BaseResponse{data=int64}  "成功"
// @Router       /user/add [post]
func (h *UserHandler) AddUser(c *gin.Context) {
	var req dto.UserAddRequest
	if err := c.ShouldBindJSON(&req); err != nil {
		log.Printf("参数绑定失败: %v", err)
		c.JSON(http.StatusOK, common.Error(common.PARAMS_ERROR, "参数错误"))
		return
	}

	userID, err := h.userService.AddUser(&req)
	if err != nil {
		if bizErr, ok := err.(*common.BusinessException); ok {
			c.JSON(http.StatusOK, common.Error(bizErr.Code, bizErr.Message))
		} else {
			log.Printf("创建用户失败: %v", err)
			c.JSON(http.StatusOK, common.Error(common.SYSTEM_ERROR, "系统内部异常"))
		}
		return
	}

	c.JSON(http.StatusOK, common.Success(userID))
}

// GetUserByID 根据id获取用户（仅管理员）
// @Summary      根据id获取用户
// @Description  管理员根据id获取用户
// @Tags         用户管理
// @Accept       json
// @Produce      json
// @Param        id  query     int64  true  "用户ID"
// @Success      200  {object}  common.BaseResponse{data=model.User}  "成功"
// @Router       /user/get [get]
func (h *UserHandler) GetUserByID(c *gin.Context) {
	id := c.Query("id")
	if id == "" {
		c.JSON(http.StatusOK, common.Error(common.PARAMS_ERROR, "id参数不能为空"))
		return
	}

	var userID int64
	if _, err := fmt.Sscanf(id, "%d", &userID); err != nil {
		c.JSON(http.StatusOK, common.Error(common.PARAMS_ERROR, "id参数格式错误"))
		return
	}

	user, err := h.userService.GetUserByID(userID)
	if err != nil {
		if bizErr, ok := err.(*common.BusinessException); ok {
			c.JSON(http.StatusOK, common.Error(bizErr.Code, bizErr.Message))
		} else {
			log.Printf("获取用户失败: %v", err)
			c.JSON(http.StatusOK, common.Error(common.SYSTEM_ERROR, "系统内部异常"))
		}
		return
	}

	c.JSON(http.StatusOK, common.Success(user))
}

// GetUserVOByID 根据id获取用户VO
// @Summary      根据id获取用户VO
// @Description  根据id获取脱敏的用户信息
// @Tags         用户管理
// @Accept       json
// @Produce      json
// @Param        id  query     int64  true  "用户ID"
// @Success      200  {object}  common.BaseResponse{data=vo.UserVO}  "成功"
// @Router       /user/get/vo [get]
func (h *UserHandler) GetUserVOByID(c *gin.Context) {
	id := c.Query("id")
	if id == "" {
		c.JSON(http.StatusOK, common.Error(common.PARAMS_ERROR, "id参数不能为空"))
		return
	}

	var userID int64
	if _, err := fmt.Sscanf(id, "%d", &userID); err != nil {
		c.JSON(http.StatusOK, common.Error(common.PARAMS_ERROR, "id参数格式错误"))
		return
	}

	user, err := h.userService.GetUserByID(userID)
	if err != nil {
		if bizErr, ok := err.(*common.BusinessException); ok {
			c.JSON(http.StatusOK, common.Error(bizErr.Code, bizErr.Message))
		} else {
			log.Printf("获取用户失败: %v", err)
			c.JSON(http.StatusOK, common.Error(common.SYSTEM_ERROR, "系统内部异常"))
		}
		return
	}

	userVO := h.userService.GetUserVO(user)
	c.JSON(http.StatusOK, common.Success(userVO))
}

// DeleteUser 删除用户（仅管理员）
// @Summary      删除用户
// @Description  管理员删除用户
// @Tags         用户管理
// @Accept       json
// @Produce      json
// @Param        request  body      dto.DeleteIntRequest  true  "删除请求"
// @Success      200      {object}  common.BaseResponse{data=bool}  "成功"
// @Router       /user/delete [post]
func (h *UserHandler) DeleteUser(c *gin.Context) {
	var req dto.DeleteIntRequest
	if err := c.ShouldBindJSON(&req); err != nil {
		log.Printf("参数绑定失败: %v", err)
		c.JSON(http.StatusOK, common.Error(common.PARAMS_ERROR, "参数错误"))
		return
	}

	if req.ID <= 0 {
		c.JSON(http.StatusOK, common.Error(common.PARAMS_ERROR, "id参数错误"))
		return
	}

	if err := h.userService.DeleteUser(req.ID); err != nil {
		if bizErr, ok := err.(*common.BusinessException); ok {
			c.JSON(http.StatusOK, common.Error(bizErr.Code, bizErr.Message))
		} else {
			log.Printf("删除用户失败: %v", err)
			c.JSON(http.StatusOK, common.Error(common.SYSTEM_ERROR, "系统内部异常"))
		}
		return
	}

	c.JSON(http.StatusOK, common.Success(true))
}

// UpdateUser 更新用户（仅管理员）
// @Summary      更新用户
// @Description  管理员更新用户信息
// @Tags         用户管理
// @Accept       json
// @Produce      json
// @Param        request  body      dto.UserUpdateRequest  true  "更新请求"
// @Success      200      {object}  common.BaseResponse{data=bool}  "成功"
// @Router       /user/update [post]
func (h *UserHandler) UpdateUser(c *gin.Context) {
	var req dto.UserUpdateRequest
	if err := c.ShouldBindJSON(&req); err != nil {
		log.Printf("参数绑定失败: %v", err)
		c.JSON(http.StatusOK, common.Error(common.PARAMS_ERROR, "参数错误"))
		return
	}

	if err := h.userService.UpdateUser(&req); err != nil {
		if bizErr, ok := err.(*common.BusinessException); ok {
			c.JSON(http.StatusOK, common.Error(bizErr.Code, bizErr.Message))
		} else {
			log.Printf("更新用户失败: %v", err)
			c.JSON(http.StatusOK, common.Error(common.SYSTEM_ERROR, "系统内部异常"))
		}
		return
	}

	c.JSON(http.StatusOK, common.Success(true))
}

// ListUserByPage 分页获取用户列表（仅管理员）
// @Summary      分页获取用户列表
// @Description  管理员分页获取用户列表
// @Tags         用户管理
// @Accept       json
// @Produce      json
// @Param        request  body      dto.UserQueryRequest  true  "查询请求"
// @Success      200      {object}  common.BaseResponse{data=dto.PageResponse}  "成功"
// @Router       /user/list/page/vo [post]
func (h *UserHandler) ListUserByPage(c *gin.Context) {
	var req dto.UserQueryRequest
	if err := c.ShouldBindJSON(&req); err != nil {
		log.Printf("参数绑定失败: %v", err)
		c.JSON(http.StatusOK, common.Error(common.PARAMS_ERROR, "参数错误"))
		return
	}

	users, total, err := h.userService.ListUserByPage(&req)
	if err != nil {
		if bizErr, ok := err.(*common.BusinessException); ok {
			c.JSON(http.StatusOK, common.Error(bizErr.Code, bizErr.Message))
		} else {
			log.Printf("查询用户列表失败: %v", err)
			c.JSON(http.StatusOK, common.Error(common.SYSTEM_ERROR, "系统内部异常"))
		}
		return
	}

	userVOList := h.userService.GetUserVOList(users)

	pageResp := dto.PageResponse{
		Total:   total,
		Current: req.PageNum,
		Size:    req.PageSize,
		Records: userVOList,
	}

	c.JSON(http.StatusOK, common.Success(pageResp))
}

// GetUserStatistics 获取用户统计数据
// @Summary      获取用户统计数据
// @Description  获取用户的模型使用统计、花费等数据
// @Tags         用户管理
// @Accept       json
// @Produce      json
// @Success      200  {object}  common.BaseResponse{data=vo.UserStatisticsVO}  "获取成功"
// @Failure      400  {object}  common.BaseResponse  "参数错误"
// @Router       /user/statistics [get]
func (h *UserHandler) GetUserStatistics(c *gin.Context) {
	userID, exists := c.Get("userID")
	if !exists {
		c.JSON(http.StatusOK, common.ErrorWithDefaultMsg(common.NOT_LOGIN_ERROR))
		return
	}

	statistics, err := h.userService.GetUserStatistics(userID.(int64))
	if err != nil {
		if bizErr, ok := err.(*common.BusinessException); ok {
			c.JSON(http.StatusOK, common.Error(bizErr.Code, bizErr.Message))
		} else {
			log.Printf("获取用户统计数据失败: %v", err)
			c.JSON(http.StatusOK, common.Error(common.SYSTEM_ERROR, "系统内部异常"))
		}
		return
	}

	c.JSON(http.StatusOK, common.Success(statistics))
}
