package cn.iocoder.yudao.module.product.dal.mysql.group;

import cn.iocoder.yudao.framework.test.core.ut.BaseDbUnitTest;
import cn.iocoder.yudao.module.product.controller.admin.group.vo.ProductGroupSpuPageReqVO;
import cn.iocoder.yudao.module.product.controller.admin.group.vo.ProductGroupSpuRespVO;
import cn.iocoder.yudao.module.product.controller.app.group.vo.AppProductGroupSpuPageReqVO;
import cn.iocoder.yudao.module.product.dal.dataobject.group.ProductGroupSpuDO;
import jakarta.annotation.Resource;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

import javax.sql.DataSource;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertIterableEquals;

class ProductGroupSpuMapperTest extends BaseDbUnitTest {

    @Resource
    private ProductGroupSpuMapper groupSpuMapper;
    @Resource
    private DataSource dataSource;

    @Test
    void testPhysicalDeleteAllowsReAdd() {
        ProductGroupSpuDO first = new ProductGroupSpuDO().setGroupId(10L).setSpuId(100L).setSort(0);
        first.setTenantId(9L);
        groupSpuMapper.insert(first);

        assertEquals(1, groupSpuMapper.deleteByGroupIdAndSpuIds(9L, 10L, List.of(100L)));

        ProductGroupSpuDO second = new ProductGroupSpuDO().setGroupId(10L).setSpuId(100L).setSort(0);
        second.setTenantId(9L);
        groupSpuMapper.insert(second);
        assertEquals(1L, groupSpuMapper.selectCountByGroupId(10L));
    }

    @Test
    void testAdminPageReturnsRelationSort() {
        insertSpu(100L, 1, 1);
        insertRelation(1L, 10L, 100L, 37, 1L);
        ProductGroupSpuPageReqVO reqVO = new ProductGroupSpuPageReqVO().setGroupId(10L);

        List<ProductGroupSpuRespVO> list = groupSpuMapper.selectAdminSpuPage(reqVO).getList();

        assertEquals(1, list.size());
        assertEquals(100L, list.get(0).getId());
        assertEquals(37, list.get(0).getGroupSort());
    }

    @Test
    void testAppPageUsesRelationSortAndFiltersUnavailableData() {
        insertGroup(10L, 0, 1L);
        insertGroup(11L, 0, 1L);
        insertGroup(12L, 1, 1L);
        insertSpu(100L, 1, 1);
        insertSpu(101L, 1, 2);
        insertSpu(102L, 0, 3);
        insertSpu(103L, 1, 4);
        insertRelation(1L, 10L, 100L, 5, 1L);
        insertRelation(2L, 10L, 101L, 10, 1L);
        insertRelation(3L, 11L, 100L, 1, 1L);
        insertRelation(4L, 11L, 102L, 20, 1L);
        insertRelation(5L, 12L, 103L, 20, 1L);

        AppProductGroupSpuPageReqVO reqVO = new AppProductGroupSpuPageReqVO()
                .setGroupIds(List.of(10L));
        reqVO.setPageSize(50);
        assertIterableEquals(List.of(101L, 100L), groupSpuMapper.selectAppSpuPage(reqVO).getList().stream()
                .map(spu -> spu.getId()).toList());

        reqVO.setGroupIds(List.of(10L, 11L));
        var multiGroupPage = groupSpuMapper.selectAppSpuPage(reqVO);
        assertEquals(2L, multiGroupPage.getTotal());
        assertIterableEquals(List.of(101L, 100L), multiGroupPage.getList().stream()
                .map(spu -> spu.getId()).toList());

        reqVO.setGroupIds(List.of(10L, 11L, 12L));
        assertIterableEquals(List.of(101L, 100L), groupSpuMapper.selectAppSpuPage(reqVO).getList().stream()
                .map(spu -> spu.getId()).toList());

        jdbc().update("UPDATE product_spu SET price = 200, sales_count = 20, virtual_sales_count = 1, "
                + "create_time = '2026-01-01 00:00:00' WHERE id = 100");
        jdbc().update("UPDATE product_spu SET price = 100, sales_count = 10, virtual_sales_count = 1, "
                + "create_time = '2026-02-01 00:00:00' WHERE id = 101");
        reqVO.setGroupIds(List.of(10L, 11L));
        reqVO.setSortField(AppProductGroupSpuPageReqVO.SORT_FIELD_PRICE).setSortAsc(true);
        assertIterableEquals(List.of(101L, 100L), groupSpuMapper.selectAppSpuPage(reqVO).getList().stream()
                .map(spu -> spu.getId()).toList());
        reqVO.setSortField(AppProductGroupSpuPageReqVO.SORT_FIELD_SALES_COUNT).setSortAsc(false);
        assertIterableEquals(List.of(100L, 101L), groupSpuMapper.selectAppSpuPage(reqVO).getList().stream()
                .map(spu -> spu.getId()).toList());
        reqVO.setSortField(AppProductGroupSpuPageReqVO.SORT_FIELD_CREATE_TIME).setSortAsc(false);
        assertIterableEquals(List.of(101L, 100L), groupSpuMapper.selectAppSpuPage(reqVO).getList().stream()
                .map(spu -> spu.getId()).toList());
    }

    @Test
    void testPhysicalDeleteIsTenantScoped() {
        insertRelation(1L, 10L, 100L, 0, 8L);
        insertRelation(2L, 10L, 100L, 0, 9L);

        assertEquals(1, groupSpuMapper.deleteByGroupIdAndSpuIds(9L, 10L, List.of(100L)));
        assertEquals(1, jdbc().queryForObject(
                "SELECT COUNT(*) FROM product_group_spu WHERE group_id = 10 AND spu_id = 100", Integer.class));
    }

    private void insertGroup(Long id, int status, Long tenantId) {
        jdbc().update("INSERT INTO product_group (id, name, sort, status, deleted, tenant_id) VALUES (?, ?, 0, ?, FALSE, ?)",
                id, "group-" + id, status, tenantId);
    }

    private void insertSpu(Long id, int status, int sort) {
        jdbc().update("INSERT INTO product_spu (id, name, keyword, introduction, description, bar_code, category_id, "
                        + "pic_url, unit, sort, status, spec_type, price, market_price, cost_price, stock, delivery_template_id, "
                        + "recommend_hot, recommend_benefit, recommend_best, recommend_new, recommend_good, give_integral, "
                        + "sub_commission_type, tenant_id) VALUES (?, ?, '', '', '', '', 1, '', 0, ?, ?, FALSE, 100, 100, 100, "
                        + "1, 1, FALSE, FALSE, FALSE, FALSE, FALSE, 0, FALSE, 1)", id, "spu-" + id, sort, status);
    }

    private void insertRelation(Long id, Long groupId, Long spuId, int sort, Long tenantId) {
        jdbc().update("INSERT INTO product_group_spu (id, group_id, spu_id, sort, deleted, tenant_id) "
                        + "VALUES (?, ?, ?, ?, FALSE, ?)", id, groupId, spuId, sort, tenantId);
    }

    private JdbcTemplate jdbc() {
        return new JdbcTemplate(dataSource);
    }

}
