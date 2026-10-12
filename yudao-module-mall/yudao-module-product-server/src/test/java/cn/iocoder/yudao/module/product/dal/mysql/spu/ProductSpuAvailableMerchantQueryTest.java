package cn.iocoder.yudao.module.product.dal.mysql.spu;

import cn.iocoder.yudao.framework.test.core.ut.BaseDbUnitTest;
import cn.iocoder.yudao.framework.mybatis.core.util.MyBatisUtils;
import cn.iocoder.yudao.framework.tenant.config.TenantProperties;
import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.framework.tenant.core.db.TenantDatabaseInterceptor;
import cn.iocoder.yudao.module.product.controller.app.spu.vo.AppProductSpuPageReqVO;
import cn.iocoder.yudao.module.product.dal.mysql.sku.ProductSkuMapper;
import com.baomidou.mybatisplus.extension.plugins.MybatisPlusInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.TenantLineInnerInterceptor;
import jakarta.annotation.Resource;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;

import javax.sql.DataSource;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

@Import(ProductSpuAvailableMerchantQueryTest.TenantQueryTestConfiguration.class)
class ProductSpuAvailableMerchantQueryTest extends BaseDbUnitTest {

    @Resource
    private ProductSpuMapper spuMapper;
    @Resource
    private ProductSkuMapper skuMapper;
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

    @TestConfiguration(proxyBeanMethods = false)
    static class TenantQueryTestConfiguration {

        @Bean
        TenantLineInnerInterceptor tenantLineInnerInterceptor(MybatisPlusInterceptor interceptor) {
            TenantLineInnerInterceptor tenantInterceptor = new TenantLineInnerInterceptor(
                    new TenantDatabaseInterceptor(new TenantProperties()));
            MyBatisUtils.addInterceptor(interceptor, tenantInterceptor, 0);
            return tenantInterceptor;
        }
    }

    @Test
    void testSelectAvailableByIds_excludesFrozenDeletedAndMissingMerchants() {
        JdbcTemplate jdbc = new JdbcTemplate(dataSource);
        insertMerchant(jdbc, 100L, 20, false);
        insertMerchant(jdbc, 200L, 50, false);
        insertMerchant(jdbc, 300L, 20, true);
        insertSpu(jdbc, 1000L, 100L);
        insertSpu(jdbc, 2000L, 200L);
        insertSpu(jdbc, 3000L, 300L);
        insertSpu(jdbc, 4000L, 400L);

        assertThat(spuMapper.selectAvailableByIds(List.of(1000L, 2000L, 3000L, 4000L)))
                .extracting(row -> row.getId()).containsExactly(1000L);
        assertThat(spuMapper.selectAvailableByIds(List.of(2000L))).isEmpty();
    }

    @Test
    void testSelectAvailableByIds_emptySelectionNeverQueriesAllProducts() {
        JdbcTemplate jdbc = new JdbcTemplate(dataSource);
        insertMerchant(jdbc, 100L, 20, false);
        insertSpu(jdbc, 1000L, 100L);

        assertThat(spuMapper.selectAvailableByIds(List.of())).isEmpty();
        assertThat(spuMapper.selectAvailableByIds(null)).isEmpty();
    }

    @Test
    void testSelectAvailableByIds_includesGlobalAndCurrentTenantDataOnly() {
        JdbcTemplate jdbc = new JdbcTemplate(dataSource);
        insertMerchant(jdbc, 500L, 20, false, 0L);
        insertMerchant(jdbc, 501L, 20, false, 1L);
        insertMerchant(jdbc, 600L, 20, false, 2L);
        insertSpu(jdbc, 5000L, 500L, 1L, 1);
        insertSpu(jdbc, 5001L, 500L, 0L, 1);
        insertSpu(jdbc, 5002L, 501L, 1L, 1);
        insertSpu(jdbc, 6000L, 500L, 2L, 1);
        insertSpu(jdbc, 6001L, 600L, 1L, 1);
        insertSpu(jdbc, 7000L, 500L, 1L, 0);

        assertThat(spuMapper.selectAvailableByIds(List.of(5000L, 5001L, 5002L, 6000L, 6001L, 7000L)))
                .extracting(row -> row.getId()).containsExactly(5000L, 5001L, 5002L);
    }

    @Test
    void testSelectPage_includesGlobalAndCurrentTenantDataOnly() {
        JdbcTemplate jdbc = new JdbcTemplate(dataSource);
        insertMerchant(jdbc, 800L, 20, false, 0L);
        insertMerchant(jdbc, 801L, 20, false, 1L);
        insertMerchant(jdbc, 802L, 20, false, 2L);
        insertSpu(jdbc, 8000L, 800L, 0L, 1);
        insertSpu(jdbc, 8001L, 801L, 1L, 1);
        insertSpu(jdbc, 8002L, 802L, 2L, 1);

        AppProductSpuPageReqVO pageReqVO = new AppProductSpuPageReqVO();
        pageReqVO.setPageNo(1);
        pageReqVO.setPageSize(20);

        assertThat(spuMapper.selectPage(pageReqVO, Set.of()).getList())
                .extracting(row -> row.getId()).containsExactly(8001L, 8000L);
    }

    @Test
    void testSelectAvailableById_includesGlobalProductForDetail() {
        JdbcTemplate jdbc = new JdbcTemplate(dataSource);
        insertMerchant(jdbc, 900L, 20, false, 0L);
        insertSpu(jdbc, 9000L, 900L, 0L, 1);

        assertThat(spuMapper.selectAvailableById(9000L)).extracting(row -> row.getId()).isEqualTo(9000L);
    }

    @Test
    void testSelectAvailableSkuListBySpuId_includesGlobalSkuForDetail() {
        JdbcTemplate jdbc = new JdbcTemplate(dataSource);
        insertMerchant(jdbc, 900L, 20, false, 0L);
        insertSpu(jdbc, 9000L, 900L, 0L, 1);
        insertSku(jdbc, 9100L, 9000L, 0L);

        assertThat(skuMapper.selectAvailableListBySpuId(9000L))
                .extracting(row -> row.getId()).containsExactly(9100L);
    }

    @Test
    void testSelectAvailableByIds_includesGlobalAndCurrentTenantSkuOnly() {
        JdbcTemplate jdbc = new JdbcTemplate(dataSource);
        insertMerchant(jdbc, 900L, 20, false, 0L);
        insertSpu(jdbc, 9000L, 900L, 0L, 1);
        insertSku(jdbc, 9300L, 9000L, 0L);
        insertSku(jdbc, 9301L, 9000L, 1L);
        insertSku(jdbc, 9302L, 9000L, 2L);

        assertThat(skuMapper.selectAvailableByIds(List.of(9300L, 9301L, 9302L)))
                .extracting(row -> row.getId()).containsExactly(9300L, 9301L);
    }

    @Test
    void testUpdateBrowseCount_updatesGlobalProduct() {
        JdbcTemplate jdbc = new JdbcTemplate(dataSource);
        insertMerchant(jdbc, 920L, 20, false, 0L);
        insertSpu(jdbc, 9200L, 920L, 0L, 1);

        spuMapper.updateBrowseCount(9200L, 1);

        assertThat(jdbc.queryForObject("SELECT browse_count FROM product_spu WHERE id = ?", Integer.class, 9200L))
                .isEqualTo(1);
    }

    private void insertMerchant(JdbcTemplate jdbc, Long id, Integer status, boolean deleted) {
        insertMerchant(jdbc, id, status, deleted, 1L);
    }

    private void insertMerchant(JdbcTemplate jdbc, Long id, Integer status, boolean deleted, Long tenantId) {
        jdbc.update("INSERT INTO product_merchant (id, name, status, tenant_id, deleted) VALUES (?, ?, ?, ?, ?)",
                id, "merchant-" + id, status, tenantId, deleted);
    }

    private void insertSpu(JdbcTemplate jdbc, Long id, Long merchantId) {
        insertSpu(jdbc, id, merchantId, 1L, 1);
    }

    private void insertSpu(JdbcTemplate jdbc, Long id, Long merchantId, Long tenantId, Integer status) {
        jdbc.update("INSERT INTO product_spu (id, name, keyword, introduction, description, bar_code, category_id, "
                        + "merchant_id, pic_url, unit, sort, status, spec_type, price, market_price, cost_price, stock, "
                        + "delivery_template_id, recommend_hot, recommend_benefit, recommend_best, recommend_new, "
                        + "recommend_good, give_integral, sub_commission_type, tenant_id) "
                        + "VALUES (?, ?, '', '', '', '', 1, ?, '', 0, 0, ?, FALSE, 100, 100, 100, 1, 1, FALSE, FALSE, "
                        + "FALSE, FALSE, FALSE, 0, FALSE, ?)", id, "spu-" + id, merchantId, status, tenantId);
    }

    private void insertSku(JdbcTemplate jdbc, Long id, Long spuId, Long tenantId) {
        jdbc.update("INSERT INTO product_sku (id, spu_id, properties, price, market_price, cost_price, bar_code, "
                        + "pic_url, stock, weight, volume, first_brokerage_price, "
                        + "second_brokerage_price, sales_count, tenant_id) "
                        + "VALUES (?, ?, '[]', 100, 120, 80, '', '', 10, 0, 0, 0, 0, 0, ?)",
                id, spuId, tenantId);
    }
}
