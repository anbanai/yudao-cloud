package cn.iocoder.yudao.module.trade.controller.admin.logistics.vo;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class LogisticsPrintDeviceSaveReqVO {
    private Long id;
    @NotBlank private String deviceCode;
    @NotBlank private String deviceName;
    private Boolean defaultFlag = false;
    private Integer status = 0;
}
