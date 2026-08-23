package cn.iocoder.yudao.module.trade.controller.admin.logistics.vo;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class WechatLogisticsTraceRespVO {

    private Long id;
    private Long waybillId;
    private LocalDateTime actionTime;
    private Integer actionType;
    private String actionMsg;
}
