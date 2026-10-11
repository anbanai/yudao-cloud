package cn.iocoder.yudao.module.trade.dal.mysql.aftersale;

import cn.iocoder.yudao.framework.test.core.ut.BaseDbUnitTest;
import cn.iocoder.yudao.module.trade.controller.admin.aftersale.vo.AfterSalePageReqVO;
import cn.iocoder.yudao.module.trade.dal.dataobject.aftersale.AfterSaleDO;
import cn.iocoder.yudao.module.trade.enums.aftersale.ReturnShipmentStatusEnum;
import jakarta.annotation.Resource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

import javax.sql.DataSource;

import static org.assertj.core.api.Assertions.assertThat;

class AfterSaleMapperMerchantIsolationTest extends BaseDbUnitTest {

    @Resource
    private AfterSaleMapper afterSaleMapper;
    @Resource
    private DataSource dataSource;

    @BeforeEach
    void seedOrdersAndAfterSales() {
        JdbcTemplate jdbc = new JdbcTemplate(dataSource);
        insertOrderItem(jdbc, 100L, 1L);
        insertOrderItem(jdbc, 200L, 2L);
        insertAfterSale(jdbc, 10L, 100L);
        insertAfterSale(jdbc, 20L, 200L);
    }

    @Test
    void testMerchantWithoutOrdersHasNoAfterSales() {
        assertThat(afterSaleMapper.selectPageByMerchantId(new AfterSalePageReqVO(), 3L).getTotal()).isZero();
        assertThat(afterSaleMapper.selectByIdAndMerchantId(10L, 3L)).isNull();
        assertThat(afterSaleMapper.updateByIdAndStatusAndMerchantId(10L, 10, 3L,
                new AfterSaleDO().setStatus(20))).isZero();
        assertThat(afterSaleMapper.selectById(10L).getStatus()).isEqualTo(10);
    }

    @Test
    void testMerchantCanOnlyReadAndUpdateOwnAfterSales() {
        assertThat(afterSaleMapper.selectPageByMerchantId(new AfterSalePageReqVO(), 1L).getList())
                .extracting(AfterSaleDO::getId).containsExactly(10L);
        assertThat(afterSaleMapper.selectByIdAndMerchantId(20L, 1L)).isNull();
        assertThat(afterSaleMapper.updateByIdAndStatusAndMerchantId(20L, 10, 1L,
                new AfterSaleDO().setStatus(20))).isZero();
        assertThat(afterSaleMapper.updateByIdAndStatusAndMerchantId(10L, 10, 1L,
                new AfterSaleDO().setStatus(20))).isEqualTo(1);
        assertThat(afterSaleMapper.selectById(20L).getStatus()).isEqualTo(10);
    }

    @Test
    void testReturnShipmentFilterWithoutMatchesReturnsEmpty() {
        AfterSalePageReqVO request = new AfterSalePageReqVO();
        request.setReturnShipmentStatus(ReturnShipmentStatusEnum.values()[0].name());
        assertThat(afterSaleMapper.selectPage(request).getTotal()).isZero();
        assertThat(afterSaleMapper.selectPageByMerchantId(request, 1L).getTotal()).isZero();
    }

    private void insertOrderItem(JdbcTemplate jdbc, Long id, Long merchantId) {
        jdbc.update("INSERT INTO trade_order_item (id, user_id, order_id, spu_id, merchant_id, spu_name, sku_id, "
                        + "count, price, discount_price, pay_price, after_sale_status) "
                        + "VALUES (?, 1, 1, 1, ?, 'tea', 1, 1, 100, 0, 100, 0)", id, merchantId);
    }

    private void insertAfterSale(JdbcTemplate jdbc, Long id, Long orderItemId) {
        jdbc.update("INSERT INTO trade_after_sale (id, no, status, type, way, user_id, apply_reason, order_id, "
                        + "order_no, order_item_id, spu_id, spu_name, sku_id, count, refund_price) "
                        + "VALUES (?, ?, 10, 10, 10, 1, 'reason', 1, 'order', ?, 1, 'tea', 1, 1, 100)",
                id, "after-sale-" + id, orderItemId);
    }
}
