package cn.iocoder.yudao.module.system.api.social.dto;

import lombok.Data;

import java.util.List;

/**
 * 微信物流助手支持的快递公司。
 */
@Data
public class SocialWxaExpressDeliveryRespDTO {

    private String deliveryId;
    private String deliveryName;
    private Integer canUseCash;
    private Integer canGetQuota;
    private String cashBizId;
    private List<SocialWxaExpressAccountRespDTO.ServiceType> serviceTypes;
}
