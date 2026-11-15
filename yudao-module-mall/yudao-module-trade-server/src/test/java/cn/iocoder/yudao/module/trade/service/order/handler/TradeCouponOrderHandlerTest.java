package cn.iocoder.yudao.module.trade.service.order.handler;
import cn.iocoder.yudao.module.promotion.api.coupon.CouponApi;
import cn.iocoder.yudao.framework.common.pojo.CommonResult;
import cn.iocoder.yudao.module.trade.dal.dataobject.order.TradeOrderDO;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.support.*;
import java.util.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;
import static org.junit.jupiter.api.Assertions.*;
class TradeCouponOrderHandlerTest {
 @Test void failedTradeTransactionCompensatesRemoteCouponUse(){CouponApi api=mock(CouponApi.class);when(api.useCoupons(anyList(),anyLong(),anyLong())).thenReturn(CommonResult.success(true));when(api.returnCoupons(anyList(),anyLong())).thenReturn(CommonResult.success(true));TradeCouponOrderHandler handler=new TradeCouponOrderHandler();ReflectionTestUtils.setField(handler,"couponApi",api);TradeOrderDO order=new TradeOrderDO().setId(10L).setUserId(1L).setCouponIds(Arrays.asList(1L,2L));TransactionSynchronizationManager.initSynchronization();try{handler.afterOrderCreate(order,Collections.emptyList());verify(api).useCoupons(Arrays.asList(1L,2L),1L,10L);for(TransactionSynchronization sync:TransactionSynchronizationManager.getSynchronizations()){sync.afterCompletion(TransactionSynchronization.STATUS_ROLLED_BACK);}verify(api).returnCoupons(Arrays.asList(1L,2L),10L);}finally{TransactionSynchronizationManager.clearSynchronization();}}
 @Test void zeroPriceExchangeMovesDirectlyToFulfillment(){TradeCouponOrderHandler handler=new TradeCouponOrderHandler();TradeOrderDO order=new TradeOrderDO().setCouponIds(Collections.singletonList(1L)).setPayPrice(0);handler.beforeOrderCreate(order,Collections.emptyList());assertTrue(order.getPayStatus());assertNotNull(order.getPayTime());assertEquals(10,order.getStatus());}
}
