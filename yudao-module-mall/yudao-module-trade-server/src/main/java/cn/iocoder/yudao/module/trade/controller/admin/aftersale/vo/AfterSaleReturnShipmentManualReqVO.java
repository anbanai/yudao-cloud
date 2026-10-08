package cn.iocoder.yudao.module.trade.controller.admin.aftersale.vo;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class AfterSaleReturnShipmentManualReqVO {
    @NotNull
    private Long logisticsId;
    @NotBlank
    @Size(max = 64)
    private String logisticsNo;
}
