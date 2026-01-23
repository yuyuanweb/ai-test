package com.yupi.template.service;

import com.mybatisflex.core.paginate.Page;
import com.yupi.template.model.dto.model.ModelQueryRequest;
import com.yupi.template.model.vo.ModelVO;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 模型服务测试
 *
 * @author <a href="https://codefather.cn">编程导航学习圈</a>
 */
@SpringBootTest
@Slf4j
class ModelServiceTest {

    @Resource
    private ModelService modelService;

    /**
     * 测试分页查询模型
     */
    @Test
    void testListModels() {
        ModelQueryRequest request = new ModelQueryRequest();
        request.setPageNum(1);
        request.setPageSize(10);
        
        Page<ModelVO> page = modelService.listModels(request);
        assertNotNull(page, "分页结果不应为空");
        assertNotNull(page.getRecords(), "模型列表不应为空");
        assertTrue(page.getTotalRow() > 0, "应至少有一个模型");
        log.info("查询到{}个模型，总数：{}", page.getRecords().size(), page.getTotalRow());
        
        // 打印前5个模型
        page.getRecords().stream().limit(5).forEach(model ->
                log.info("模型: id={}, name={}, isChina={}, recommended={}",
                        model.getId(), model.getName(), model.getIsChina(), model.getRecommended())
        );
    }

    /**
     * 测试获取所有模型
     */
    @Test
    void testGetAllModels() {
        List<ModelVO> models = modelService.getAllModels();
        assertNotNull(models, "模型列表不应为空");
        log.info("获取到{}个模型", models.size());
        
        // 打印前5个模型
        models.stream().limit(5).forEach(model -> {
            log.info("模型: id={}, name={}, isChina={}",
                    model.getId(), model.getName(), model.getIsChina());
        });
    }
}

