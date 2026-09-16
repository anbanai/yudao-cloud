package cn.iocoder.yudao.module.trade.service.order.handler;

import cn.iocoder.yudao.framework.common.pojo.CommonResult;
import cn.iocoder.yudao.module.member.api.level.MemberLevelApi;
import cn.iocoder.yudao.module.member.api.point.MemberPointApi;
import cn.iocoder.yudao.module.member.enums.MemberExperienceBizTypeEnum;
import cn.iocoder.yudao.module.member.enums.point.MemberPointBizTypeEnum;
import cn.iocoder.yudao.module.member.enums.point.MemberPointGiveTimingEnum;
import cn.iocoder.yudao.module.trade.dal.dataobject.aftersale.AfterSaleDO;
import cn.iocoder.yudao.module.trade.dal.dataobject.order.TradeOrderDO;
import cn.iocoder.yudao.module.trade.dal.dataobject.order.TradeOrderItemDO;
import cn.iocoder.yudao.module.trade.enums.order.TradeOrderItemAfterSaleStatusEnum;
import cn.iocoder.yudao.module.trade.service.aftersale.AfterSaleService;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;

class TradeMemberPointOrderHandlerTest {

    @Test
    void afterCancelOrder_returnsSumOfAllocatedItemPoints() {
        MemberPointApi pointApi = mock(MemberPointApi.class);
        when(pointApi.addPoint(2L, 46, MemberPointBizTypeEnum.ORDER_USE_CANCEL.getType(), "10"))
                .thenReturn(CommonResult.success(true));
        TradeMemberPointOrderHandler handler = handler(pointApi, null, null);
        TradeOrderDO order = new TradeOrderDO().setId(10L).setUserId(2L).setPayStatus(false);
        List<TradeOrderItemDO> items = List.of(item(11L, 12), item(12L, 34));

        handler.afterCancelOrder(order, items);

        verify(pointApi).addPoint(2L, 46, MemberPointBizTypeEnum.ORDER_USE_CANCEL.getType(), "10");
    }

    @Test
    void afterCancelOrderItem_returnsThatItemsAllocatedPoints() {
        MemberPointApi pointApi = mock(MemberPointApi.class);
        when(pointApi.addPoint(2L, 17, MemberPointBizTypeEnum.ORDER_USE_CANCEL_ITEM.getType(), "22"))
                .thenReturn(CommonResult.success(true));
        MemberLevelApi levelApi = mock(MemberLevelApi.class);
        when(levelApi.reduceExperience(2L, 50,
                MemberExperienceBizTypeEnum.ORDER_GIVE_CANCEL_ITEM.getType(), "22"))
                .thenReturn(CommonResult.success(true));
        AfterSaleService afterSaleService = mock(AfterSaleService.class);
        when(afterSaleService.getAfterSale(33L)).thenReturn(new AfterSaleDO().setRefundPrice(50));
        TradeMemberPointOrderHandler handler = handler(pointApi, levelApi, afterSaleService);
        TradeOrderDO order = new TradeOrderDO().setId(10L).setUserId(2L);
        TradeOrderItemDO item = item(22L, 17).setAfterSaleId(33L).setGivePoint(0);

        handler.afterCancelOrderItem(order, item);

        verify(pointApi).addPoint(2L, 17, MemberPointBizTypeEnum.ORDER_USE_CANCEL_ITEM.getType(), "22");
    }

    @Test
    void afterPayOrder_whenGiveTimingIsReceive_createsPendingPointsPerItem() {
        MemberPointApi pointApi = mock(MemberPointApi.class);
        when(pointApi.addPendingPoint(2L, 12, MemberPointBizTypeEnum.ORDER_GIVE_PENDING.getType(), "11"))
                .thenReturn(CommonResult.success(true));
        MemberLevelApi levelApi = mock(MemberLevelApi.class);
        when(levelApi.addExperience(2L, 0, MemberExperienceBizTypeEnum.ORDER_GIVE.getType(), "10"))
                .thenReturn(CommonResult.success(true));
        TradeMemberPointOrderHandler handler = handler(pointApi, levelApi, null);
        TradeOrderDO order = new TradeOrderDO().setId(10L).setUserId(2L)
                .setGivePoint(12).setPayPrice(0).setPointGiveTiming(MemberPointGiveTimingEnum.RECEIVE.getType());

        handler.afterPayOrder(order, List.of(item(11L, 0).setGivePoint(12)));

        verify(pointApi).addPendingPoint(2L, 12, MemberPointBizTypeEnum.ORDER_GIVE_PENDING.getType(), "11");
    }

    @Test
    void afterPayOrder_whenHistoricalTimingIsNull_givesPointsImmediately() {
        MemberPointApi pointApi = mock(MemberPointApi.class);
        when(pointApi.addPoint(2L, 12, MemberPointBizTypeEnum.ORDER_GIVE.getType(), "10"))
                .thenReturn(CommonResult.success(true));
        MemberLevelApi levelApi = mock(MemberLevelApi.class);
        when(levelApi.addExperience(2L, 0, MemberExperienceBizTypeEnum.ORDER_GIVE.getType(), "10"))
                .thenReturn(CommonResult.success(true));
        TradeMemberPointOrderHandler handler = handler(pointApi, levelApi, null);
        TradeOrderDO order = new TradeOrderDO().setId(10L).setUserId(2L)
                .setGivePoint(12).setPayPrice(0).setPointGiveTiming(null);

        handler.afterPayOrder(order, List.of(item(11L, 0).setGivePoint(12)));

        verify(pointApi).addPoint(2L, 12, MemberPointBizTypeEnum.ORDER_GIVE.getType(), "10");
        verify(pointApi, never()).addPendingPoint(anyLong(), anyInt(), anyInt(), anyString());
    }

    @Test
    void afterReceiveOrder_releasesPendingPointsPerItem() {
        MemberPointApi pointApi = mock(MemberPointApi.class);
        when(pointApi.effectPendingPoint(2L, MemberPointBizTypeEnum.ORDER_GIVE_PENDING.getType(), "11"))
                .thenReturn(CommonResult.success(true));
        TradeMemberPointOrderHandler handler = handler(pointApi, null, null);
        TradeOrderDO order = new TradeOrderDO().setId(10L).setUserId(2L)
                .setPointGiveTiming(MemberPointGiveTimingEnum.RECEIVE.getType());

        handler.afterReceiveOrder(order, List.of(item(11L, 0).setGivePoint(12)));

        verify(pointApi).effectPendingPoint(2L, MemberPointBizTypeEnum.ORDER_GIVE_PENDING.getType(), "11");
    }

    @Test
    void afterCancelOrderItem_whenGiveTimingIsReceive_refundsPointByActualRecordStatus() {
        MemberPointApi pointApi = mock(MemberPointApi.class);
        when(pointApi.refundOrderItemPoint(2L, "22"))
                .thenReturn(CommonResult.success(true));
        AfterSaleService afterSaleService = mock(AfterSaleService.class);
        when(afterSaleService.getAfterSale(33L)).thenReturn(new AfterSaleDO().setRefundPrice(50));
        MemberLevelApi levelApi = mock(MemberLevelApi.class);
        when(levelApi.reduceExperience(2L, 50,
                MemberExperienceBizTypeEnum.ORDER_GIVE_CANCEL_ITEM.getType(), "22"))
                .thenReturn(CommonResult.success(true));
        TradeMemberPointOrderHandler handler = handler(pointApi, levelApi, afterSaleService);
        TradeOrderDO order = new TradeOrderDO().setId(10L).setUserId(2L)
                .setPointGiveTiming(MemberPointGiveTimingEnum.RECEIVE.getType());
        TradeOrderItemDO item = item(22L, 0).setGivePoint(17).setAfterSaleId(33L);

        handler.afterCancelOrderItem(order, item);

        verify(pointApi).refundOrderItemPoint(2L, "22");
        verify(pointApi, never()).reducePoint(anyLong(), anyInt(), anyInt(), anyString());
    }

    @Test
    void afterCancelOrderItem_whenReceiveTimingAndOrderCompleted_stillRefundsByActualRecordStatus() {
        MemberPointApi pointApi = mock(MemberPointApi.class);
        when(pointApi.refundOrderItemPoint(2L, "22"))
                .thenReturn(CommonResult.success(true));
        AfterSaleService afterSaleService = mock(AfterSaleService.class);
        when(afterSaleService.getAfterSale(33L)).thenReturn(new AfterSaleDO().setRefundPrice(50));
        MemberLevelApi levelApi = mock(MemberLevelApi.class);
        when(levelApi.reduceExperience(2L, 50,
                MemberExperienceBizTypeEnum.ORDER_GIVE_CANCEL_ITEM.getType(), "22"))
                .thenReturn(CommonResult.success(true));
        TradeMemberPointOrderHandler handler = handler(pointApi, levelApi, afterSaleService);
        TradeOrderDO order = new TradeOrderDO().setId(10L).setUserId(2L)
                .setPointGiveTiming(MemberPointGiveTimingEnum.RECEIVE.getType());
        TradeOrderItemDO item = item(22L, 0).setGivePoint(17).setAfterSaleId(33L);

        handler.afterCancelOrderItem(order, item);

        verify(pointApi).refundOrderItemPoint(2L, "22");
        verify(pointApi, never()).reducePoint(anyLong(), anyInt(), anyInt(), anyString());
    }

    @Test
    void afterCancelOrder_whenGiveTimingIsReceive_refundsEachOrderItem() {
        MemberPointApi pointApi = mock(MemberPointApi.class);
        when(pointApi.refundOrderItemPoint(2L, "11")).thenReturn(CommonResult.success(true));
        when(pointApi.refundOrderItemPoint(2L, "12")).thenReturn(CommonResult.success(true));
        MemberLevelApi levelApi = mock(MemberLevelApi.class);
        when(levelApi.addExperience(2L, 0,
                MemberExperienceBizTypeEnum.ORDER_GIVE_CANCEL.getType(), "10"))
                .thenReturn(CommonResult.success(true));
        TradeMemberPointOrderHandler handler = handler(pointApi, levelApi, null);
        TradeOrderDO order = new TradeOrderDO().setId(10L).setUserId(2L).setPayStatus(true)
                .setPayPrice(0).setRefundPrice(0)
                .setPointGiveTiming(MemberPointGiveTimingEnum.RECEIVE.getType());

        handler.afterCancelOrder(order, List.of(
                item(11L, 0).setGivePoint(12), item(12L, 0).setGivePoint(34)));

        verify(pointApi).refundOrderItemPoint(2L, "11");
        verify(pointApi).refundOrderItemPoint(2L, "12");
    }

    private static TradeMemberPointOrderHandler handler(MemberPointApi pointApi, MemberLevelApi levelApi,
                                                         AfterSaleService afterSaleService) {
        TradeMemberPointOrderHandler handler = new TradeMemberPointOrderHandler();
        ReflectionTestUtils.setField(handler, "memberPointApi", pointApi);
        ReflectionTestUtils.setField(handler, "memberLevelApi", levelApi);
        ReflectionTestUtils.setField(handler, "afterSaleService", afterSaleService);
        return handler;
    }

    private static TradeOrderItemDO item(long id, int usePoint) {
        return new TradeOrderItemDO().setId(id).setUsePoint(usePoint).setGivePoint(0)
                .setAfterSaleStatus(TradeOrderItemAfterSaleStatusEnum.NONE.getStatus());
    }

}
