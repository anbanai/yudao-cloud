package cn.iocoder.yudao.module.trade.service.order.handler;

import cn.iocoder.yudao.framework.quartz.config.YudaoAsyncAutoConfiguration;
import cn.iocoder.yudao.framework.test.core.ut.BaseDbUnitTest;
import cn.iocoder.yudao.module.trade.dal.dataobject.order.TradeOrderDO;
import cn.iocoder.yudao.module.trade.service.order.WechatWaybillQueryService;
import jakarta.annotation.Resource;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.transaction.TestTransaction;
import org.springframework.transaction.annotation.Transactional;

import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

@Import({WechatWaybillQueryOrderHandler.class, WechatWaybillQueryOrderEventListener.class,
        YudaoAsyncAutoConfiguration.class})
class WechatWaybillQueryOrderHandlerTest extends BaseDbUnitTest {

    @Resource
    private WechatWaybillQueryOrderHandler handler;

    @MockitoBean
    private WechatWaybillQueryService waybillQueryService;

    @Test
    @Transactional
    void afterDeliveryOrder_generatesTokenOnlyAfterCommit() {
        handler.afterDeliveryOrder(new TradeOrderDO().setId(1L));

        verifyNoInteractions(waybillQueryService);
        TestTransaction.flagForCommit();
        TestTransaction.end();

        verify(waybillQueryService, timeout(5000)).ensureWechatWaybillToken(1L);
    }

}
