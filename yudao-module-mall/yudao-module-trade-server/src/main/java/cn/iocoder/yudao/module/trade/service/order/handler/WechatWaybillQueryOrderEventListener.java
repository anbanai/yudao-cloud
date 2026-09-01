package cn.iocoder.yudao.module.trade.service.order.handler;

import cn.iocoder.yudao.module.trade.service.order.WechatWaybillQueryService;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * 订单发货事务提交后生成微信物流查询 token。
 */
@Component
@Slf4j
public class WechatWaybillQueryOrderEventListener {

    @Resource
    private WechatWaybillQueryService waybillQueryService;

    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onDeliveryCommitted(WechatWaybillQueryOrderEvent event) {
        try {
            waybillQueryService.ensureWechatWaybillToken(event.orderId());
        } catch (Exception ex) {
            log.warn("[onDeliveryCommitted][订单({}) 生成微信物流查询 token 失败，异常类型({})，原因({})]",
                    event.orderId(), ex.getClass().getSimpleName(), ex.getMessage());
        }
    }

}
