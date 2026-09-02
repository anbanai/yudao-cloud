package cn.iocoder.yudao.module.trade.controller.admin.logistics.vo;

import lombok.Data;
import lombok.experimental.Accessors;

import java.time.LocalDateTime;

@Data
@Accessors(chain = true)
public class LogisticsWaybillRespVO {
    private Long id;
    private Long orderId;
    private String orderNo;
    private String providerOrderNo;
    private String waybillNo;
    private String status;
    private String deliveryStatus;
    private String printStatus;
    private String jobId;
    private Long deviceId;
    private boolean reused;
    private String errorCode;
    private String errorMessage;
    private LocalDateTime createTime;
}
