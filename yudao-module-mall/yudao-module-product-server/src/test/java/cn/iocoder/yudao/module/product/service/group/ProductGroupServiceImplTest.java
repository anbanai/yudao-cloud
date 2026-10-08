package cn.iocoder.yudao.module.product.service.group;

import cn.iocoder.yudao.framework.common.enums.CommonStatusEnum;
import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.product.controller.admin.group.vo.ProductGroupSpuBatchReqVO;
import cn.iocoder.yudao.module.product.dal.dataobject.group.ProductGroupDO;
import cn.iocoder.yudao.module.product.dal.dataobject.group.ProductGroupSpuDO;
import cn.iocoder.yudao.module.product.dal.dataobject.spu.ProductSpuDO;
import cn.iocoder.yudao.module.product.dal.mysql.group.ProductGroupMapper;
import cn.iocoder.yudao.module.product.dal.mysql.group.ProductGroupOperationsMapper;
import cn.iocoder.yudao.module.product.dal.mysql.group.ProductGroupSpuMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.stream.LongStream;

import static cn.iocoder.yudao.framework.test.core.util.AssertUtils.assertServiceException;
import static cn.iocoder.yudao.module.product.enums.ErrorCodeConstants.GROUP_HAVE_BIND_SPU;
import static cn.iocoder.yudao.module.product.enums.ErrorCodeConstants.GROUP_DISABLED;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ProductGroupServiceImplTest {

    @InjectMocks
    private ProductGroupServiceImpl groupService;

    @Mock
    private ProductGroupMapper groupMapper;
    @Mock
    private ProductGroupSpuMapper groupSpuMapper;
    @Mock
    private ProductGroupOperationsMapper spuMapper;

    @AfterEach
    void tearDown() {
        TenantContextHolder.clear();
    }

    @Test
    void testSyncSpuGroupsNullKeepsExistingRelations() {
        groupService.syncSpuGroups(100L, null);

        verifyNoInteractions(groupMapper, groupSpuMapper, spuMapper);
    }

    @Test
    void testSyncSpuGroupsReplacesRelationsAndValidatesOnlyNewGroups() {
        TenantContextHolder.setTenantId(9L);
        when(spuMapper.selectSpusForUpdate(9L, List.of(100L))).thenReturn(List.of(new ProductSpuDO().setId(100L)));
        when(groupSpuMapper.selectListBySpuIdForUpdate(9L, 100L)).thenReturn(List.of(
                new ProductGroupSpuDO().setGroupId(1L).setSpuId(100L),
                new ProductGroupSpuDO().setGroupId(2L).setSpuId(100L)));
        when(groupMapper.selectGroupsForUpdate(9L, Set.of(2L, 3L))).thenReturn(List.of(
                new ProductGroupDO().setId(2L).setStatus(CommonStatusEnum.DISABLE.getStatus()),
                new ProductGroupDO().setId(3L).setStatus(CommonStatusEnum.ENABLE.getStatus())));

        groupService.syncSpuGroups(100L, List.of(2L, 3L, 3L));

        verify(groupSpuMapper).deleteBySpuIdAndGroupIds(9L, 100L, List.of(1L));
        @SuppressWarnings("unchecked")
        ArgumentCaptor<Collection<ProductGroupSpuDO>> insertCaptor = ArgumentCaptor.forClass(Collection.class);
        verify(groupSpuMapper).insertBatch(insertCaptor.capture());
        assertEquals(1, insertCaptor.getValue().size());
        ProductGroupSpuDO relation = insertCaptor.getValue().iterator().next();
        assertEquals(3L, relation.getGroupId());
        assertEquals(100L, relation.getSpuId());
        assertEquals(0, relation.getSort());
    }

    @Test
    void testSyncSpuGroupsEmptyClearsRelations() {
        TenantContextHolder.setTenantId(9L);
        when(spuMapper.selectSpusForUpdate(9L, List.of(100L))).thenReturn(List.of(new ProductSpuDO().setId(100L)));
        when(groupSpuMapper.selectListBySpuIdForUpdate(9L, 100L)).thenReturn(List.of(
                new ProductGroupSpuDO().setGroupId(1L).setSpuId(100L)));

        groupService.syncSpuGroups(100L, List.of());

        verify(groupSpuMapper).deleteBySpuIdAndGroupIds(9L, 100L, List.of(1L));
        verify(groupSpuMapper, never()).insertBatch(anyCollection());
    }

    @Test
    void testAddSpusDeduplicatesAndSkipsExistingRelations() {
        TenantContextHolder.setTenantId(9L);
        when(groupMapper.selectGroupsForUpdate(9L, List.of(10L))).thenReturn(List.of(
                new ProductGroupDO().setId(10L).setStatus(CommonStatusEnum.ENABLE.getStatus())));
        when(spuMapper.selectSpusForUpdate(eq(9L), anyCollection())).thenReturn(List.of(
                new ProductSpuDO().setId(100L), new ProductSpuDO().setId(101L)));
        when(groupSpuMapper.selectListByGroupIdForUpdate(9L, 10L)).thenReturn(List.of(
                new ProductGroupSpuDO().setGroupId(10L).setSpuId(100L)));

        groupService.addSpus(new ProductGroupSpuBatchReqVO()
                .setGroupId(10L).setSpuIds(List.of(100L, 101L, 101L)));

        @SuppressWarnings("unchecked")
        ArgumentCaptor<Collection<ProductGroupSpuDO>> captor = ArgumentCaptor.forClass(Collection.class);
        verify(groupSpuMapper).insertBatch(captor.capture());
        assertEquals(List.of(101L), captor.getValue().stream().map(ProductGroupSpuDO::getSpuId).toList());
    }

    @Test
    void testRemoveSpusUsesTenantScopedPhysicalDelete() {
        TenantContextHolder.setTenantId(9L);
        when(groupMapper.selectGroupsForUpdate(9L, List.of(10L))).thenReturn(List.of(new ProductGroupDO().setId(10L)));

        groupService.removeSpus(new ProductGroupSpuBatchReqVO()
                .setGroupId(10L).setSpuIds(List.of(100L, 100L, 101L)));

        @SuppressWarnings("unchecked")
        ArgumentCaptor<Collection<Long>> captor = ArgumentCaptor.forClass(Collection.class);
        verify(groupSpuMapper).deleteByGroupIdAndSpuIds(eq(9L), eq(10L), captor.capture());
        assertEquals(List.of(100L, 101L), List.copyOf(captor.getValue()));
    }

    @Test
    void testDeleteGroupRejectsNonEmptyGroup() {
        TenantContextHolder.setTenantId(9L);
        when(groupMapper.selectGroupsForUpdate(9L, List.of(10L))).thenReturn(List.of(new ProductGroupDO().setId(10L)));
        when(groupSpuMapper.selectListByGroupIdForUpdate(9L, 10L)).thenReturn(List.of(
                new ProductGroupSpuDO().setGroupId(10L).setSpuId(100L)));

        assertServiceException(() -> groupService.deleteGroup(10L), GROUP_HAVE_BIND_SPU);

        verify(groupMapper, never()).deleteById(10L);
    }

    @Test
    void testSyncSpuGroupsRejectsNewDisabledMembershipWithoutRemovingExistingMemberships() {
        TenantContextHolder.setTenantId(9L);
        when(spuMapper.selectSpusForUpdate(9L, List.of(100L))).thenReturn(List.of(new ProductSpuDO().setId(100L)));
        when(groupMapper.selectGroupsForUpdate(9L, Set.of(2L))).thenReturn(List.of(
                new ProductGroupDO().setId(2L).setStatus(CommonStatusEnum.DISABLE.getStatus())));
        when(groupSpuMapper.selectListBySpuIdForUpdate(9L, 100L)).thenReturn(List.of(
                new ProductGroupSpuDO().setGroupId(1L).setSpuId(100L)));

        assertServiceException(() -> groupService.syncSpuGroups(100L, List.of(2L)), GROUP_DISABLED);

        verify(groupSpuMapper, never()).deleteBySpuIdAndGroupIds(eq(9L), eq(100L), anyCollection());
        verify(groupSpuMapper, never()).insertBatch(anyCollection());
    }

    @Test
    void testLegacyAddStillAcceptsOneThousandProducts() {
        TenantContextHolder.setTenantId(9L);
        List<Long> ids = LongStream.rangeClosed(1, 1000).boxed().toList();
        when(spuMapper.selectSpusForUpdate(eq(9L), anyCollection())).thenReturn(ids.stream()
                .map(id -> new ProductSpuDO().setId(id)).toList());
        when(groupMapper.selectGroupsForUpdate(9L, List.of(10L))).thenReturn(List.of(
                new ProductGroupDO().setId(10L).setStatus(CommonStatusEnum.ENABLE.getStatus())));

        groupService.addSpus(new ProductGroupSpuBatchReqVO().setGroupId(10L).setSpuIds(ids));

        @SuppressWarnings("unchecked")
        ArgumentCaptor<Collection<ProductGroupSpuDO>> captor = ArgumentCaptor.forClass(Collection.class);
        verify(groupSpuMapper).insertBatch(captor.capture());
        assertEquals(1000, captor.getValue().size());
    }

}
