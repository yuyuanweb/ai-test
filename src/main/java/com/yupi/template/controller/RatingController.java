package com.yupi.template.controller;

import com.yupi.template.annotation.AuthCheck;
import com.yupi.template.common.BaseResponse;
import com.yupi.template.common.ResultUtils;
import com.yupi.template.constant.UserConstant;
import com.yupi.template.exception.ErrorCode;
import com.yupi.template.exception.ThrowUtils;
import com.yupi.template.model.dto.rating.RatingAddRequest;
import com.yupi.template.model.entity.Rating;
import com.yupi.template.model.entity.User;
import com.yupi.template.model.vo.RatingVO;
import com.yupi.template.service.RatingService;
import com.yupi.template.service.UserService;
import jakarta.annotation.Resource;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.web.bind.annotation.*;

/**
 * 评分接口
 *
 * @author <a href="https://codefather.cn">编程导航学习圈</a>
 */
@RestController
@RequestMapping("/rating")
@Slf4j
public class RatingController {

    @Resource
    private RatingService ratingService;

    @Resource
    private UserService userService;

    /**
     * 添加或更新评分
     *
     * @param request 添加评分请求
     * @param httpServletRequest HTTP请求
     * @return 是否成功
     */
    @PostMapping("/add")
    @AuthCheck(mustRole = UserConstant.DEFAULT_ROLE)
    public BaseResponse<Boolean> addRating(@RequestBody RatingAddRequest request,
                                           HttpServletRequest httpServletRequest) {
        ThrowUtils.throwIf(request == null, ErrorCode.PARAMS_ERROR);
        ThrowUtils.throwIf(StringUtils.isBlank(request.getConversationId()), ErrorCode.PARAMS_ERROR, "对话ID不能为空");
        ThrowUtils.throwIf(request.getMessageIndex() == null, ErrorCode.PARAMS_ERROR, "消息序号不能为空");
        ThrowUtils.throwIf(StringUtils.isBlank(request.getRatingType()), ErrorCode.PARAMS_ERROR, "评分类型不能为空");


        // 获取当前登录用户
        User loginUser = userService.getLoginUser(httpServletRequest);

        // 构建评分实体
        Rating rating = Rating.builder()
                .conversationId(request.getConversationId())
                .messageIndex(request.getMessageIndex())
                .userId(loginUser.getId())
                .ratingType(request.getRatingType())
                .winnerModel(request.getWinnerModel())
                .loserModel(request.getLoserModel())
                .winnerVariantIndex(request.getWinnerVariantIndex())
                .loserVariantIndex(request.getLoserVariantIndex())
                .build();

        boolean result = ratingService.saveOrUpdateRating(rating);
        ThrowUtils.throwIf(!result, ErrorCode.OPERATION_ERROR, "评分失败");

        return ResultUtils.success(true);
    }

    /**
     * 获取评分
     *
     * @param conversationId 对话ID
     * @param messageIndex 消息序号
     * @param httpServletRequest HTTP请求
     * @return 评分VO
     */
    @GetMapping("/get")
    @AuthCheck(mustRole = UserConstant.DEFAULT_ROLE)
    public BaseResponse<RatingVO> getRating(@RequestParam String conversationId,
                                            @RequestParam Integer messageIndex,
                                            HttpServletRequest httpServletRequest) {
        ThrowUtils.throwIf(StringUtils.isBlank(conversationId), ErrorCode.PARAMS_ERROR, "对话ID不能为空");
        ThrowUtils.throwIf(messageIndex == null, ErrorCode.PARAMS_ERROR, "消息序号不能为空");

        // 获取当前登录用户
        User loginUser = userService.getLoginUser(httpServletRequest);

        RatingVO ratingVO = ratingService.getRating(conversationId, messageIndex, loginUser.getId());
        return ResultUtils.success(ratingVO);
    }

    /**
     * 删除评分
     *
     * @param conversationId 对话ID
     * @param messageIndex 消息序号
     * @param httpServletRequest HTTP请求
     * @return 是否成功
     */
    @DeleteMapping("/delete")
    @AuthCheck(mustRole = UserConstant.DEFAULT_ROLE)
    public BaseResponse<Boolean> deleteRating(@RequestParam String conversationId,
                                              @RequestParam Integer messageIndex,
                                              HttpServletRequest httpServletRequest) {
        ThrowUtils.throwIf(StringUtils.isBlank(conversationId), ErrorCode.PARAMS_ERROR, "对话ID不能为空");
        ThrowUtils.throwIf(messageIndex == null, ErrorCode.PARAMS_ERROR, "消息序号不能为空");

        // 获取当前登录用户
        User loginUser = userService.getLoginUser(httpServletRequest);

        boolean result = ratingService.deleteRating(conversationId, messageIndex, loginUser.getId());
        ThrowUtils.throwIf(!result, ErrorCode.OPERATION_ERROR, "删除评分失败");

        return ResultUtils.success(true);
    }
}

