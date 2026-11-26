package cn.iocoder.yudao.module.product.service.group;

import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.framework.test.core.ut.BaseDbUnitTest;
import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.product.controller.admin.category.vo.ProductCategoryListReqVO;
import cn.iocoder.yudao.module.product.controller.admin.group.vo.*;
import cn.iocoder.yudao.module.product.controller.app.group.vo.AppProductGroupSpuPageReqVO;
import cn.iocoder.yudao.module.product.controller.app.spu.vo.AppProductSpuPageReqVO;
import cn.iocoder.yudao.module.product.dal.dataobject.category.ProductCategoryDO;
import cn.iocoder.yudao.module.product.dal.dataobject.spu.ProductSpuDO;
import cn.iocoder.yudao.module.product.service.category.ProductCategoryService;
import cn.iocoder.yudao.module.product.service.spu.ProductSpuService;
import jakarta.annotation.Resource;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.validation.beanvalidation.MethodValidationPostProcessor;

import javax.sql.DataSource;
import java.util.List;
import java.util.Map;

import static cn.iocoder.yudao.framework.test.core.util.AssertUtils.assertServiceException;
import static cn.iocoder.yudao.module.product.enums.ErrorCodeConstants.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@Import({ProductGroupOperationsService.class, ProductGroupServiceImpl.class, MethodValidationPostProcessor.class})
class ProductGroupOperationsServiceTest extends BaseDbUnitTest {

    @Resource private ProductGroupOperationsService operations;
    @Resource private ProductGroupService groupService;
    @Resource private DataSource dataSource;
    @MockBean private ProductCategoryService categoryService;
    @MockBean private ProductSpuService productSpuService;

    @BeforeEach
    void prepare() {
        TenantContextHolder.setTenantId(1L);
        jdbc().update("INSERT INTO product_merchant (id, name, status, tenant_id, deleted)"
                + " VALUES (1, 'platform-merchant', 20, 1, FALSE)");
        group(10, true, 0, 1);
        group(20, false, 0, 1);
        group(30, true, 1, 1);
        group(40, true, 0, 2);
        spu(100, 1, 2, 1);
        spu(101, 0, 2, 1);
        spu(102, 1, 3, 1);
        spu(103, 1, 2, 2);
        relation(10, 100, 1);
        relation(20, 100, 1);
        relation(20, 101, 1);
        relation(40, 103, 2);
    }

    @AfterEach
    void clearTenant() {
        TenantContextHolder.clear();
    }

    @Test
    void filterDeduplicatesIntersectsCategoryAndKeepsCountAndExportConsistent() {
        when(categoryService.getCategoryList(any(ProductCategoryListReqVO.class)))
                .thenReturn(List.of(new ProductCategoryDO().setId(2L)));
        ProductGroupSpuFilterReqVO query = new ProductGroupSpuFilterReqVO();
        query.setGroupIds(List.of(10L, 20L));
        query.setCategoryId(1L);
        query.setName("tea");
        query.setTabType(0);
        var page = operations.getSpuPage(query);
        assertEquals(1L, page.getTotal());
        assertEquals(100L, page.getList().get(0).getId());
        assertEquals(List.of(10L, 20L), page.getList().get(0).getGroupIds());
        assertEquals(1L, operations.getSpuCounts(query).get(0));
        assertEquals(1L, operations.getSpuCounts(query).get(1));
        assertEquals(List.of(100L), operations.getSpuExportList(query).stream().map(spu -> spu.getId()).toList());
        assertEquals(10, query.getPageSize());
    }

    @Test
    void ungroupedUsesTenantScopedRelationsAndCountsAllMemberships() {
        relation(40, 102, 2); // Foreign tenant relation cannot hide this tenant's ungrouped product.
        ProductGroupSpuFilterReqVO query = new ProductGroupSpuFilterReqVO();
        query.setUngrouped(true);
        assertEquals(List.of(102L), operations.getSpuPage(query).getList().stream().map(spu -> spu.getId()).toList());
        assertEquals(Map.of(100L, List.of(10L, 20L), 102L, List.of()),
                operations.getSpuGroupMap(List.of(100L, 102L, 103L)));
    }

    @Test
    void storefrontMetadataRequiresExplicitIds() {
        assertTrue(operations.getEnabledGroupsByIds(null).isEmpty());
        assertTrue(operations.getEnabledGroupsByIds(List.of()).isEmpty());
    }

    @Test
    void storefrontCanUseAnyEnabledGroupOfTheTenant() {
        assertEquals(List.of(20L, 10L), operations.getEnabledGroupsByIds(List.of(10L, 20L, 30L, 40L))
                .stream().map(group -> group.getId()).toList());
        assertEquals(List.of(100L), groupService.getAppSpuPage(
                new AppProductGroupSpuPageReqVO().setGroupIds(List.of(20L)))
                .getList().stream().map(spu -> spu.getId()).toList());
        assertServiceException(() -> groupService.getAppSpuPage(
                new AppProductGroupSpuPageReqVO().setGroupIds(List.of(30L))), GROUP_DISABLED);
        assertServiceException(() -> groupService.getAppSpuPage(
                new AppProductGroupSpuPageReqVO().setGroupIds(List.of(40L))), GROUP_NOT_EXISTS);
    }

    @Test
    void storefrontProductPageAppliesProductVisibilityToLegacyGroups() {
        PageResult<ProductSpuDO> page = new PageResult<>(List.of(new ProductSpuDO().setId(100L)), 1L);
        when(productSpuService.getVisibleSpuPage(any(AppProductSpuPageReqVO.class), isNull()))
                .thenReturn(page);
        AppProductGroupSpuPageReqVO request = new AppProductGroupSpuPageReqVO().setGroupIds(List.of(20L));
        request.setPageNo(2);
        request.setPageSize(5);

        assertSame(page, groupService.getVisibleAppSpuPage(request));
        AppProductSpuPageReqVO expected = new AppProductSpuPageReqVO().setGroupIds(List.of(20L));
        expected.setPageNo(2);
        expected.setPageSize(5);
        verify(productSpuService).getVisibleSpuPage(expected, null);
    }

    @Test
    void storefrontProductPageRejectsDisabledAndForeignGroups() {
        assertServiceException(() -> groupService.getVisibleAppSpuPage(
                new AppProductGroupSpuPageReqVO().setGroupIds(List.of(20L, 30L))), GROUP_DISABLED);
        assertServiceException(() -> groupService.getVisibleAppSpuPage(
                new AppProductGroupSpuPageReqVO().setGroupIds(List.of(40L))), GROUP_NOT_EXISTS);
        verifyNoInteractions(productSpuService);
    }

    @Test
    void batchAddAndRemovePreserveOtherMembershipsPrimaryCategoryAndAllowReAdd() {
        ProductGroupSpuUpdateReqVO request = new ProductGroupSpuUpdateReqVO()
                .setSpuIds(List.of(100L, 102L, 102L)).setGroupIds(List.of(10L, 20L)).setOperation("add");
        operations.updateSpuGroups(request);
        operations.updateSpuGroups(request);
        assertEquals(List.of(10L, 20L), operations.getSpuGroupMap(List.of(102L)).get(102L));
        assertEquals(3L, jdbc().queryForObject("SELECT category_id FROM product_spu WHERE id = 102", Long.class));
        request.setGroupIds(List.of(10L)).setOperation("remove");
        operations.updateSpuGroups(request);
        assertEquals(List.of(20L), operations.getSpuGroupMap(List.of(102L)).get(102L));
        operations.updateSpuGroups(request.setOperation("add"));
        assertEquals(List.of(10L, 20L), operations.getSpuGroupMap(List.of(102L)).get(102L));
    }

    @Test
    void invalidBatchDoesNotPartiallyChangeMemberships() {
        ProductGroupSpuUpdateReqVO request = new ProductGroupSpuUpdateReqVO()
                .setSpuIds(List.of(102L)).setGroupIds(List.of(10L, 30L)).setOperation("add");
        assertServiceException(() -> operations.updateSpuGroups(request), GROUP_DISABLED);
        assertEquals(List.of(), operations.getSpuGroupMap(List.of(102L)).get(102L));
        request.setGroupIds(List.of(10L)).setSpuIds(List.of(102L, 103L));
        assertServiceException(() -> operations.updateSpuGroups(request), SPU_NOT_EXISTS);
        assertEquals(List.of(), operations.getSpuGroupMap(List.of(102L)).get(102L));
    }

    @Test
    void disabledGroupCanBeRemovedAndCountsUseSameTenant() {
        relation(30, 100, 1);
        operations.updateSpuGroups(new ProductGroupSpuUpdateReqVO().setOperation("remove")
                .setSpuIds(List.of(100L)).setGroupIds(List.of(30L)));
        var groups = operations.getGroupPage(new ProductGroupPageReqVO()).getList();
        var group = groups.stream().filter(item -> item.getId().equals(20L)).findFirst().orElseThrow();
        assertEquals(2L, group.getSpuCount());
        assertEquals(1L, group.getSaleSpuCount());
        assertEquals(3, groups.size());
    }

    @Test
    void groupSaveWorksWithLegacyVisibilityColumn() {
        groupService.updateGroup(new ProductGroupSaveReqVO().setId(20L).setName("renamed")
                .setSort(0).setStatus(0));
        assertEquals("renamed", groupService.getGroup(20L).getName());
        Long id = groupService.createGroup(new ProductGroupSaveReqVO().setName("new group")
                .setSort(0).setStatus(0));
        assertEquals("new group", groupService.getGroup(id).getName());
    }

    private JdbcTemplate jdbc() { return new JdbcTemplate(dataSource); }

    private void group(long id, boolean visible, int status, long tenant) {
        jdbc().update("INSERT INTO product_group (id,name,sort,status,storefront_visible,tenant_id) VALUES (?,?,0,?,?,?)",
                id, "group-" + id, status, visible, tenant);
    }

    private void relation(long group, long spu, long tenant) {
        jdbc().update("INSERT INTO product_group_spu (group_id,spu_id,sort,tenant_id) VALUES (?,?,0,?)", group, spu, tenant);
    }

    private void spu(long id, int status, long category, long tenant) {
        jdbc().update("INSERT INTO product_spu (id,name,keyword,introduction,description,bar_code,category_id,merchant_id,"
                + "pic_url,unit,sort,status,spec_type,price,market_price,cost_price,stock,delivery_template_id,"
                + "recommend_hot,recommend_benefit,recommend_best,recommend_new,recommend_good,give_integral,"
                + "sub_commission_type,tenant_id) VALUES (?,?,'','','','',?,1,'',0,0,?,FALSE,100,100,100,"
                + "1,1,FALSE,FALSE,FALSE,FALSE,FALSE,0,FALSE,?)", id, "tea-" + id, category, status, tenant);
    }
}
