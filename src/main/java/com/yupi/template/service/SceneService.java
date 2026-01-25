package com.yupi.template.service;

import com.mybatisflex.core.paginate.Page;
import com.mybatisflex.core.service.IService;
import com.yupi.template.model.entity.Scene;
import com.yupi.template.model.entity.ScenePrompt;

import java.util.List;

/**
 * 场景服务接口
 *
 * @author <a href="https://codefather.cn">编程导航学习圈</a>
 */
public interface SceneService extends IService<Scene> {

    /**
     * 创建场景
     *
     * @param scene 场景信息
     * @param userId 用户ID
     * @return 场景ID
     */
    String createScene(Scene scene, Long userId);

    /**
     * 更新场景
     *
     * @param scene 场景信息
     * @param userId 用户ID
     * @return 是否成功
     */
    boolean updateScene(Scene scene, Long userId);

    /**
     * 删除场景
     *
     * @param sceneId 场景ID
     * @param userId 用户ID
     * @return 是否成功
     */
    boolean deleteScene(String sceneId, Long userId);

    /**
     * 获取场景详情
     *
     * @param sceneId 场景ID
     * @param userId 用户ID
     * @return 场景信息
     */
    Scene getScene(String sceneId, Long userId);

    /**
     * 分页查询场景列表
     *
     * @param userId 用户ID
     * @param pageNum 页码
     * @param pageSize 每页大小
     * @param category 分类（可选）
     * @param isPreset 是否为预设场景（可选）
     * @return 分页结果
     */
    Page<Scene> listScenes(Long userId, int pageNum, int pageSize, String category, Boolean isPreset);

    /**
     * 获取场景的所有提示词
     *
     * @param sceneId 场景ID
     * @param userId 用户ID
     * @return 提示词列表
     */
    List<ScenePrompt> getScenePrompts(String sceneId, Long userId);

    /**
     * 添加提示词到场景
     *
     * @param scenePrompt 提示词信息
     * @param userId 用户ID
     * @return 提示词ID
     */
    String addScenePrompt(ScenePrompt scenePrompt, Long userId);

    /**
     * 更新场景提示词
     *
     * @param scenePrompt 提示词信息
     * @param userId 用户ID
     * @return 是否成功
     */
    boolean updateScenePrompt(ScenePrompt scenePrompt, Long userId);

    /**
     * 删除场景提示词
     *
     * @param promptId 提示词ID
     * @param userId 用户ID
     * @return 是否成功
     */
    boolean deleteScenePrompt(String promptId, Long userId);
}

