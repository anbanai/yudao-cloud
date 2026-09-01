package cn.iocoder.yudao.module.trade.controller.admin.logistics.vo;

import lombok.Data;
import lombok.experimental.Accessors;

import java.time.LocalDateTime;

@Data
@Accessors(chain = true)
public class LogisticsPrintTaskRespVO {
    private Long id;
    private String requestId;
    private String jobId;
    private Long orderId;
    private Long waybillId;
    private Long deviceId;
    private String status;
    private String format;
    private Integer paperWidthMm;
    private Integer paperHeightMm;
    private Integer dpi;
    private Integer copies;
    private LocalDateTime leaseExpireTime;
    private String lastError;
    private LocalDateTime createTime;
}
