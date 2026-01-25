package com.yupi.template.controller;

import cn.hutool.core.bean.BeanUtil;
import com.mybatisflex.core.paginate.Page;
import com.yupi.template.common.BaseResponse;
import com.yupi.template.common.DeleteRequest;
import com.yupi.template.common.ResultUtils;
import com.yupi.template.exception.ErrorCode;
import com.yupi.template.exception.ThrowUtils;
import com.yupi.template.model.dto.scene.CreateSceneRequest;
import com.yupi.template.model.dto.scene.UpdateSceneRequest;
import com.yupi.template.model.dto.scene.AddScenePromptRequest;
import com.yupi.template.model.dto.scene.UpdateScenePromptRequest;
import com.yupi.template.model.entity.Scene;
import com.yupi.template.model.entity.ScenePrompt;
import com.yupi.template.model.entity.User;
import com.yupi.template.service.SceneService;
import com.yupi.template.service.UserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Resource;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 场景管理接口
 *
 * @author <a href="https://codefather.cn">编程导航学习圈</a>
 */
@RestController
@RequestMapping("/scene")
@Slf4j
@Tag(name = "场景管理接口")
public class SceneController {

    @Resource
    private SceneService sceneService;

    @Resource
    private UserService userService;

    /**
     * 创建场景
     */
    @PostMapping("/create")
    @Operation(summary = "创建场景")
    public BaseResponse<String> createScene(
            @RequestBody CreateSceneRequest request,
            HttpServletRequest httpRequest
    ) {
        ThrowUtils.throwIf(request == null, ErrorCode.PARAMS_ERROR);
        User loginUser = userService.getLoginUser(httpRequest);
        
        Scene scene = new Scene();
        BeanUtil.copyProperties(request, scene);
        String sceneId = sceneService.createScene(scene, loginUser.getId());
        return ResultUtils.success(sceneId);
    }

    /**
     * 更新场景
     */
    @PostMapping("/update")
    @Operation(summary = "更新场景")
    public BaseResponse<Boolean> updateScene(
            @RequestBody UpdateSceneRequest request,
            HttpServletRequest httpRequest
    ) {
        ThrowUtils.throwIf(request == null || request.getId() == null, ErrorCode.PARAMS_ERROR);
        User loginUser = userService.getLoginUser(httpRequest);
        
        Scene scene = new Scene();
        BeanUtil.copyProperties(request, scene);
        boolean result = sceneService.updateScene(scene, loginUser.getId());
        return ResultUtils.success(result);
    }

    /**
     * 删除场景
     */
    @PostMapping("/delete")
    @Operation(summary = "删除场景")
    public BaseResponse<Boolean> deleteScene(
            @RequestBody DeleteRequest deleteRequest,
            HttpServletRequest httpRequest
    ) {
        ThrowUtils.throwIf(deleteRequest == null || deleteRequest.getId() == null, ErrorCode.PARAMS_ERROR);
        User loginUser = userService.getLoginUser(httpRequest);
        
        boolean result = sceneService.deleteScene(deleteRequest.getId().toString(), loginUser.getId());
        return ResultUtils.success(result);
    }

    /**
     * 获取场景详情
     */
    @GetMapping("/get")
    @Operation(summary = "获取场景详情")
    public BaseResponse<Scene> getScene(
            @RequestParam String id,
            HttpServletRequest httpRequest
    ) {
        ThrowUtils.throwIf(id == null || id.trim().isEmpty(), ErrorCode.PARAMS_ERROR);
        User loginUser = userService.getLoginUser(httpRequest);
        
        Scene scene = sceneService.getScene(id, loginUser.getId());
        return ResultUtils.success(scene);
    }

    /**
     * 分页查询场景列表
     */
    @GetMapping("/list/page")
    @Operation(summary = "分页查询场景列表")
    public BaseResponse<Page<Scene>> listScenes(
            @RequestParam(defaultValue = "1") int pageNum,
            @RequestParam(defaultValue = "10") int pageSize,
            @RequestParam(required = false) String category,
            @RequestParam(required = false) Boolean isPreset,
            HttpServletRequest httpRequest
    ) {
        User loginUser = userService.getLoginUser(httpRequest);
        Page<Scene> scenePage = sceneService.listScenes(loginUser.getId(), pageNum, pageSize, category, isPreset);
        return ResultUtils.success(scenePage);
    }

    /**
     * 获取场景的所有提示词
     */
    @GetMapping("/prompts")
    @Operation(summary = "获取场景的所有提示词")
    public BaseResponse<List<ScenePrompt>> getScenePrompts(
            @RequestParam String sceneId,
            HttpServletRequest httpRequest
    ) {
        ThrowUtils.throwIf(sceneId == null || sceneId.trim().isEmpty(), ErrorCode.PARAMS_ERROR);
        User loginUser = userService.getLoginUser(httpRequest);
        
        List<ScenePrompt> prompts = sceneService.getScenePrompts(sceneId, loginUser.getId());
        return ResultUtils.success(prompts);
    }

    /**
     * 添加提示词到场景
     */
    @PostMapping("/prompt/add")
    @Operation(summary = "添加提示词到场景")
    public BaseResponse<String> addScenePrompt(
            @RequestBody AddScenePromptRequest request,
            HttpServletRequest httpRequest
    ) {
        ThrowUtils.throwIf(request == null, ErrorCode.PARAMS_ERROR);
        User loginUser = userService.getLoginUser(httpRequest);
        
        ScenePrompt scenePrompt = new ScenePrompt();
        BeanUtil.copyProperties(request, scenePrompt);
        String promptId = sceneService.addScenePrompt(scenePrompt, loginUser.getId());
        return ResultUtils.success(promptId);
    }

    /**
     * 更新场景提示词
     */
    @PostMapping("/prompt/update")
    @Operation(summary = "更新场景提示词")
    public BaseResponse<Boolean> updateScenePrompt(
            @RequestBody UpdateScenePromptRequest request,
            HttpServletRequest httpRequest
    ) {
        ThrowUtils.throwIf(request == null || request.getId() == null, ErrorCode.PARAMS_ERROR);
        User loginUser = userService.getLoginUser(httpRequest);
        
        ScenePrompt scenePrompt = new ScenePrompt();
        BeanUtil.copyProperties(request, scenePrompt);
        boolean result = sceneService.updateScenePrompt(scenePrompt, loginUser.getId());
        return ResultUtils.success(result);
    }

    /**
     * 删除场景提示词
     */
    @PostMapping("/prompt/delete")
    @Operation(summary = "删除场景提示词")
    public BaseResponse<Boolean> deleteScenePrompt(
            @RequestBody DeleteRequest deleteRequest,
            HttpServletRequest httpRequest
    ) {
        ThrowUtils.throwIf(deleteRequest == null || deleteRequest.getId() == null, ErrorCode.PARAMS_ERROR);
        User loginUser = userService.getLoginUser(httpRequest);
        
        boolean result = sceneService.deleteScenePrompt(deleteRequest.getId().toString(), loginUser.getId());
        return ResultUtils.success(result);
    }
}
