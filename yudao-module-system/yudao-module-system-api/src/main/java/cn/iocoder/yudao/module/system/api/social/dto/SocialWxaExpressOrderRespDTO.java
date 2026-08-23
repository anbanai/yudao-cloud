package cn.iocoder.yudao.module.system.api.social.dto;

import lombok.Data;

import java.util.List;
import java.util.Map;

/**
 * 微信物流助手运单响应。
 */
@Data
public class SocialWxaExpressOrderRespDTO {

    private String orderId;
    private String waybillId;
    private String printHtml;
    private List<Map<String, String>> waybillData;
    private Integer orderStatus;
}
