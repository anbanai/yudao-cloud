package cn.iocoder.yudao.module.trade.controller.admin.logistics.vo;

import cn.iocoder.yudao.module.system.api.social.dto.SocialWxaExpressAccountRespDTO;
import cn.iocoder.yudao.module.system.api.social.dto.SocialWxaExpressDeliveryRespDTO;
import lombok.Data;

import java.util.List;

@Data
public class WechatLogisticsAccountStatusRespVO {

    private Boolean available;
    private String message;
    private List<SocialWxaExpressAccountRespDTO> accounts;
    private List<SocialWxaExpressDeliveryRespDTO> deliveries;
}
