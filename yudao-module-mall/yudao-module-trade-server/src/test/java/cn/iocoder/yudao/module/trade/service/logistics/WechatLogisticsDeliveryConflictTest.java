package cn.iocoder.yudao.module.trade.service.logistics;

import cn.iocoder.yudao.module.trade.dal.dataobject.logistics.TradeLogisticsWaybillDO;
import cn.iocoder.yudao.module.trade.dal.dataobject.logistics.TradeWechatLogisticsWaybillDO;
import cn.iocoder.yudao.module.trade.dal.dataobject.order.TradeOrderDO;
import cn.iocoder.yudao.module.trade.dal.mysql.logistics.TradeLogisticsWaybillMapper;
import cn.iocoder.yudao.module.trade.dal.mysql.logistics.TradeWechatLogisticsWaybillMapper;
import cn.iocoder.yudao.module.trade.dal.mysql.order.TradeOrderMapper;
import cn.iocoder.yudao.module.trade.enums.logistics.LogisticsWaybillStatusEnum;
import cn.iocoder.yudao.module.trade.enums.logistics.WechatLogisticsPrintStatusEnum;
import cn.iocoder.yudao.module.trade.enums.logistics.WechatLogisticsWaybillStatusEnum;
import cn.iocoder.yudao.module.trade.enums.order.TradeOrderRefundStatusEnum;
import cn.iocoder.yudao.module.trade.service.delivery.DeliveryExpressService;
import cn.iocoder.yudao.module.trade.service.order.TradeOrderUpdateService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class WechatLogisticsDeliveryConflictTest {

    @Mock private TradeWechatLogisticsWaybillMapper wechatWaybillMapper;
    @Mock private TradeLogisticsWaybillMapper sfWaybillMapper;
    @Mock private TradeOrderMapper orderMapper;
    @Mock private DeliveryExpressService deliveryExpressService;
    @Mock private TradeOrderUpdateService orderUpdateService;

    @Test
    void confirmPrint_activeSfWaybillIsRejected() {
        WechatLogisticsServiceImpl service = new WechatLogisticsServiceImpl();
        ReflectionTestUtils.setField(service, "waybillMapper", wechatWaybillMapper);
        ReflectionTestUtils.setField(service, "tradeOrderMapper", orderMapper);
        ReflectionTestUtils.setField(service, "deliveryExpressService", deliveryExpressService);
        ReflectionTestUtils.setField(service, "tradeOrderUpdateService", orderUpdateService);
        ReflectionTestUtils.setField(service, "sfWaybillMapper", sfWaybillMapper);

        TradeWechatLogisticsWaybillDO wechatWaybill = new TradeWechatLogisticsWaybillDO().setId(1L)
                .setOrderId(10L).setDeliveryId("SF").setWaybillId("WX-SF-001")
                .setStatus(WechatLogisticsWaybillStatusEnum.CREATED.name())
                .setPrintStatus(WechatLogisticsPrintStatusEnum.PENDING.name());
        TradeOrderDO order = new TradeOrderDO().setId(10L).setStatus(10).setDeliveryType(1)
                .setRefundStatus(TradeOrderRefundStatusEnum.NONE.getStatus());
        when(wechatWaybillMapper.selectById(1L)).thenReturn(wechatWaybill);
        when(wechatWaybillMapper.selectByIdForUpdate(1L)).thenReturn(wechatWaybill);
        when(orderMapper.selectByIdForUpdate(10L)).thenReturn(order);
        when(sfWaybillMapper.selectActiveByOrderId(10L)).thenReturn(new TradeLogisticsWaybillDO()
                .setOrderId(10L).setStatus(LogisticsWaybillStatusEnum.CREATED.name()));
        assertThatThrownBy(() -> service.confirmPrint(1L))
                .isInstanceOf(cn.iocoder.yudao.framework.common.exception.ServiceException.class)
                .extracting("code")
                .isEqualTo(cn.iocoder.yudao.module.trade.enums.ErrorCodeConstants
                        .LOGISTICS_WAYBILL_ALREADY_EXISTS.getCode());
    }
}
