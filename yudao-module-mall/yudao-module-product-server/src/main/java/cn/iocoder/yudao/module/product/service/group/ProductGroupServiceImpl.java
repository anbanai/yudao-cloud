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
import cn.iocoder.yudao.module.product.dal.mysql.group.ProductGroupOperationsMapper;
import cn.iocoder.yudao.module.product.dal.mysql.group.ProductGroupSpuMapper;
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
    private ProductGroupOperationsMapper spuMapper;

    @Override
    public Long createGroup(ProductGroupSaveReqVO reqVO) {
        validateNameUnique(null, reqVO.getName());
        ProductGroupDO group = BeanUtils.toBean(reqVO, ProductGroupDO.class);
        if (group.getStorefrontVisible() == null) {
            group.setStorefrontVisible(true);
        }
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
        validateExistsForUpdate(id);
        if (!groupSpuMapper.selectListByGroupIdForUpdate(TenantContextHolder.getRequiredTenantId(), id).isEmpty()) {
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

    private ProductGroupDO validateExistsForUpdate(Long id) {
        List<ProductGroupDO> groups = groupMapper.selectGroupsForUpdate(
                TenantContextHolder.getRequiredTenantId(), List.of(id));
        if (groups.isEmpty()) {
            throw exception(GROUP_NOT_EXISTS);
        }
        return groups.get(0);
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
        for (ProductGroupDO group : groupMapper.selectByIds(reqVO.getGroupIds())) {
            if (!Boolean.TRUE.equals(group.getStorefrontVisible())
                    || !Objects.equals(group.getTenantId(), TenantContextHolder.getRequiredTenantId())) {
                throw exception(GROUP_NOT_EXISTS);
            }
        }
        return groupSpuMapper.selectAppSpuPage(reqVO);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void addSpus(ProductGroupSpuBatchReqVO reqVO) {
        LinkedHashSet<Long> spuIds = new LinkedHashSet<>(reqVO.getSpuIds());
        Long tenantId = TenantContextHolder.getRequiredTenantId();
        if (spuMapper.selectSpusForUpdate(tenantId, spuIds).size() != spuIds.size()) {
            throw exception(SPU_NOT_EXISTS);
        }
        ProductGroupDO group = validateExistsForUpdate(reqVO.getGroupId());
        if (!group.isEnabled()) {
            throw exception(GROUP_DISABLED);
        }
        Set<Long> existing = groupSpuMapper.selectListByGroupIdForUpdate(tenantId, reqVO.getGroupId()).stream()
                .map(ProductGroupSpuDO::getSpuId).collect(Collectors.toSet());
        List<ProductGroupSpuDO> relations = spuIds.stream().filter(id -> !existing.contains(id))
                .map(spuId -> new ProductGroupSpuDO().setGroupId(reqVO.getGroupId()).setSpuId(spuId).setSort(0))
                .toList();
        if (CollUtil.isNotEmpty(relations)) {
            groupSpuMapper.insertBatch(relations);
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void removeSpus(ProductGroupSpuBatchReqVO reqVO) {
        // 旧移除接口允许重复或不存在的商品 ID，锁住仍存在的商品即可。
        spuMapper.selectSpusForUpdate(TenantContextHolder.getRequiredTenantId(), new TreeSet<>(reqVO.getSpuIds()));
        validateExistsForUpdate(reqVO.getGroupId());
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
        Long tenantId = TenantContextHolder.getRequiredTenantId();
        if (spuMapper.selectSpusForUpdate(tenantId, List.of(spuId)).isEmpty()) {
            throw exception(SPU_NOT_EXISTS);
        }
        // 先锁目标分组，再读取关系；移出的分组不需要再获取行锁，避免 group/关系反向等待。
        Map<Long, ProductGroupDO> targetGroups = groupMapper.selectGroupsForUpdate(tenantId, new TreeSet<>(targetIds))
                .stream().collect(Collectors.toMap(ProductGroupDO::getId, Function.identity()));
        Set<Long> currentIds = groupSpuMapper.selectListBySpuIdForUpdate(tenantId, spuId).stream()
                .map(ProductGroupSpuDO::getGroupId).collect(Collectors.toCollection(LinkedHashSet::new));
        List<Long> addIds = targetIds.stream().filter(id -> !currentIds.contains(id)).toList();
        List<Long> removeIds = currentIds.stream().filter(id -> !targetIds.contains(id)).toList();
        for (Long groupId : addIds) {
            ProductGroupDO group = targetGroups.get(groupId);
            if (group == null) throw exception(GROUP_NOT_EXISTS);
            if (!group.isEnabled()) throw exception(GROUP_DISABLED);
        }
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
    @Transactional(rollbackFor = Exception.class)
    public void deleteRelationsBySpuId(Long spuId) {
        // 商品删除的外层事务已持有该行锁；直接调用时也与成员写入串行化。
        spuMapper.selectSpusForUpdate(TenantContextHolder.getRequiredTenantId(), List.of(spuId));
        groupSpuMapper.deleteBySpuId(TenantContextHolder.getRequiredTenantId(), spuId);
    }
}
