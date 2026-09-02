package cn.iocoder.yudao.module.trade.controller.admin.logistics.vo;

import lombok.Data;
import jakarta.validation.constraints.Size;

@Data
public class LogisticsPrintDeviceSaveReqVO {
    private Long id;
    @Size(max = 128)
    private String printerName;
    private Boolean defaultFlag = false;
    private Integer status = 0;
}
