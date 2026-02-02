package com.yupi.template.service.impl;

import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONUtil;
import com.mybatisflex.core.paginate.Page;
import com.mybatisflex.core.query.QueryWrapper;
import com.yupi.template.constant.CacheConstant;
import com.yupi.template.constant.ConversationConstant;
import com.yupi.template.mapper.ModelMapper;
import com.yupi.template.model.dto.model.ModelQueryRequest;
import com.yupi.template.model.entity.Model;
import com.yupi.template.model.entity.UserModelUsage;
import com.yupi.template.model.vo.ModelPricingVO;
import com.yupi.template.model.vo.ModelVO;
import com.yupi.template.service.ModelService;
import com.yupi.template.service.UserModelUsageService;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
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

    @Resource
    private UserModelUsageService userModelUsageService;

    @Resource
    private CacheManager cacheManager;

    /**
     * 分页查询模型列表（支持搜索）
     */
    @Override
    public Page<ModelVO> listModels(ModelQueryRequest queryRequest, Long userId) {
        int pageNum = (int) queryRequest.getPageNum();
        int pageSize = (int) queryRequest.getPageSize();

        // 构建查询条件
        QueryWrapper queryWrapper = QueryWrapper.create()
                .select("id", "name", "description", "provider", "contextLength",
                        "inputPrice", "outputPrice", "recommended", "isChina", "tags",
                        "totalTokens", "totalCost")
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

        // 转换为VO，并关联用户使用统计
        List<ModelVO> modelVOList = modelPage.getRecords().stream()
                .map(model -> convertToModelVO(model, userId))
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
    public List<ModelVO> getAllModels(Long userId) {
        QueryWrapper queryWrapper = QueryWrapper.create()
                .select("id", "name", "description", "provider", "contextLength",
                        "inputPrice", "outputPrice", "recommended", "isChina", "tags",
                        "totalTokens", "totalCost")
                .where("isDelete = 0")
                .orderBy("isChina", false)
                .orderBy("recommended", false)
                .orderBy("updateTime", false);

        List<Model> models = modelMapper.selectListByQuery(queryWrapper);

        log.info("返回{}个模型", models.size());
        return models.stream()
                .map(model -> convertToModelVO(model, userId))
                .collect(Collectors.toList());
    }

    @Override
    @Cacheable(
            cacheNames = CacheConstant.MODEL_PRICING_CACHE_NAME,
            key = "'" + CacheConstant.MODEL_PRICING_KEY_PREFIX + "' + #modelName",
            unless = "#result == null"
    )
    public ModelPricingVO getModelPricing(String modelName) {
        if (StrUtil.isBlank(modelName)) {
            return null;
        }
        Model model = modelMapper.selectOneById(modelName);
        if (model == null) {
            return null;
        }
        BigDecimal inputPrice = model.getInputPrice() != null
                ? model.getInputPrice()
                : BigDecimal.valueOf(ConversationConstant.DEFAULT_INPUT_PRICE_PER_MILLION);
        BigDecimal outputPrice = model.getOutputPrice() != null
                ? model.getOutputPrice()
                : BigDecimal.valueOf(ConversationConstant.DEFAULT_OUTPUT_PRICE_PER_MILLION);
        return ModelPricingVO.builder()
                .inputPrice(inputPrice)
                .outputPrice(outputPrice)
                .build();
    }

    @Override
    public void evictModelPricingCache(String modelName) {
        if (StrUtil.isBlank(modelName)) {
            return;
        }
        var cache = cacheManager.getCache(CacheConstant.MODEL_PRICING_CACHE_NAME);
        if (cache != null) {
            cache.evict(CacheConstant.MODEL_PRICING_KEY_PREFIX + modelName);
            log.debug("已清除模型价格缓存: modelName={}", modelName);
        }
    }

    /**
     * 转换Model实体为ModelVO
     */
    private ModelVO convertToModelVO(Model model, Long userId) {
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

        // 查询用户维度的使用统计
        Long userTotalTokens = 0L;
        java.math.BigDecimal userTotalCost = java.math.BigDecimal.ZERO;
        if (userId != null) {
            UserModelUsage userUsage = userModelUsageService.getUserModelUsage(userId, model.getId());
            if (userUsage != null) {
                userTotalTokens = userUsage.getTotalTokens() != null ? userUsage.getTotalTokens() : 0L;
                userTotalCost = userUsage.getTotalCost() != null ? userUsage.getTotalCost() : java.math.BigDecimal.ZERO;
            }
        }

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
                .totalTokens(model.getTotalTokens() != null ? model.getTotalTokens() : 0L)
                .totalCost(model.getTotalCost() != null ? model.getTotalCost() : java.math.BigDecimal.ZERO)
                .userTotalTokens(userTotalTokens)
                .userTotalCost(userTotalCost)
                .build();
    }

    /**
     * 更新模型使用统计
     */
    @Override
    public void updateModelUsage(String modelName, int tokens, java.math.BigDecimal cost) {
        if (StrUtil.isBlank(modelName) || tokens <= 0) {
            return;
        }

        try {
            Model model = modelMapper.selectOneById(modelName);
            if (model == null) {
                log.warn("模型不存在，无法更新使用统计: modelName={}", modelName);
                return;
            }

            Long currentTokens = model.getTotalTokens() != null ? model.getTotalTokens() : 0L;
            java.math.BigDecimal currentCost = model.getTotalCost() != null ? model.getTotalCost() : java.math.BigDecimal.ZERO;

            model.setTotalTokens(currentTokens + tokens);
            if (cost != null) {
                model.setTotalCost(currentCost.add(cost));
            }

            modelMapper.update(model);
            log.debug("更新模型使用统计: modelName={}, addTokens={}, addCost={}, totalTokens={}, totalCost={}",
                    modelName, tokens, cost, model.getTotalTokens(), model.getTotalCost());
        } catch (Exception e) {
            log.error("更新模型使用统计失败: modelName={}", modelName, e);
        }
    }
}

