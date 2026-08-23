package cn.iocoder.yudao.module.system.api.social.dto;

import lombok.Data;

import java.util.List;

/**
 * 微信物流助手绑定账号。
 */
@Data
public class SocialWxaExpressAccountRespDTO {

    private String bizId;
    private String deliveryId;
    private Integer statusCode;
    private String alias;
    private String remarkWrongMsg;
    private String remarkContent;
    private Integer quotaNum;
    private Integer quotaUpdateTime;
    private List<ServiceType> serviceTypes;

    @Data
    public static class ServiceType {
        private Integer serviceType;
        private String serviceName;
    }
}
