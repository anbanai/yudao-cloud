package cn.iocoder.yudao.module.product.dal.mysql.spu;

import cn.iocoder.yudao.framework.test.core.ut.BaseDbUnitTest;
import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.product.controller.app.spu.vo.AppProductSpuPageReqVO;
import jakarta.annotation.Resource;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

import javax.sql.DataSource;
import java.util.Collections;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ProductSpuMapperMerchantVisibilityTest extends BaseDbUnitTest {

    @Resource
    private ProductSpuMapper spuMapper;
    @Resource
    private DataSource dataSource;

    @BeforeEach
    void setTenant() {
        TenantContextHolder.setTenantId(1L);
    }

    @AfterEach
    void clearTenant() {
        TenantContextHolder.clear();
    }

    @Test
    void testAppPageOnlyReturnsProductsFromApprovedMerchants() {
        JdbcTemplate jdbc = new JdbcTemplate(dataSource);
        insertMerchant(jdbc, 100L, 20);
        insertMerchant(jdbc, 200L, 50);
        insertSpu(jdbc, 1000L, 100L);
        insertSpu(jdbc, 2000L, 200L);

        var result = spuMapper.selectPage(new AppProductSpuPageReqVO(), Collections.emptySet());

        assertEquals(1L, result.getTotal());
        assertEquals(1000L, result.getList().get(0).getId());
    }

    private void insertMerchant(JdbcTemplate jdbc, Long id, Integer status) {
        jdbc.update("INSERT INTO product_merchant (id, name, status, tenant_id, deleted) VALUES (?, ?, ?, 1, FALSE)",
                id, "merchant-" + id, status);
    }

    private void insertSpu(JdbcTemplate jdbc, Long id, Long merchantId) {
        jdbc.update("INSERT INTO product_spu (id, name, keyword, introduction, description, bar_code, category_id, "
                        + "merchant_id, pic_url, unit, sort, status, spec_type, price, market_price, cost_price, stock, "
                        + "delivery_template_id, recommend_hot, recommend_benefit, recommend_best, recommend_new, "
                        + "recommend_good, give_integral, sub_commission_type, tenant_id) "
                        + "VALUES (?, ?, '', '', '', '', 1, ?, '', 0, 0, 1, FALSE, 100, 100, 100, 1, 1, FALSE, FALSE, "
                        + "FALSE, FALSE, FALSE, 0, FALSE, 1)", id, "spu-" + id, merchantId);
    }
}
