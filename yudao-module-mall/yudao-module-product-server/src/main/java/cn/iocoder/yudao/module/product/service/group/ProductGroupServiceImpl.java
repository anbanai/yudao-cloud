package cn.iocoder.yudao.module.product.service.group;

import cn.hutool.core.collection.CollUtil;
import cn.iocoder.yudao.framework.common.enums.CommonStatusEnum;
import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.framework.common.util.object.BeanUtils;
import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.product.controller.admin.group.vo.*;
import cn.iocoder.yudao.module.product.controller.app.group.vo.AppProductGroupSpuPageReqVO;
import cn.iocoder.yudao.module.product.dal.dataobject.group.ProductGroupDO;
import cn.iocoder.yudao.module.product.dal.dataobject.group.ProductGroupSpuDO;
import cn.iocoder.yudao.module.product.dal.dataobject.spu.ProductSpuDO;
import cn.iocoder.yudao.module.product.dal.mysql.group.ProductGroupMapper;
import cn.iocoder.yudao.module.product.dal.mysql.group.ProductGroupSpuMapper;
import cn.iocoder.yudao.module.product.dal.mysql.spu.ProductSpuMapper;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.validation.annotation.Validated;

import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.yudao.module.product.enums.ErrorCodeConstants.*;

@Service
@Validated
public class ProductGroupServiceImpl implements ProductGroupService {

    @Resource
    private ProductGroupMapper groupMapper;
    @Resource
    private ProductGroupSpuMapper groupSpuMapper;
    @Resource
    private ProductSpuMapper spuMapper;

    @Override
    public Long createGroup(ProductGroupSaveReqVO reqVO) {
        validateNameUnique(null, reqVO.getName());
        ProductGroupDO group = BeanUtils.toBean(reqVO, ProductGroupDO.class);
        groupMapper.insert(group);
        return group.getId();
    }

    @Override
    public void updateGroup(ProductGroupSaveReqVO reqVO) {
        validateExists(reqVO.getId());
        validateNameUnique(reqVO.getId(), reqVO.getName());
        groupMapper.updateById(BeanUtils.toBean(reqVO, ProductGroupDO.class));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteGroup(Long id) {
        validateExists(id);
        if (groupSpuMapper.selectCountByGroupId(id) > 0) {
            throw exception(GROUP_HAVE_BIND_SPU);
        }
        groupMapper.deleteById(id);
    }

    private ProductGroupDO validateExists(Long id) {
        ProductGroupDO group = groupMapper.selectById(id);
        if (group == null) {
            throw exception(GROUP_NOT_EXISTS);
        }
        return group;
    }

    private void validateNameUnique(Long id, String name) {
        ProductGroupDO group = groupMapper.selectByName(name);
        if (group != null && !Objects.equals(id, group.getId())) {
            throw exception(GROUP_NAME_EXISTS);
        }
    }

    private void validateEnabledGroups(Collection<Long> groupIds) {
        if (CollUtil.isEmpty(groupIds)) {
            return;
        }
        List<ProductGroupDO> groups = groupMapper.selectByIds(groupIds);
        Map<Long, ProductGroupDO> groupMap = groups.stream()
                .collect(Collectors.toMap(ProductGroupDO::getId, Function.identity()));
        for (Long groupId : groupIds) {
            ProductGroupDO group = groupMap.get(groupId);
            if (group == null) {
                throw exception(GROUP_NOT_EXISTS);
            }
            if (!group.isEnabled()) {
                throw exception(GROUP_DISABLED);
            }
        }
    }

    @Override
    public ProductGroupDO getGroup(Long id) {
        return groupMapper.selectById(id);
    }

    @Override
    public PageResult<ProductGroupDO> getGroupPage(ProductGroupPageReqVO reqVO) {
        return groupMapper.selectPage(reqVO);
    }

    @Override
    public List<ProductGroupDO> getGroupList(Collection<Long> ids, boolean onlyEnabled) {
        List<ProductGroupDO> groups = CollUtil.isEmpty(ids)
                ? (onlyEnabled ? groupMapper.selectListByStatus(CommonStatusEnum.ENABLE.getStatus()) : groupMapper.selectList())
                : groupMapper.selectByIds(ids);
        if (onlyEnabled) {
            groups.removeIf(group -> !group.isEnabled());
        }
        groups.sort(Comparator.comparing(ProductGroupDO::getSort).reversed()
                .thenComparing(ProductGroupDO::getId, Comparator.reverseOrder()));
        return groups;
    }

    @Override
    public List<Long> getGroupIdsBySpuId(Long spuId) {
        return groupSpuMapper.selectListBySpuId(spuId).stream()
                .map(ProductGroupSpuDO::getGroupId).toList();
    }

    @Override
    public PageResult<ProductGroupSpuRespVO> getAdminSpuPage(ProductGroupSpuPageReqVO reqVO) {
        validateExists(reqVO.getGroupId());
        return groupSpuMapper.selectAdminSpuPage(reqVO);
    }

    @Override
    public PageResult<ProductSpuDO> getAppSpuPage(AppProductGroupSpuPageReqVO reqVO) {
        validateEnabledGroups(new LinkedHashSet<>(reqVO.getGroupIds()));
        return groupSpuMapper.selectAppSpuPage(reqVO);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void addSpus(ProductGroupSpuBatchReqVO reqVO) {
        ProductGroupDO group = validateExists(reqVO.getGroupId());
        if (!group.isEnabled()) {
            throw exception(GROUP_DISABLED);
        }
        LinkedHashSet<Long> spuIds = new LinkedHashSet<>(reqVO.getSpuIds());
        if (spuMapper.selectByIds(spuIds).size() != spuIds.size()) {
            throw exception(SPU_NOT_EXISTS);
        }
        Set<Long> existing = groupSpuMapper.selectListByGroupId(reqVO.getGroupId()).stream()
                .map(ProductGroupSpuDO::getSpuId).collect(Collectors.toSet());
        List<ProductGroupSpuDO> relations = spuIds.stream().filter(id -> !existing.contains(id))
                .map(spuId -> new ProductGroupSpuDO().setGroupId(reqVO.getGroupId()).setSpuId(spuId).setSort(0))
                .toList();
        if (CollUtil.isNotEmpty(relations)) {
            groupSpuMapper.insertBatch(relations);
        }
    }

    @Override
    public void removeSpus(ProductGroupSpuBatchReqVO reqVO) {
        validateExists(reqVO.getGroupId());
        groupSpuMapper.deleteByGroupIdAndSpuIds(TenantContextHolder.getRequiredTenantId(),
                reqVO.getGroupId(), new LinkedHashSet<>(reqVO.getSpuIds()));
    }

    @Override
    public void updateSpuSort(ProductGroupSpuSortReqVO reqVO) {
        if (groupSpuMapper.updateSort(reqVO.getGroupId(), reqVO.getSpuId(), reqVO.getSort()) == 0) {
            throw exception(GROUP_NOT_EXISTS);
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void syncSpuGroups(Long spuId, List<Long> groupIds) {
        if (groupIds == null) {
            return;
        }
        LinkedHashSet<Long> targetIds = new LinkedHashSet<>(groupIds);
        Set<Long> currentIds = groupSpuMapper.selectListBySpuId(spuId).stream()
                .map(ProductGroupSpuDO::getGroupId).collect(Collectors.toCollection(LinkedHashSet::new));
        List<Long> addIds = targetIds.stream().filter(id -> !currentIds.contains(id)).toList();
        List<Long> removeIds = currentIds.stream().filter(id -> !targetIds.contains(id)).toList();
        validateEnabledGroups(addIds);
        if (CollUtil.isNotEmpty(removeIds)) {
            groupSpuMapper.deleteBySpuIdAndGroupIds(TenantContextHolder.getRequiredTenantId(), spuId, removeIds);
        }
        if (CollUtil.isNotEmpty(addIds)) {
            groupSpuMapper.insertBatch(addIds.stream()
                    .map(groupId -> new ProductGroupSpuDO().setGroupId(groupId).setSpuId(spuId).setSort(0))
                    .toList());
        }
    }

    @Override
    public void deleteRelationsBySpuId(Long spuId) {
        groupSpuMapper.deleteBySpuId(TenantContextHolder.getRequiredTenantId(), spuId);
    }
}
