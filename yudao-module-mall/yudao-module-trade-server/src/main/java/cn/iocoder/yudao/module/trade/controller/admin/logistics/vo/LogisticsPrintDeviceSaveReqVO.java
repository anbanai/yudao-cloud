package cn.iocoder.yudao.module.trade.controller.admin.logistics.vo;

import lombok.Data;
import jakarta.validation.constraints.Size;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

@Data
public class LogisticsPrintDeviceSaveReqVO {
    @NotNull
    private Long id;
    @Size(max = 128)
    private String printerName;
    private Boolean defaultFlag = false;
    @NotNull
    @Min(0)
    @Max(1)
    private Integer status = 0;
}
