package cn.iocoder.yudao.module.trade.controller.admin.logistics.vo;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class WechatLogisticsWaybillRespVO {

    private Long id;
    private Long orderId;
    private String orderNo;
    private String wechatOrderId;
    private String deliveryId;
    private String bizId;
    private String waybillId;
    private String status;
    private String printStatus;
    private Integer wechatOrderStatus;
    private Integer errorCode;
    private String errorMessage;
    private LocalDateTime lastSyncTime;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
