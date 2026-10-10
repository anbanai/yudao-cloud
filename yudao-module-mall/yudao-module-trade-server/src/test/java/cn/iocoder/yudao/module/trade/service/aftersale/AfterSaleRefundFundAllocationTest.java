package cn.iocoder.yudao.module.trade.service.aftersale;

import cn.iocoder.yudao.framework.common.enums.UserTypeEnum;
import cn.iocoder.yudao.framework.common.pojo.CommonResult;
import cn.iocoder.yudao.module.pay.api.refund.PayRefundApi;
import cn.iocoder.yudao.module.pay.api.refund.dto.PayRefundCreateReqDTO;
import cn.iocoder.yudao.module.trade.dal.dataobject.aftersale.AfterSaleDO;
import cn.iocoder.yudao.module.trade.dal.dataobject.order.TradeOrderItemDO;
import cn.iocoder.yudao.module.trade.dal.mysql.aftersale.AfterSaleMapper;
import cn.iocoder.yudao.module.trade.enums.aftersale.AfterSaleStatusEnum;
import cn.iocoder.yudao.module.trade.framework.aftersale.core.aop.AfterSaleLogAspect;
import cn.iocoder.yudao.module.trade.framework.order.config.TradeOrderProperties;
import cn.iocoder.yudao.module.trade.service.order.TradeOrderQueryService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AfterSaleRefundFundAllocationTest {

    @Mock
    private AfterSaleMapper afterSaleMapper;
    @Mock
    private AfterSaleReturnShipmentService returnShipmentService;
    @Mock
    private TradeOrderQueryService orderQueryService;
    @Mock
    private PayRefundApi payRefundApi;

    private AfterSaleServiceImpl service;
    private AfterSaleDO afterSale;

    @BeforeEach
    void setUp() {
        service = new AfterSaleServiceImpl();
        ReflectionTestUtils.setField(service, "tradeAfterSaleMapper", afterSaleMapper);
        ReflectionTestUtils.setField(service, "returnShipmentService", returnShipmentService);
        ReflectionTestUtils.setField(service, "tradeOrderQueryService", orderQueryService);
        ReflectionTestUtils.setField(service, "payRefundApi", payRefundApi);
        ReflectionTestUtils.setField(service, "tradeOrderProperties", new TradeOrderProperties());
        afterSale = new AfterSaleDO().setId(10L).setOrderId(20L).setOrderItemId(30L)
                .setUserId(40L).setSpuName("茶叶").setRefundPrice(400)
                .setStatus(AfterSaleStatusEnum.WAIT_REFUND.getStatus());
        when(afterSaleMapper.selectByIdForUpdate(10L)).thenReturn(afterSale);
        when(payRefundApi.createRefund(any())).thenReturn(CommonResult.success(50L));
    }

    @AfterEach
    void clearLogContext() {
        ReflectionTestUtils.invokeMethod(AfterSaleLogAspect.class, "clear");
    }

    @Test
    void refundAfterSale_merchantPartialRefundAllocatesActualAmountAfterFreightDeduction() {
        when(orderQueryService.getOrderItem(30L)).thenReturn(new TradeOrderItemDO()
                .setId(30L).setOrderId(20L).setSubOrderId(60L).setMerchantId(70L).setPayPrice(1_000));
        doAnswer(invocation -> {
            AfterSaleDO value = invocation.getArgument(0);
            value.setRefundPrice(value.getRefundPrice() - 120);
            return null;
        }).when(returnShipmentService).applyRefundDeduction(afterSale);

        service.refundAfterSale(90L, "127.0.0.1", 10L);

        PayRefundCreateReqDTO request = captureRefund();
        assertThat(request.getPrice()).isEqualTo(280);
        assertThat(request.getFundAllocation()).singleElement().satisfies(allocation -> {
            assertThat(allocation.getAfterSaleId()).isEqualTo(10L);
            assertThat(allocation.getTradeOrderItemId()).isEqualTo(30L);
            assertThat(allocation.getTradeOrderSubId()).isEqualTo(60L);
            assertThat(allocation.getMerchantId()).isEqualTo(70L);
            assertThat(allocation.getAmount()).isEqualTo(280);
        });
    }

    @Test
    void refundAfterSale_legacyItemWithoutSubOrderKeepsOrdinaryRefundRequest() {
        when(orderQueryService.getOrderItem(30L)).thenReturn(new TradeOrderItemDO()
                .setId(30L).setOrderId(20L).setMerchantId(70L).setPayPrice(1_000));

        service.refundAfterSale(90L, "127.0.0.1", 10L);

        assertLegacyRefund(captureRefund());
    }

    @Test
    void refundAfterSale_legacyItemWithoutMerchantKeepsOrdinaryRefundRequest() {
        when(orderQueryService.getOrderItem(30L)).thenReturn(new TradeOrderItemDO()
                .setId(30L).setOrderId(20L).setSubOrderId(60L).setPayPrice(1_000));

        service.refundAfterSale(90L, "127.0.0.1", 10L);

        assertLegacyRefund(captureRefund());
    }

    @Test
    void refundAfterSale_missingLegacyItemKeepsOrdinaryRefundRequest() {
        service.refundAfterSale(90L, "127.0.0.1", 10L);

        assertLegacyRefund(captureRefund());
    }

    private PayRefundCreateReqDTO captureRefund() {
        ArgumentCaptor<PayRefundCreateReqDTO> requestCaptor = ArgumentCaptor.forClass(PayRefundCreateReqDTO.class);
        verify(payRefundApi).createRefund(requestCaptor.capture());
        ArgumentCaptor<AfterSaleDO> updateCaptor = ArgumentCaptor.forClass(AfterSaleDO.class);
        verify(afterSaleMapper).updateById(updateCaptor.capture());
        assertThat(updateCaptor.getValue().getId()).isEqualTo(10L);
        assertThat(updateCaptor.getValue().getPayRefundId()).isEqualTo(50L);
        return requestCaptor.getValue();
    }

    private void assertLegacyRefund(PayRefundCreateReqDTO request) {
        assertThat(request.getAppKey()).isEqualTo("mall");
        assertThat(request.getMerchantOrderId()).isEqualTo("20");
        assertThat(request.getMerchantRefundId()).isEqualTo("10");
        assertThat(request.getUserId()).isEqualTo(40L);
        assertThat(request.getUserType()).isEqualTo(UserTypeEnum.MEMBER.getValue());
        assertThat(request.getUserIp()).isEqualTo("127.0.0.1");
        assertThat(request.getPrice()).isEqualTo(400);
        assertThat(request.getFundAllocation()).isNullOrEmpty();
    }

}
