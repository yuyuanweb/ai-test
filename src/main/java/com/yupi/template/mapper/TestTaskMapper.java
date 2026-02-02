package com.yupi.template.mapper;

import com.mybatisflex.core.BaseMapper;
import com.yupi.template.model.entity.TestTask;
import org.apache.ibatis.annotations.Param;

/**
 * 批量测试任务 Mapper
 *
 * @author <a href="https://codefather.cn">编程导航学习圈</a>
 */
public interface TestTaskMapper extends BaseMapper<TestTask> {

    /**
     * 原子自增 completedSubtasks，并更新 status/startedAt/completedAt
     *
     * @param taskId 任务ID
     * @return 影响行数，0 表示任务已取消或已失败
     */
    int incrementCompletedSubtasks(@Param("taskId") String taskId);

    /**
     * 原子自增 completedSubtasks 并标记任务失败
     *
     * @param taskId 任务ID
     * @return 影响行数
     */
    int incrementCompletedSubtasksAndFail(@Param("taskId") String taskId);
}

