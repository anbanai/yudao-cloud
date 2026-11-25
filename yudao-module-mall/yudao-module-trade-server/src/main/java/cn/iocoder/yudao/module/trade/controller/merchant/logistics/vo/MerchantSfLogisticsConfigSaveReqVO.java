package cn.iocoder.yudao.module.trade.controller.merchant.logistics.vo;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class MerchantSfLogisticsConfigSaveReqVO {

    @NotBlank
    private String monthlyCard;
}
