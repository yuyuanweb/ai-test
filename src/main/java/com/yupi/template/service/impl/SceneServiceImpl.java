package com.yupi.template.service.impl;

import cn.hutool.core.util.StrUtil;
import com.mybatisflex.core.paginate.Page;
import com.mybatisflex.core.query.QueryWrapper;
import com.mybatisflex.spring.service.impl.ServiceImpl;
import com.yupi.template.exception.BusinessException;
import com.yupi.template.exception.ErrorCode;
import com.yupi.template.mapper.SceneMapper;
import com.yupi.template.mapper.ScenePromptMapper;
import com.yupi.template.model.entity.Scene;
import com.yupi.template.model.entity.ScenePrompt;
import com.yupi.template.service.SceneService;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;


/**
 * 场景服务实现
 *
 * @author <a href="https://codefather.cn">编程导航学习圈</a>
 */
@Service
@Slf4j
public class SceneServiceImpl extends ServiceImpl<SceneMapper, Scene> implements SceneService {

    @Resource
    private ScenePromptMapper scenePromptMapper;

    @Override
    public String createScene(Scene scene, Long userId) {
        if (StrUtil.isBlank(scene.getName())) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "场景名称不能为空");
        }

        String sceneId = UUID.randomUUID().toString();
        scene.setId(sceneId);
        scene.setUserId(userId);
        scene.setIsPreset(0);
        scene.setIsActive(1);
        scene.setCreateTime(LocalDateTime.now());
        scene.setUpdateTime(LocalDateTime.now());
        scene.setIsDelete(0);

        boolean result = this.save(scene);
        if (!result) {
            throw new BusinessException(ErrorCode.OPERATION_ERROR, "创建场景失败");
        }

        return sceneId;
    }

    @Override
    public boolean updateScene(Scene scene, Long userId) {
        if (StrUtil.isBlank(scene.getId())) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "场景ID不能为空");
        }

        Scene existingScene = this.getById(scene.getId());
        if (existingScene == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND_ERROR, "场景不存在");
        }

        if (!existingScene.getUserId().equals(userId)) {
            throw new BusinessException(ErrorCode.NO_AUTH_ERROR, "无权限操作");
        }

        scene.setUpdateTime(LocalDateTime.now());
        return this.updateById(scene);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean deleteScene(String sceneId, Long userId) {
        Scene scene = this.getById(sceneId);
        if (scene == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND_ERROR, "场景不存在");
        }

        if (!scene.getUserId().equals(userId)) {
            throw new BusinessException(ErrorCode.NO_AUTH_ERROR, "无权限操作");
        }

        boolean result = this.removeById(sceneId);
        if (result) {
            QueryWrapper queryWrapper = QueryWrapper.create()
                    .eq("sceneId", sceneId);
            scenePromptMapper.deleteByQuery(queryWrapper);
        }

        return result;
    }

    @Override
    public Scene getScene(String sceneId, Long userId) {
        Scene scene = this.getById(sceneId);
        if (scene == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND_ERROR, "场景不存在");
        }

        if (scene.getIsPreset() == 0 && !scene.getUserId().equals(userId)) {
            throw new BusinessException(ErrorCode.NO_AUTH_ERROR, "无权限查看");
        }

        return scene;
    }

    @Override
    public Page<Scene> listScenes(Long userId, int pageNum, int pageSize, String category, Boolean isPreset) {
        QueryWrapper queryWrapper = QueryWrapper.create()
                .where("(userId = ? or isPreset = 1)", userId)
                .orderBy("createTime", false);

        if (StrUtil.isNotBlank(category)) {
            queryWrapper.and("category = ?", category);
        }

        if (isPreset != null) {
            queryWrapper.and("isPreset = ?", isPreset ? 1 : 0);
        }

        return this.page(new Page<>(pageNum, pageSize), queryWrapper);
    }

    @Override
    public List<ScenePrompt> getScenePrompts(String sceneId, Long userId) {
        Scene scene = this.getById(sceneId);
        if (scene == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND_ERROR, "场景不存在");
        }

        if (scene.getIsPreset() == 0 && !scene.getUserId().equals(userId)) {
            throw new BusinessException(ErrorCode.NO_AUTH_ERROR, "无权限查看");
        }

        QueryWrapper queryWrapper = QueryWrapper.create()
                .eq("sceneId", sceneId)
                .orderBy("promptIndex", true);

        return scenePromptMapper.selectListByQuery(queryWrapper);
    }

    @Override
    public String addScenePrompt(ScenePrompt scenePrompt, Long userId) {
        if (StrUtil.isBlank(scenePrompt.getSceneId())) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "场景ID不能为空");
        }

        Scene scene = this.getById(scenePrompt.getSceneId());
        if (scene == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND_ERROR, "场景不存在");
        }

        if (!scene.getUserId().equals(userId)) {
            throw new BusinessException(ErrorCode.NO_AUTH_ERROR, "无权限操作");
        }

        QueryWrapper queryWrapper = QueryWrapper.create()
                .eq("sceneId", scenePrompt.getSceneId());
        long count = scenePromptMapper.selectCountByQuery(queryWrapper);
        int promptIndex = (int) count;

        String promptId = UUID.randomUUID().toString();
        scenePrompt.setId(promptId);
        scenePrompt.setUserId(userId);
        scenePrompt.setPromptIndex(promptIndex);
        scenePrompt.setCreateTime(LocalDateTime.now());
        scenePrompt.setUpdateTime(LocalDateTime.now());
        scenePrompt.setIsDelete(0);

        boolean result = scenePromptMapper.insert(scenePrompt) > 0;
        if (!result) {
            throw new BusinessException(ErrorCode.OPERATION_ERROR, "添加提示词失败");
        }

        return promptId;
    }

    @Override
    public boolean updateScenePrompt(ScenePrompt scenePrompt, Long userId) {
        if (StrUtil.isBlank(scenePrompt.getId())) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "提示词ID不能为空");
        }

        ScenePrompt existingPrompt = scenePromptMapper.selectOneById(scenePrompt.getId());
        if (existingPrompt == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND_ERROR, "提示词不存在");
        }

        if (!existingPrompt.getUserId().equals(userId)) {
            throw new BusinessException(ErrorCode.NO_AUTH_ERROR, "无权限操作");
        }

        scenePrompt.setUpdateTime(LocalDateTime.now());
        return scenePromptMapper.update(scenePrompt) > 0;
    }

    @Override
    public boolean deleteScenePrompt(String promptId, Long userId) {
        ScenePrompt prompt = scenePromptMapper.selectOneById(promptId);
        if (prompt == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND_ERROR, "提示词不存在");
        }

        if (!prompt.getUserId().equals(userId)) {
            throw new BusinessException(ErrorCode.NO_AUTH_ERROR, "无权限操作");
        }

        return scenePromptMapper.deleteById(promptId) > 0;
    }
}

