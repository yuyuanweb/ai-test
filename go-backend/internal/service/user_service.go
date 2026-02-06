// Package service 用户服务层
// @author <a href="https://codefather.cn">编程导航学习圈</a>
package service

import (
	"ai-test-go/internal/model"
	"ai-test-go/internal/model/dto"
	"ai-test-go/internal/model/vo"
	"ai-test-go/internal/repository"
	"ai-test-go/pkg/common"
	"ai-test-go/pkg/constants"
	"ai-test-go/pkg/utils"
	"errors"

	"gorm.io/gorm"
)

type UserService struct {
	userRepo                *repository.UserRepository
	conversationMessageRepo *repository.ConversationMessageRepository
	userModelUsageRepo      *repository.UserModelUsageRepository
	modelRepo               *repository.ModelRepository
}

func NewUserService(
	userRepo *repository.UserRepository,
	conversationMessageRepo *repository.ConversationMessageRepository,
	userModelUsageRepo *repository.UserModelUsageRepository,
	modelRepo *repository.ModelRepository,
) *UserService {
	return &UserService{
		userRepo:                userRepo,
		conversationMessageRepo: conversationMessageRepo,
		userModelUsageRepo:      userModelUsageRepo,
		modelRepo:               modelRepo,
	}
}

func (s *UserService) UserRegister(req *dto.UserRegisterRequest) (int64, error) {
	if utils.IsAnyBlank(req.UserAccount, req.UserPassword, req.CheckPassword) {
		return 0, common.NewBusinessException(common.PARAMS_ERROR, "参数为空")
	}

	if len(req.UserAccount) < 4 {
		return 0, common.NewBusinessException(common.PARAMS_ERROR, "账号长度过短")
	}

	if len(req.UserPassword) < 8 || len(req.CheckPassword) < 8 {
		return 0, common.NewBusinessException(common.PARAMS_ERROR, "密码长度过短")
	}

	if req.UserPassword != req.CheckPassword {
		return 0, common.NewBusinessException(common.PARAMS_ERROR, "两次输入的密码不一致")
	}

	count, err := s.userRepo.CountByAccount(req.UserAccount)
	if err != nil {
		return 0, common.NewBusinessException(common.SYSTEM_ERROR, "查询用户失败")
	}
	if count > 0 {
		return 0, common.NewBusinessException(common.PARAMS_ERROR, "账号重复")
	}

	encryptPassword := utils.EncryptPassword(req.UserPassword)

	user := &model.User{
		UserAccount:  req.UserAccount,
		UserPassword: encryptPassword,
		UserName:     "无名",
		UserRole:     constants.DEFAULT_ROLE,
	}

	if err := s.userRepo.Create(user); err != nil {
		return 0, common.NewBusinessException(common.OPERATION_ERROR, "注册失败，数据库错误")
	}

	return user.ID, nil
}

func (s *UserService) UserLogin(req *dto.UserLoginRequest) (*vo.LoginUserVO, *model.User, error) {
	if utils.IsAnyBlank(req.UserAccount, req.UserPassword) {
		return nil, nil, common.NewBusinessException(common.PARAMS_ERROR, "参数为空")
	}

	if len(req.UserAccount) < 4 {
		return nil, nil, common.NewBusinessException(common.PARAMS_ERROR, "账号长度过短")
	}

	if len(req.UserPassword) < 8 {
		return nil, nil, common.NewBusinessException(common.PARAMS_ERROR, "密码长度过短")
	}

	encryptPassword := utils.EncryptPassword(req.UserPassword)

	user, err := s.userRepo.FindByAccountAndPassword(req.UserAccount, encryptPassword)
	if err != nil {
		if errors.Is(err, gorm.ErrRecordNotFound) {
			return nil, nil, common.NewBusinessException(common.PARAMS_ERROR, "用户不存在或密码错误")
		}
		return nil, nil, common.NewBusinessException(common.SYSTEM_ERROR, "查询用户失败")
	}

	loginUserVO := s.GetLoginUserVO(user)
	return loginUserVO, user, nil
}

func (s *UserService) GetLoginUser(userID int64) (*model.User, error) {
	user, err := s.userRepo.FindByID(userID)
	if err != nil {
		if errors.Is(err, gorm.ErrRecordNotFound) {
			return nil, common.NewBusinessExceptionWithDefaultMsg(common.NOT_LOGIN_ERROR)
		}
		return nil, common.NewBusinessException(common.SYSTEM_ERROR, "查询用户失败")
	}
	return user, nil
}

func (s *UserService) GetLoginUserVO(user *model.User) *vo.LoginUserVO {
	if user == nil {
		return nil
	}
	return &vo.LoginUserVO{
		ID:                   user.ID,
		UserAccount:          user.UserAccount,
		UserName:             user.UserName,
		UserAvatar:           user.UserAvatar,
		UserProfile:          user.UserProfile,
		UserRole:             user.UserRole,
		DailyBudget:          user.DailyBudget,
		MonthlyBudget:        user.MonthlyBudget,
		BudgetAlertThreshold: user.BudgetAlertThreshold,
		CreateTime:           user.CreateTime,
		UpdateTime:           user.UpdateTime,
	}
}

func (s *UserService) GetUserVO(user *model.User) *vo.UserVO {
	if user == nil {
		return nil
	}
	return &vo.UserVO{
		ID:          user.ID,
		UserAccount: user.UserAccount,
		UserName:    user.UserName,
		UserAvatar:  user.UserAvatar,
		UserProfile: user.UserProfile,
		UserRole:    user.UserRole,
		CreateTime:  user.CreateTime,
	}
}

func (s *UserService) GetUserVOList(users []*model.User) []*vo.UserVO {
	var userVOList []*vo.UserVO
	for _, user := range users {
		userVOList = append(userVOList, s.GetUserVO(user))
	}
	return userVOList
}

func (s *UserService) AddUser(req *dto.UserAddRequest) (int64, error) {
	if req == nil {
		return 0, common.NewBusinessException(common.PARAMS_ERROR, "参数为空")
	}

	defaultPassword := "12345678"
	encryptPassword := utils.EncryptPassword(defaultPassword)

	user := &model.User{
		UserAccount:  req.UserAccount,
		UserPassword: encryptPassword,
		UserName:     req.UserName,
		UserAvatar:   req.UserAvatar,
		UserProfile:  req.UserProfile,
		UserRole:     req.UserRole,
	}

	if user.UserRole == "" {
		user.UserRole = constants.DEFAULT_ROLE
	}

	if err := s.userRepo.Create(user); err != nil {
		return 0, common.NewBusinessException(common.OPERATION_ERROR, "创建用户失败")
	}

	return user.ID, nil
}

func (s *UserService) GetUserByID(id int64) (*model.User, error) {
	if id <= 0 {
		return nil, common.NewBusinessException(common.PARAMS_ERROR, "id参数错误")
	}

	user, err := s.userRepo.FindByID(id)
	if err != nil {
		if errors.Is(err, gorm.ErrRecordNotFound) {
			return nil, common.NewBusinessException(common.NOT_FOUND_ERROR, "用户不存在")
		}
		return nil, common.NewBusinessException(common.SYSTEM_ERROR, "查询用户失败")
	}

	return user, nil
}

func (s *UserService) DeleteUser(id int64) error {
	if id <= 0 {
		return common.NewBusinessException(common.PARAMS_ERROR, "id参数错误")
	}

	if err := s.userRepo.Delete(id); err != nil {
		return common.NewBusinessException(common.OPERATION_ERROR, "删除用户失败")
	}

	return nil
}

func (s *UserService) UpdateUser(req *dto.UserUpdateRequest) error {
	if req == nil || req.ID == nil {
		return common.NewBusinessException(common.PARAMS_ERROR, "参数错误")
	}

	updates := make(map[string]interface{})
	if req.UserName != "" {
		updates["userName"] = req.UserName
	}
	if req.UserAvatar != "" {
		updates["userAvatar"] = req.UserAvatar
	}
	if req.UserProfile != "" {
		updates["userProfile"] = req.UserProfile
	}
	if req.UserRole != "" {
		updates["userRole"] = req.UserRole
	}
	if req.DailyBudget != nil {
		updates["dailyBudget"] = *req.DailyBudget
	}
	if req.MonthlyBudget != nil {
		updates["monthlyBudget"] = *req.MonthlyBudget
	}
	if req.BudgetAlertThreshold != nil {
		updates["budgetAlertThreshold"] = *req.BudgetAlertThreshold
	}

	if len(updates) == 0 {
		return common.NewBusinessException(common.PARAMS_ERROR, "没有需要更新的字段")
	}

	if err := s.userRepo.UpdateByID(*req.ID, updates); err != nil {
		return common.NewBusinessException(common.OPERATION_ERROR, "更新用户失败")
	}

	return nil
}

func (s *UserService) ListUserByPage(req *dto.UserQueryRequest) ([]*model.User, int64, error) {
	if req == nil {
		return nil, 0, common.NewBusinessException(common.PARAMS_ERROR, "参数错误")
	}

	if req.PageNum <= 0 {
		req.PageNum = 1
	}
	if req.PageSize <= 0 {
		req.PageSize = 10
	}

	query := make(map[string]interface{})
	if req.ID != nil {
		query["id"] = *req.ID
	}
	if req.UserAccount != "" {
		query["userAccount"] = req.UserAccount
	}
	if req.UserName != "" {
		query["userName"] = req.UserName
	}
	if req.UserProfile != "" {
		query["userProfile"] = req.UserProfile
	}
	if req.UserRole != "" {
		query["userRole"] = req.UserRole
	}
	query["sortField"] = req.SortField
	query["sortOrder"] = req.SortOrder

	users, total, err := s.userRepo.ListByPage(req.PageNum, req.PageSize, query)
	if err != nil {
		return nil, 0, common.NewBusinessException(common.SYSTEM_ERROR, "查询用户失败")
	}

	result := make([]*model.User, len(users))
	for i := range users {
		result[i] = &users[i]
	}

	return result, total, nil
}

func (s *UserService) GetUserStatistics(userID int64) (*vo.UserStatisticsVO, error) {
	statistics := &vo.UserStatisticsVO{}

	totalModels, err := s.modelRepo.Count()
	if err == nil {
		statistics.TotalModels = totalModels
	}

	userUsages, err := s.userModelUsageRepo.ListByUserID(userID)
	if err == nil {
		var totalTokens int64
		var totalCost float64
		for _, usage := range userUsages {
			totalTokens += usage.TotalTokens
			totalCost += usage.TotalCost
		}
		statistics.TotalTokens = totalTokens
		statistics.TotalCost = totalCost
	}

	todayCost, err := s.conversationMessageRepo.GetTodayCostByUserID(userID)
	if err == nil {
		statistics.TodayCost = todayCost
	}

	monthCost, err := s.conversationMessageRepo.GetMonthCostByUserID(userID)
	if err == nil {
		statistics.MonthCost = monthCost
	}

	user, err := s.userRepo.FindByID(userID)
	if err == nil && user != nil {
		statistics.DailyBudget = user.DailyBudget
		statistics.MonthlyBudget = user.MonthlyBudget
		statistics.BudgetAlertThreshold = user.BudgetAlertThreshold

		if user.DailyBudget > 0 {
			statistics.DailyBudgetUsagePercent = (todayCost / user.DailyBudget) * 100
			statistics.DailyBudgetAlert = statistics.DailyBudgetUsagePercent >= float64(user.BudgetAlertThreshold)
		}

		if user.MonthlyBudget > 0 {
			statistics.MonthlyBudgetUsagePercent = (monthCost / user.MonthlyBudget) * 100
			statistics.MonthlyBudgetAlert = statistics.MonthlyBudgetUsagePercent >= float64(user.BudgetAlertThreshold)
		}
	}

	return statistics, nil
}
