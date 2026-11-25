package cn.iocoder.yudao.module.trade.controller.admin.logistics.vo;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class TradeMerchantLogisticsConfigSaveReqVO {

    @NotNull
    private Long merchantId;
    @NotBlank
    private String monthlyCard;
}
