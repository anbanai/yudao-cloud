package cn.iocoder.yudao.module.system.api.social.dto;

import lombok.Data;

/**
 * 微信物流助手查询/取消运单请求。
 */
@Data
public class SocialWxaExpressOrderQueryReqDTO {

    private String orderId;
    private String openid;
    private String deliveryId;
    private String waybillId;
}
