package cn.iocoder.yudao.module.trade.service.order.handler;

import cn.iocoder.yudao.module.trade.dal.dataobject.order.TradeOrderDO;
import jakarta.annotation.Resource;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;

/**
 * 发货后预生成微信物流查询组件 token。
 */
@Component
public class WechatWaybillQueryOrderHandler implements TradeOrderHandler {

    @Resource
    private ApplicationEventPublisher eventPublisher;

    @Override
    public void afterDeliveryOrder(TradeOrderDO order) {
        eventPublisher.publishEvent(new WechatWaybillQueryOrderEvent(order.getId()));
    }

}
