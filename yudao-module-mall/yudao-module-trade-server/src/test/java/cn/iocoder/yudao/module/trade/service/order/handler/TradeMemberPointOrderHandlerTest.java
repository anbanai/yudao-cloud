package cn.iocoder.yudao.module.trade.service.order.handler;

import cn.iocoder.yudao.framework.common.pojo.CommonResult;
import cn.iocoder.yudao.module.member.api.level.MemberLevelApi;
import cn.iocoder.yudao.module.member.api.point.MemberPointApi;
import cn.iocoder.yudao.module.member.enums.MemberExperienceBizTypeEnum;
import cn.iocoder.yudao.module.member.enums.point.MemberPointBizTypeEnum;
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
