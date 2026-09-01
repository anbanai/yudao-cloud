package cn.iocoder.yudao.module.trade.service.order;

/**
 * 微信物流查询组件 Service。
 */
public interface WechatWaybillQueryService {

    /**
     * 为订单生成或取得物流查询 token，用于发货后的主动生成。
     */
    String ensureWechatWaybillToken(Long orderId);

    /**
     * 为当前用户的订单生成或取得物流查询 token。
     */
    String ensureWechatWaybillToken(Long userId, Long orderId);

}
