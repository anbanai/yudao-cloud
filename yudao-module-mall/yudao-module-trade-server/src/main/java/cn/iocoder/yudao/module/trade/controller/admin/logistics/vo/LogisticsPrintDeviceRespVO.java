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
    private String printerName;
    private Boolean pending;
    private LocalDateTime enrollmentExpiresTime;
    private Boolean defaultFlag;
    private Integer status;
    private String version;
    private LocalDateTime lastPollTime;
    /** Encrypted PrintBridge import file, only returned while enrolling a device. */
    private String configFile;
}
