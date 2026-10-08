package cn.iocoder.yudao.module.product.service.group;

import cn.hutool.core.collection.CollUtil;
import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.framework.common.util.object.BeanUtils;
import cn.iocoder.yudao.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.iocoder.yudao.framework.security.core.util.SecurityFrameworkUtils;
import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.product.controller.admin.category.vo.ProductCategoryListReqVO;
import cn.iocoder.yudao.module.product.controller.admin.group.vo.*;
import cn.iocoder.yudao.module.product.controller.admin.spu.vo.ProductSpuRespVO;
import cn.iocoder.yudao.module.product.dal.dataobject.category.ProductCategoryDO;
import cn.iocoder.yudao.module.product.dal.dataobject.group.ProductGroupDO;
import cn.iocoder.yudao.module.product.dal.dataobject.group.ProductGroupSpuDO;
import cn.iocoder.yudao.module.product.dal.dataobject.spu.ProductSpuDO;
import cn.iocoder.yudao.module.product.dal.mysql.group.ProductGroupMapper;
import cn.iocoder.yudao.module.product.dal.mysql.group.ProductGroupOperationsMapper;
import cn.iocoder.yudao.module.product.dal.mysql.group.ProductGroupSpuMapper;
import cn.iocoder.yudao.module.product.service.category.ProductCategoryService;
import jakarta.annotation.Resource;
import jakarta.validation.Valid;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.validation.annotation.Validated;

import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.yudao.framework.common.pojo.PageParam.PAGE_SIZE_NONE;
import static cn.iocoder.yudao.module.product.enums.ErrorCodeConstants.*;

/** 独立分组运营能力，不更改现有分类、交易及活动的商品匹配链路。 */
@Service
@Validated
public class ProductGroupOperationsService {
    @Resource private ProductGroupMapper groupMapper;
    @Resource private ProductGroupSpuMapper relationMapper;
    @Resource private ProductGroupOperationsMapper spuMapper;
    @Resource private ProductCategoryService categoryService;

    public PageResult<ProductSpuRespVO> getSpuPage(@Valid ProductGroupSpuFilterReqVO request) {
        var page = spuMapper.selectFilterPage(request, getCategoryIds(request), tenantId());
        PageResult<ProductSpuRespVO> response = BeanUtils.toBean(page, ProductSpuRespVO.class);
        Map<Long, List<Long>> groups = getSpuGroupMap(page.getList().stream().map(ProductSpuDO::getId).toList());
        response.getList().forEach(spu -> spu.setGroupIds(groups.getOrDefault(spu.getId(), List.of())));
        return response;
    }

    public Map<Integer, Long> getSpuCounts(@Valid ProductGroupSpuFilterReqVO request) {
        Set<Long> categories = getCategoryIds(request);
        Map<Integer, Long> counts = new LinkedHashMap<>();
        for (int tab = 0; tab < 5; tab++) {
            counts.put(tab, spuMapper.selectFilterCount(request, categories, tenantId(), tab));
        }
        return counts;
    }

    public List<ProductSpuRespVO> getSpuExportList(@Valid ProductGroupSpuFilterReqVO request) {
        // Validate the public request before applying the internal, unpaged export sentinel.
        ProductGroupSpuFilterReqVO query = BeanUtils.toBean(request, ProductGroupSpuFilterReqVO.class);
        query.setPageSize(PAGE_SIZE_NONE);
        return getSpuPage(query).getList();
    }

    private Set<Long> getCategoryIds(ProductGroupSpuFilterReqVO request) {
        Set<Long> categories = new LinkedHashSet<>();
        if (request.getCategoryId() != null && request.getCategoryId() > 0) categories.add(request.getCategoryId());
        if (CollUtil.isNotEmpty(request.getCategoryIds())) categories.addAll(request.getCategoryIds());
        if (!categories.isEmpty()) {
            categoryService.getCategoryList(new ProductCategoryListReqVO().setParentIds(new ArrayList<>(categories)))
                    .stream().map(ProductCategoryDO::getId).forEach(categories::add);
        }
        return categories;
    }

    public Map<Long, List<Long>> getSpuGroupMap(Collection<Long> spuIds) {
        if (CollUtil.isEmpty(spuIds)) return Map.of();
        Map<Long, List<Long>> result = new LinkedHashMap<>();
        // Explicit tenant condition also protects callers when a background context ignores the interceptor.
        spuMapper.selectList(new LambdaQueryWrapperX<ProductSpuDO>().apply("tenant_id = {0}", tenantId())
                .in(ProductSpuDO::getId, spuIds)).forEach(spu -> result.put(spu.getId(), new ArrayList<>()));
        if (result.isEmpty()) return result;
        relationMapper.selectRelationsBySpuIds(tenantId(), result.keySet()).forEach(relation ->
                result.get(relation.getSpuId()).add(relation.getGroupId()));
        return result;
    }

    public PageResult<ProductGroupRespVO> getGroupPage(ProductGroupPageReqVO request) {
        var page = groupMapper.selectPage(request, new LambdaQueryWrapperX<ProductGroupDO>()
                .eq(ProductGroupDO::getTenantId, tenantId())
                .likeIfPresent(ProductGroupDO::getName, request.getName())
                .eqIfPresent(ProductGroupDO::getStatus, request.getStatus())
                .eqIfPresent(ProductGroupDO::getStorefrontVisible, request.getStorefrontVisible())
                .orderByDesc(ProductGroupDO::getSort).orderByDesc(ProductGroupDO::getId));
        PageResult<ProductGroupRespVO> response = BeanUtils.toBean(page, ProductGroupRespVO.class);
        Map<Long, ProductGroupCountRespVO> counts = page.getList().isEmpty() ? Map.of()
                : relationMapper.selectGroupCounts(tenantId(), page.getList().stream().map(ProductGroupDO::getId).toList())
                .stream().collect(Collectors.toMap(ProductGroupCountRespVO::getGroupId, Function.identity()));
        response.getList().forEach(group -> {
            ProductGroupCountRespVO count = counts.get(group.getId());
            group.setSpuCount(count == null ? 0L : count.getSpuCount());
            group.setSaleSpuCount(count == null ? 0L : count.getSaleSpuCount());
        });
        return response;
    }

    public List<ProductGroupDO> getPublicGroups(Collection<Long> ids) {
        if (ids != null && ids.isEmpty()) return List.of();
        return groupMapper.selectList(new LambdaQueryWrapperX<ProductGroupDO>()
                .eq(ProductGroupDO::getTenantId, tenantId())
                .eq(ProductGroupDO::getStatus, 0).eq(ProductGroupDO::getStorefrontVisible, true)
                .inIfPresent(ProductGroupDO::getId, ids)
                .orderByDesc(ProductGroupDO::getSort).orderByDesc(ProductGroupDO::getId));
    }

    public List<ProductGroupDO> getPublicGroupsBySpuId(Long spuId) {
        if (spuMapper.selectCount(new LambdaQueryWrapperX<ProductSpuDO>()
                .eq(ProductSpuDO::getId, spuId).apply("tenant_id = {0}", tenantId())
                .eq(ProductSpuDO::getStatus, 1)) == 0) return List.of();
        List<Long> ids = relationMapper.selectRelationsBySpuIds(tenantId(), List.of(spuId)).stream()
                .map(ProductGroupSpuDO::getGroupId).toList();
        return getPublicGroups(ids);
    }

    @Transactional(rollbackFor = Exception.class)
    public void updateSpuGroups(@Valid ProductGroupSpuUpdateReqVO request) {
        Set<Long> spuIds = new TreeSet<>(request.getSpuIds());
        Set<Long> groupIds = new TreeSet<>(request.getGroupIds());
        List<ProductSpuDO> spus = spuMapper.selectSpusForUpdate(tenantId(), spuIds);
        if (spus.size() != spuIds.size()) throw exception(SPU_NOT_EXISTS);
        List<ProductGroupDO> groups = groupMapper.selectGroupsForUpdate(tenantId(), groupIds);
        if (groups.size() != groupIds.size()) throw exception(GROUP_NOT_EXISTS);
        if ("remove".equals(request.getOperation())) {
            for (Long groupId : groupIds) relationMapper.deleteByGroupIdAndSpuIds(tenantId(), groupId, spuIds);
            return;
        }
        if (groups.stream().anyMatch(group -> !group.isEnabled())) throw exception(GROUP_DISABLED);
        List<ProductGroupSpuDO> relations = new ArrayList<>();
        for (Long groupId : groupIds) {
            for (Long spuId : spuIds) relations.add(new ProductGroupSpuDO().setGroupId(groupId).setSpuId(spuId));
        }
        relationMapper.insertRelationsIfAbsent(tenantId(),
                Objects.toString(SecurityFrameworkUtils.getLoginUserId(), ""), relations);
    }

    private Long tenantId() { return TenantContextHolder.getRequiredTenantId(); }
}
