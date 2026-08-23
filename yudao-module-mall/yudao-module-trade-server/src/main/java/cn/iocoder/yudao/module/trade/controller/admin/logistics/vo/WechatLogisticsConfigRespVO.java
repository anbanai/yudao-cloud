package cn.iocoder.yudao.module.trade.controller.admin.logistics.vo;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 管理后台 - 微信物流助手配置 Response VO。
 *
 * <p>只返回物流业务配置，不暴露租户审计字段。</p>
 */
@Data
public class WechatLogisticsConfigRespVO {

    private Long id;
    private Integer userType;
    private String deliveryId;
    private String bizId;
    private Integer serviceType;
    private String serviceName;
    private Boolean enabled;

    private String senderName;
    private String senderTel;
    private String senderMobile;
    private String senderCompany;
    private String senderPostCode;
    private String senderCountry;
    private String senderProvince;
    private String senderCity;
    private String senderArea;
    private String senderAddress;

    private BigDecimal defaultWeight;
    private BigDecimal defaultSpaceLength;
    private BigDecimal defaultSpaceWidth;
    private BigDecimal defaultSpaceHeight;
    private LocalDateTime updateTime;
}
