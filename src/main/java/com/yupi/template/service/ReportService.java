package com.yupi.template.service;

import com.yupi.template.model.vo.ReportVO;

/**
 * 报告服务接口
 *
 * @author <a href="https://codefather.cn">编程导航学习圈</a>
 */
public interface ReportService {

    /**
     * 生成测试报告
     *
     * @param taskId 任务ID
     * @param userId 用户ID
     * @return 测试报告
     */
    ReportVO generateReport(String taskId, Long userId);
}
