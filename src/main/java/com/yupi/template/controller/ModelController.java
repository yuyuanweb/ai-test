package com.yupi.template.controller;

import com.mybatisflex.core.paginate.Page;
import com.yupi.template.common.BaseResponse;
import com.yupi.template.common.ResultUtils;
import com.yupi.template.model.dto.model.ModelQueryRequest;
import com.yupi.template.model.vo.ModelVO;
import com.yupi.template.service.ModelService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 模型接口
 *
 * @author <a href="https://codefather.cn">编程导航学习圈</a>
 */
@RestController
@RequestMapping("/model")
@Slf4j
@Tag(name = "模型接口")
public class ModelController {

    @Resource
    private ModelService modelService;

    /**
     * 分页查询模型列表（支持搜索）
     */
    @PostMapping("/list")
    @Operation(summary = "分页查询模型列表")
    public BaseResponse<Page<ModelVO>> listModels(@RequestBody ModelQueryRequest queryRequest) {
        Page<ModelVO> page = modelService.listModels(queryRequest);
        return ResultUtils.success(page);
    }

    /**
     * 获取所有模型列表（国内优先）
     */
    @GetMapping("/all")
    @Operation(summary = "获取所有模型列表")
    public BaseResponse<List<ModelVO>> getAllModels() {
        List<ModelVO> models = modelService.getAllModels();
        return ResultUtils.success(models);
    }
}

