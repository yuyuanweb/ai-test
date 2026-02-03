package com.yupi.template.service.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.util.StrUtil;
import com.mybatisflex.core.query.QueryWrapper;
import com.mybatisflex.spring.service.impl.ServiceImpl;
import com.yupi.template.exception.BusinessException;
import com.yupi.template.exception.ErrorCode;
import com.yupi.template.model.dto.user.UserQueryRequest;
import com.yupi.template.model.entity.User;
import com.yupi.template.mapper.UserMapper;
import com.yupi.template.model.enums.UserRoleEnum;
import com.yupi.template.mapper.ConversationMessageMapper;
import com.yupi.template.mapper.ModelMapper;
import com.yupi.template.mapper.UserModelUsageMapper;
import com.yupi.template.model.entity.UserModelUsage;
import com.yupi.template.model.vo.LoginUserVO;
import com.yupi.template.model.vo.UserStatisticsVO;
import com.yupi.template.model.vo.UserVO;
import com.yupi.template.service.UserService;
import jakarta.annotation.Resource;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.stereotype.Service;
import org.springframework.util.DigestUtils;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

import static com.yupi.template.constant.UserConstant.USER_LOGIN_STATE;

/**
 * 用户 服务层实现。
 *
 * @author <a href="https://codefather.cn">编程导航学习圈</a>
 */
@Service
public class UserServiceImpl extends ServiceImpl<UserMapper, User> implements UserService {

    @Resource
    private ModelMapper modelMapper;

    @Resource
    private UserModelUsageMapper userModelUsageMapper;

    @Resource
    private ConversationMessageMapper conversationMessageMapper;

    @Override
    public long userRegister(String userAccount, String userPassword, String checkPassword) {
        // 1. 校验参数
        if (StrUtil.hasBlank(userAccount, userPassword, checkPassword)) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "参数为空");
        }
        if (userAccount.length() < 4) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "账号长度过短");
        }
        if (userPassword.length() < 8 || checkPassword.length() < 8) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "密码长度过短");
        }
        if (!userPassword.equals(checkPassword)) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "两次输入的密码不一致");
        }
        // 2. 查询用户是否已存在
        QueryWrapper queryWrapper = new QueryWrapper();
        queryWrapper.eq("userAccount", userAccount);
        long count = this.mapper.selectCountByQuery(queryWrapper);
        if (count > 0) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "账号重复");
        }
        // 3. 加密密码
        String encryptPassword = getEncryptPassword(userPassword);
        // 4. 创建用户，插入数据库
        User user = new User();
        user.setUserAccount(userAccount);
        user.setUserPassword(encryptPassword);
        user.setUserName("无名");
        user.setUserRole(UserRoleEnum.USER.getValue());
        boolean saveResult = this.save(user);
        if (!saveResult) {
            throw new BusinessException(ErrorCode.OPERATION_ERROR, "注册失败，数据库错误");
        }
        return user.getId();
    }

    @Override
    public LoginUserVO getLoginUserVO(User user) {
        if (user == null) {
            return null;
        }
        LoginUserVO loginUserVO = new LoginUserVO();
        BeanUtil.copyProperties(user, loginUserVO);
        return loginUserVO;
    }

    @Override
    public LoginUserVO userLogin(String userAccount, String userPassword, HttpServletRequest request) {
        // 1. 校验参数
        if (StrUtil.hasBlank(userAccount, userPassword)) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "参数为空");
        }
        if (userAccount.length() < 4) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "账号长度过短");
        }
        if (userPassword.length() < 8) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "密码长度过短");
        }
        // 2. 加密
        String encryptPassword = getEncryptPassword(userPassword);
        // 3. 查询用户是否存在
        QueryWrapper queryWrapper = new QueryWrapper();
        queryWrapper.eq("userAccount", userAccount);
        queryWrapper.eq("userPassword", encryptPassword);
        User user = this.mapper.selectOneByQuery(queryWrapper);
        if (user == null) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "用户不存在或密码错误");
        }
        // 4. 如果用户存在，记录用户的登录态
        request.getSession().setAttribute(USER_LOGIN_STATE, user);
        // 5. 返回脱敏的用户信息
        return this.getLoginUserVO(user);
    }

    @Override
    public User getLoginUser(HttpServletRequest request) {
        // 先判断用户是否登录
        Object userObj = request.getSession().getAttribute(USER_LOGIN_STATE);
        User currentUser = (User) userObj;
        if (currentUser == null || currentUser.getId() == null) {
            throw new BusinessException(ErrorCode.NOT_LOGIN_ERROR);
        }
        // 从数据库查询当前用户信息
        long userId = currentUser.getId();
        currentUser = this.getById(userId);
        if (currentUser == null) {
            throw new BusinessException(ErrorCode.NOT_LOGIN_ERROR);
        }
        return currentUser;
    }

    @Override
    public UserVO getUserVO(User user) {
        if (user == null) {
            return null;
        }
        UserVO userVO = new UserVO();
        BeanUtil.copyProperties(user, userVO);
        return userVO;
    }

    @Override
    public List<UserVO> getUserVOList(List<User> userList) {
        if (CollUtil.isEmpty(userList)) {
            return new ArrayList<>();
        }
        return userList.stream()
                .map(this::getUserVO)
                .collect(Collectors.toList());
    }

    @Override
    public boolean userLogout(HttpServletRequest request) {
        // 先判断用户是否登录
        Object userObj = request.getSession().getAttribute(USER_LOGIN_STATE);
        if (userObj == null) {
            throw new BusinessException(ErrorCode.OPERATION_ERROR, "用户未登录");
        }
        // 移除登录态
        request.getSession().removeAttribute(USER_LOGIN_STATE);
        return true;
    }

    @Override
    public QueryWrapper getQueryWrapper(UserQueryRequest userQueryRequest) {
        if (userQueryRequest == null) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "请求参数为空");
        }
        Long id = userQueryRequest.getId();
        String userAccount = userQueryRequest.getUserAccount();
        String userName = userQueryRequest.getUserName();
        String userProfile = userQueryRequest.getUserProfile();
        String userRole = userQueryRequest.getUserRole();
        String sortField = userQueryRequest.getSortField();
        String sortOrder = userQueryRequest.getSortOrder();
        return QueryWrapper.create()
                .eq("id", id) // where id = ${id}
                .eq("userRole", userRole) // and userRole = ${userRole}
                .like("userAccount", userAccount)
                .like("userName", userName)
                .like("userProfile", userProfile)
                .orderBy(sortField, "ascend".equals(sortOrder));
    }

    @Override
    public String getEncryptPassword(String userPassword) {
        // 盐值，混淆密码
        final String SALT = "yupi";
        return DigestUtils.md5DigestAsHex((userPassword + SALT).getBytes(StandardCharsets.UTF_8));
    }

    @Override
    public UserStatisticsVO getUserStatistics(Long userId) {
        UserStatisticsVO statistics = new UserStatisticsVO();

        // 查询模型总数（isDelete = 0）
        QueryWrapper modelWrapper = QueryWrapper.create()
                .eq("isDelete", 0);
        Long totalModels = modelMapper.selectCountByQuery(modelWrapper);
        statistics.setTotalModels(totalModels != null ? totalModels : 0L);

        // 从用户-模型使用统计表查询用户的总Tokens和总花费
        QueryWrapper userModelUsageWrapper = QueryWrapper.create()
                .eq("userId", userId)
                .eq("isDelete", 0);
        List<UserModelUsage> userModelUsages = userModelUsageMapper.selectListByQuery(userModelUsageWrapper);

        Long totalTokens = 0L;
        BigDecimal totalCost = BigDecimal.ZERO;

        if (CollUtil.isNotEmpty(userModelUsages)) {
            for (UserModelUsage usage : userModelUsages) {
                if (usage.getTotalTokens() != null) {
                    totalTokens += usage.getTotalTokens();
                }
                if (usage.getTotalCost() != null) {
                    totalCost = totalCost.add(usage.getTotalCost());
                }
            }
        }

        statistics.setTotalTokens(totalTokens);
        statistics.setTotalCost(totalCost);

        // 查询今日花费和本月花费
        BigDecimal todayCost = conversationMessageMapper.selectTodayCostByUserId(userId);
        BigDecimal monthCost = conversationMessageMapper.selectMonthCostByUserId(userId);
        statistics.setTodayCost(todayCost != null ? todayCost : BigDecimal.ZERO);
        statistics.setMonthCost(monthCost != null ? monthCost : BigDecimal.ZERO);

        // 获取用户的预算配置
        User user = this.getById(userId);
        if (user != null) {
            BigDecimal dailyBudget = user.getDailyBudget();
            BigDecimal monthlyBudget = user.getMonthlyBudget();
            Integer alertThreshold = user.getBudgetAlertThreshold();

            statistics.setDailyBudget(dailyBudget);
            statistics.setMonthlyBudget(monthlyBudget);
            statistics.setBudgetAlertThreshold(alertThreshold != null ? alertThreshold : 80);

            // 计算预算使用百分比
            if (dailyBudget != null && dailyBudget.compareTo(BigDecimal.ZERO) > 0) {
                BigDecimal dailyUsagePercent = statistics.getTodayCost()
                        .multiply(new BigDecimal("100"))
                        .divide(dailyBudget, 2, RoundingMode.HALF_UP);
                statistics.setDailyBudgetUsagePercent(dailyUsagePercent);
                statistics.setDailyBudgetAlert(dailyUsagePercent.compareTo(new BigDecimal(statistics.getBudgetAlertThreshold())) >= 0);
            } else {
                statistics.setDailyBudgetUsagePercent(BigDecimal.ZERO);
                statistics.setDailyBudgetAlert(false);
            }

            if (monthlyBudget != null && monthlyBudget.compareTo(BigDecimal.ZERO) > 0) {
                BigDecimal monthlyUsagePercent = statistics.getMonthCost()
                        .multiply(new BigDecimal("100"))
                        .divide(monthlyBudget, 2, RoundingMode.HALF_UP);
                statistics.setMonthlyBudgetUsagePercent(monthlyUsagePercent);
                statistics.setMonthlyBudgetAlert(monthlyUsagePercent.compareTo(new BigDecimal(statistics.getBudgetAlertThreshold())) >= 0);
            } else {
                statistics.setMonthlyBudgetUsagePercent(BigDecimal.ZERO);
                statistics.setMonthlyBudgetAlert(false);
            }
        }

        return statistics;
    }
}
