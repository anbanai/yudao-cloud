package cn.iocoder.yudao.module.trade.service.aftersale;

import cn.iocoder.yudao.module.trade.dal.dataobject.aftersale.AfterSaleDO;
import cn.iocoder.yudao.module.trade.dal.dataobject.aftersale.AfterSaleReturnShipmentDO;
import cn.iocoder.yudao.module.trade.dal.mysql.aftersale.AfterSaleMapper;
import cn.iocoder.yudao.module.trade.dal.mysql.aftersale.AfterSaleReturnShipmentMapper;
import cn.iocoder.yudao.module.trade.enums.aftersale.AfterSaleStatusEnum;
import cn.iocoder.yudao.module.trade.enums.aftersale.AfterSaleWayEnum;
import cn.iocoder.yudao.module.trade.dal.dataobject.logistics.TradeMerchantLogisticsConfigDO;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AfterSaleReturnShipmentSettingsTest {

    @Test
    void existingBookingPreviewUsesItsSavedWarehouse() {
        var service = new AfterSaleReturnShipmentServiceImpl();
        var afterSales = mock(AfterSaleMapper.class);
        var shipments = mock(AfterSaleReturnShipmentMapper.class);
        ReflectionTestUtils.setField(service, "afterSaleMapper", afterSales);
        ReflectionTestUtils.setField(service, "shipmentMapper", shipments);
        when(afterSales.selectByIdAndUserId(10L, 2L)).thenReturn(new AfterSaleDO().setId(10L)
                .setStatus(AfterSaleStatusEnum.SELLER_AGREE.getStatus()).setWay(AfterSaleWayEnum.RETURN_AND_REFUND.getWay()));
        var booked = new AfterSaleReturnShipmentDO().setMerchantId(30L).setAfterSaleId(10L)
                .setWarehouseAddress("原售后仓");
        when(shipments.selectByAfterSaleId(10L)).thenReturn(booked);

        assertThat(service.preview(2L, 10L)).isSameAs(booked);
    }

    @Test
    void bookingsRetainTheirSelectedWarehouseAndBillingWhenMerchantUpdatesSettings() {
        var config = new TradeMerchantLogisticsConfigDO().setServiceCode("2").setMonthlyCard("card-a")
                .setSenderName("发货人").setSenderPhone("13800000000").setSenderProvince("浙江省")
                .setSenderCity("杭州市").setSenderDistrict("余杭区").setSenderAddress("发货仓");
        var original = AfterSaleReturnShipmentServiceImpl.applyReturnSettings(new AfterSaleReturnShipmentDO(), config);
        config.setReturnSameAsSender(false).setReturnName("退货人").setReturnPhone("13900000000")
                .setReturnProvince("江苏省").setReturnCity("苏州市").setReturnDistrict("吴中区")
                .setReturnAddress("售后仓").setServiceCode("1").setMonthlyCard("card-b");
        var next = AfterSaleReturnShipmentServiceImpl.applyReturnSettings(new AfterSaleReturnShipmentDO(), config);
        assertThat(original).extracting("warehouseName", "warehousePhone", "warehouseProvince", "warehouseCity",
                "warehouseDistrict", "warehouseAddress", "serviceCode", "monthlyCard")
                .containsExactly("发货人", "13800000000", "浙江省", "杭州市", "余杭区", "发货仓", "2", "card-a");
        assertThat(next).extracting("warehouseName", "warehousePhone", "warehouseProvince", "warehouseCity",
                "warehouseDistrict", "warehouseAddress", "serviceCode", "monthlyCard")
                .containsExactly("退货人", "13900000000", "江苏省", "苏州市", "吴中区", "售后仓", "1", "card-b");
    }
}
