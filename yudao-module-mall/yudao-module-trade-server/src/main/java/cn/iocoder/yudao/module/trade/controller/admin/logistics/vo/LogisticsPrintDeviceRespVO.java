package cn.iocoder.yudao.module.trade.controller.admin.logistics.vo;

import lombok.Data;
import lombok.experimental.Accessors;

import java.time.LocalDateTime;

@Data
@Accessors(chain = true)
public class LogisticsPrintDeviceRespVO {
    private Long id;
    private String deviceCode;
    private String deviceName;
    private Boolean defaultFlag;
    private Integer status;
    private String version;
    private LocalDateTime lastPollTime;
    /** Only returned when creating or rotating a token. */
    private String token;
}
