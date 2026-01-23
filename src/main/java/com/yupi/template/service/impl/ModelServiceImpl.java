package com.yupi.template.service.impl;

import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONUtil;
import com.mybatisflex.core.paginate.Page;
import com.mybatisflex.core.query.QueryWrapper;
import com.yupi.template.mapper.ModelMapper;
import com.yupi.template.model.dto.model.ModelQueryRequest;
import com.yupi.template.model.entity.Model;
import com.yupi.template.model.vo.ModelVO;
import com.yupi.template.service.ModelService;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.stream.Collectors;

/**
 * 模型服务实现类
 *
 * @author <a href="https://codefather.cn">编程导航学习圈</a>
 */
@Slf4j
@Service
public class ModelServiceImpl implements ModelService {

    @Resource
    private ModelMapper modelMapper;

    /**
     * 分页查询模型列表（支持搜索）
     */
    @Override
    public Page<ModelVO> listModels(ModelQueryRequest queryRequest) {
        int pageNum = (int) queryRequest.getPageNum();
        int pageSize = (int) queryRequest.getPageSize();

        // 构建查询条件
        QueryWrapper queryWrapper = QueryWrapper.create()
                .select("id", "name", "description", "provider", "contextLength",
                        "inputPrice", "outputPrice", "recommended", "isChina", "tags")
                .where("isDelete = 0");

        // 搜索条件
        if (StrUtil.isNotBlank(queryRequest.getSearchText())) {
            String searchText = "%" + queryRequest.getSearchText() + "%";
            queryWrapper.and("(name LIKE ? OR id LIKE ? OR provider LIKE ?)",
                    searchText, searchText, searchText);
        }

        // 提供商筛选
        if (StrUtil.isNotBlank(queryRequest.getProvider())) {
            queryWrapper.and("provider = ?", queryRequest.getProvider());
        }

        // 排序：国内模型优先，推荐在前，最新在前
        queryWrapper.orderBy("isChina", false)
                .orderBy("recommended", false)
                .orderBy("updateTime", false);

        Page<Model> modelPage = modelMapper.paginate(Page.of(pageNum, pageSize), queryWrapper);

        log.info("分页查询模型：第{}页，每页{}条，总数{}，返回{}条",
                pageNum, pageSize, modelPage.getTotalRow(), modelPage.getRecords().size());

        // 转换为VO
        List<ModelVO> modelVOList = modelPage.getRecords().stream()
                .map(this::convertToModelVO)
                .collect(Collectors.toList());

        // 构造分页结果
        Page<ModelVO> result = new Page<>(pageNum, pageSize, modelPage.getTotalRow());
        result.setRecords(modelVOList);
        return result;
    }

    /**
     * 获取所有模型列表（国内优先）
     */
    @Override
    public List<ModelVO> getAllModels() {
        QueryWrapper queryWrapper = QueryWrapper.create()
                .select("id", "name", "description", "provider", "contextLength",
                        "inputPrice", "outputPrice", "recommended", "isChina", "tags")
                .where("isDelete = 0")
                .orderBy("isChina", false)
                .orderBy("recommended", false)
                .orderBy("updateTime", false);

        List<Model> models = modelMapper.selectListByQuery(queryWrapper);

        log.info("返回{}个模型", models.size());
        return models.stream()
                .map(this::convertToModelVO)
                .collect(Collectors.toList());
    }

    /**
     * 转换Model实体为ModelVO
     */
    private ModelVO convertToModelVO(Model model) {
        // 解析标签JSON数组
        String[] tags = null;
        if (model.getTags() != null && !model.getTags().isEmpty()) {
            try {
                // 使用parseArray解析JSON数组
                List<String> tagList = JSONUtil.parseArray(model.getTags()).toList(String.class);
                tags = tagList.toArray(new String[0]);
            } catch (Exception e) {
                log.warn("解析模型标签失败: {}, tags: {}", model.getId(), model.getTags());
                // 降级处理：返回空数组
                tags = new String[0];
            }
        }

        Boolean isChina = model.getIsChina() != null && model.getIsChina() == 1;

        return ModelVO.builder()
                .id(model.getId())
                .name(model.getName())
                .description(model.getDescription())
                .provider(model.getProvider())
                .contextLength(model.getContextLength())
                .inputPrice(model.getInputPrice())
                .outputPrice(model.getOutputPrice())
                .recommended(model.getRecommended() != null && model.getRecommended() == 1)
                .isChina(isChina)
                .tags(tags)
                .build();
    }
}

