package cn.iocoder.yudao.module.trade.service.order;

import cn.iocoder.yudao.framework.common.enums.TerminalEnum;
import cn.iocoder.yudao.framework.common.enums.UserTypeEnum;
import cn.iocoder.yudao.framework.common.pojo.CommonResult;
import cn.iocoder.yudao.framework.test.core.ut.BaseDbUnitTest;
import cn.iocoder.yudao.module.pay.api.order.PayOrderApi;
import cn.iocoder.yudao.module.pay.api.order.dto.PayOrderRespDTO;
import cn.iocoder.yudao.module.pay.enums.PayChannelEnum;
import cn.iocoder.yudao.module.system.api.social.SocialClientApi;
import cn.iocoder.yudao.module.system.api.social.dto.SocialWxaWaybillTraceReqDTO;
import cn.iocoder.yudao.module.trade.dal.dataobject.delivery.DeliveryExpressDO;
import cn.iocoder.yudao.module.trade.dal.dataobject.order.TradeOrderDO;
import cn.iocoder.yudao.module.trade.dal.dataobject.order.TradeOrderItemDO;
import cn.iocoder.yudao.module.trade.dal.mysql.order.TradeOrderItemMapper;
import cn.iocoder.yudao.module.trade.dal.mysql.order.TradeOrderMapper;
import cn.iocoder.yudao.module.trade.enums.delivery.DeliveryTypeEnum;
import cn.iocoder.yudao.module.trade.enums.order.TradeOrderStatusEnum;
import cn.iocoder.yudao.module.trade.enums.order.TradeOrderTypeEnum;
import cn.iocoder.yudao.module.trade.service.delivery.DeliveryExpressService;
import jakarta.annotation.Resource;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static cn.iocoder.yudao.framework.test.core.util.AssertUtils.assertServiceException;
import static cn.iocoder.yudao.module.trade.enums.ErrorCodeConstants.ORDER_NOT_FOUND;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@Import(WechatWaybillQueryServiceImpl.class)
class WechatWaybillQueryServiceImplTest extends BaseDbUnitTest {

    private static final Long USER_ID = 100L;

    @Resource
    private WechatWaybillQueryService waybillQueryService;
    @Resource
    private TradeOrderMapper orderMapper;
    @Resource
    private TradeOrderItemMapper orderItemMapper;

    @MockitoBean
    private PayOrderApi payOrderApi;
    @MockitoBean
    private SocialClientApi socialClientApi;
    @MockitoBean
    private DeliveryExpressService deliveryExpressService;

    @Test
    void ensureWechatWaybillToken_returnsExistingTokenWithoutCallingUpstream() {
        TradeOrderDO order = insertEligibleOrder(TradeOrderStatusEnum.DELIVERED);
        orderMapper.updateById(new TradeOrderDO().setId(order.getId()).setWechatWaybillToken("existing-token"));

        String token = waybillQueryService.ensureWechatWaybillToken(USER_ID, order.getId());

        assertEquals("existing-token", token);
        verifyNoInteractions(payOrderApi, socialClientApi, deliveryExpressService);
    }

    @Test
    void ensureWechatWaybillToken_createsAndPersistsTokenWithCompleteMapping() {
        TradeOrderDO order = insertEligibleOrder(TradeOrderStatusEnum.DELIVERED);
        stubEligibleDependencies(order, "new-token");

        String token = waybillQueryService.ensureWechatWaybillToken(USER_ID, order.getId());

        assertEquals("new-token", token);
        assertEquals("new-token", orderMapper.selectById(order.getId()).getWechatWaybillToken());
        ArgumentCaptor<SocialWxaWaybillTraceReqDTO> captor = ArgumentCaptor.forClass(SocialWxaWaybillTraceReqDTO.class);
        verify(socialClientApi).traceWxaWaybill(eq(UserTypeEnum.MEMBER.getValue()), captor.capture());
        SocialWxaWaybillTraceReqDTO request = captor.getValue();
        assertEquals("openid-100", request.getOpenid());
        assertEquals("13800138000", request.getReceiverPhone());
        assertEquals("SF123456789", request.getWaybillId());
        assertEquals("420000000000001", request.getTransactionId());
        assertEquals("SF", request.getDeliveryId());
        assertEquals("pages/order/detail?id=" + order.getId(), request.getOrderDetailPath());
        assertEquals(1, request.getGoods().size());
        assertEquals("测试商品", request.getGoods().get(0).getName());
        assertEquals("https://static.example.com/product.png", request.getGoods().get(0).getImageUrl());
    }

    @Test
    void ensureWechatWaybillToken_supportsCompletedHistoricalOrders() {
        TradeOrderDO order = insertEligibleOrder(TradeOrderStatusEnum.COMPLETED);
        stubEligibleDependencies(order, "history-token");

        assertEquals("history-token", waybillQueryService.ensureWechatWaybillToken(USER_ID, order.getId()));
        assertEquals("history-token", orderMapper.selectById(order.getId()).getWechatWaybillToken());
    }

    @Test
    void ensureWechatWaybillToken_rejectsOrdersOwnedByAnotherUser() {
        TradeOrderDO order = insertEligibleOrder(TradeOrderStatusEnum.DELIVERED);

        assertServiceException(() -> waybillQueryService.ensureWechatWaybillToken(USER_ID + 1, order.getId()),
                ORDER_NOT_FOUND);
        verifyNoInteractions(payOrderApi, socialClientApi, deliveryExpressService);
    }

    @Test
    void ensureWechatWaybillToken_skipsIneligibleOrderFields() {
        List<TradeOrderDO> orders = List.of(
                insertEligibleOrder(TradeOrderStatusEnum.UNDELIVERED),
                insertEligibleOrder(TradeOrderStatusEnum.DELIVERED).setDeliveryType(DeliveryTypeEnum.PICK_UP.getType()),
                insertEligibleOrder(TradeOrderStatusEnum.DELIVERED).setLogisticsNo(""),
                insertEligibleOrder(TradeOrderStatusEnum.DELIVERED).setPayChannelCode("alipay_app"));
        for (TradeOrderDO order : orders) {
            orderMapper.updateById(order);
            assertEquals("", waybillQueryService.ensureWechatWaybillToken(USER_ID, order.getId()));
        }

        verifyNoInteractions(payOrderApi, socialClientApi, deliveryExpressService);
    }

    @Test
    void ensureWechatWaybillToken_skipsIncompletePaymentAndProductData() {
        TradeOrderDO missingOpenid = insertEligibleOrder(TradeOrderStatusEnum.DELIVERED);
        when(payOrderApi.getOrder(missingOpenid.getPayOrderId())).thenReturn(CommonResult.success(validPayOrder().setChannelUserId("")));
        assertEquals("", waybillQueryService.ensureWechatWaybillToken(USER_ID, missingOpenid.getId()));

        reset(payOrderApi);
        TradeOrderDO invalidImage = insertEligibleOrder(TradeOrderStatusEnum.DELIVERED);
        when(payOrderApi.getOrder(invalidImage.getPayOrderId())).thenReturn(CommonResult.success(validPayOrder()));
        when(deliveryExpressService.getDeliveryExpress(invalidImage.getLogisticsId()))
                .thenReturn(new DeliveryExpressDO().setCode("SF"));
        TradeOrderItemDO item = orderItemMapper.selectListByOrderId(invalidImage.getId()).get(0);
        orderItemMapper.updateById(item.setPicUrl("/static/product.png"));
        assertEquals("", waybillQueryService.ensureWechatWaybillToken(USER_ID, invalidImage.getId()));

        verifyNoInteractions(socialClientApi);
    }

    @Test
    void ensureWechatWaybillToken_skipsMissingReceiverPhone() {
        TradeOrderDO order = insertEligibleOrder(TradeOrderStatusEnum.DELIVERED);
        orderMapper.updateById(order.setReceiverMobile(""));
        stubEligibleDependencies(order, "must-not-be-used");

        assertEquals("", waybillQueryService.ensureWechatWaybillToken(USER_ID, order.getId()));
        verifyNoInteractions(socialClientApi);
    }

    @Test
    void ensureWechatWaybillToken_returnsEmptyWhenWechatFails() {
        TradeOrderDO order = insertEligibleOrder(TradeOrderStatusEnum.DELIVERED);
        stubEligibleDependencies(order, null);
        when(socialClientApi.traceWxaWaybill(eq(UserTypeEnum.MEMBER.getValue()), any()))
                .thenReturn(CommonResult.error(9300622, "delivery_id invalid"));

        assertEquals("", waybillQueryService.ensureWechatWaybillToken(USER_ID, order.getId()));
        assertNull(orderMapper.selectById(order.getId()).getWechatWaybillToken());
    }

    @Test
    void ensureWechatWaybillToken_serializesConcurrentCreationWithRowLock() throws Exception {
        TradeOrderDO order = insertEligibleOrder(TradeOrderStatusEnum.DELIVERED);
        stubEligibleDependencies(order, null);
        CountDownLatch firstEnteredWechat = new CountDownLatch(1);
        CountDownLatch releaseWechat = new CountDownLatch(1);
        when(socialClientApi.traceWxaWaybill(eq(UserTypeEnum.MEMBER.getValue()), any())).thenAnswer(invocation -> {
            firstEnteredWechat.countDown();
            assertTrue(releaseWechat.await(5, TimeUnit.SECONDS));
            return CommonResult.success("concurrent-token");
        });
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            Future<String> first = executor.submit(() -> waybillQueryService.ensureWechatWaybillToken(USER_ID, order.getId()));
            assertTrue(firstEnteredWechat.await(5, TimeUnit.SECONDS));
            CountDownLatch secondStarted = new CountDownLatch(1);
            Future<String> second = executor.submit(() -> {
                secondStarted.countDown();
                return waybillQueryService.ensureWechatWaybillToken(USER_ID, order.getId());
            });
            assertTrue(secondStarted.await(5, TimeUnit.SECONDS));
            releaseWechat.countDown();

            assertEquals("concurrent-token", first.get(5, TimeUnit.SECONDS));
            assertEquals("concurrent-token", second.get(5, TimeUnit.SECONDS));
            verify(socialClientApi, times(1)).traceWxaWaybill(eq(UserTypeEnum.MEMBER.getValue()), any());
        } finally {
            releaseWechat.countDown();
            executor.shutdownNow();
        }
    }

    private TradeOrderDO insertEligibleOrder(TradeOrderStatusEnum status) {
        TradeOrderDO order = new TradeOrderDO()
                .setNo("ORD-" + System.nanoTime())
                .setType(TradeOrderTypeEnum.NORMAL.getType())
                .setTerminal(TerminalEnum.WECHAT_MINI_PROGRAM.getTerminal())
                .setUserId(USER_ID)
                .setUserIp("127.0.0.1")
                .setStatus(status.getStatus())
                .setProductCount(1)
                .setPayOrderId(System.nanoTime())
                .setPayStatus(true)
                .setPayTime(LocalDateTime.now())
                .setPayChannelCode(PayChannelEnum.WX_LITE.getCode())
                .setTotalPrice(1000)
                .setDiscountPrice(0)
                .setDeliveryPrice(0)
                .setAdjustPrice(0)
                .setPayPrice(1000)
                .setDeliveryType(DeliveryTypeEnum.EXPRESS.getType())
                .setLogisticsId(1L)
                .setLogisticsNo("SF123456789")
                .setDeliveryTime(LocalDateTime.now())
                .setReceiverName("测试用户")
                .setReceiverMobile("13800138000")
                .setReceiverAreaId(110101)
                .setReceiverDetailAddress("测试地址")
                .setRefundStatus(0)
                .setRefundPrice(0)
                .setCouponId(0L)
                .setCouponPrice(0)
                .setPointPrice(0);
        orderMapper.insert(order);
        orderItemMapper.insert(new TradeOrderItemDO()
                .setUserId(USER_ID)
                .setOrderId(order.getId())
                .setSpuId(10L)
                .setSpuName("测试商品")
                .setSkuId(20L)
                .setPicUrl("https://static.example.com/product.png")
                .setCount(1)
                .setCommentStatus(false)
                .setPrice(1000)
                .setDiscountPrice(0)
                .setDeliveryPrice(0)
                .setAdjustPrice(0)
                .setPayPrice(1000)
                .setCouponPrice(0)
                .setPointPrice(0)
                .setUsePoint(0)
                .setGivePoint(0)
                .setVipPrice(0)
                .setAfterSaleStatus(0));
        return order;
    }

    private void stubEligibleDependencies(TradeOrderDO order, String token) {
        when(payOrderApi.getOrder(order.getPayOrderId())).thenReturn(CommonResult.success(validPayOrder()));
        when(deliveryExpressService.getDeliveryExpress(order.getLogisticsId()))
                .thenReturn(new DeliveryExpressDO().setCode("SF"));
        if (token != null) {
            when(socialClientApi.traceWxaWaybill(eq(UserTypeEnum.MEMBER.getValue()), any()))
                    .thenReturn(CommonResult.success(token));
        }
    }

    private PayOrderRespDTO validPayOrder() {
        return new PayOrderRespDTO()
                .setChannelCode(PayChannelEnum.WX_LITE.getCode())
                .setChannelUserId("openid-100")
                .setChannelOrderNo("420000000000001");
    }

}
