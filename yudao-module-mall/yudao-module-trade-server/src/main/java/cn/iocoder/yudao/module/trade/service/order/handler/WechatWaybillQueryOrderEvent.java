package cn.iocoder.yudao.module.trade.service.order.handler;

/**
 * 订单发货完成后生成微信物流查询 token 的事件。
 *
 * @param orderId 订单编号
 */
public record WechatWaybillQueryOrderEvent(Long orderId) {
}
