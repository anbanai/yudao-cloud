package cn.iocoder.yudao.module.trade.controller.merchant.logistics.vo;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class MerchantSfLogisticsConfigRespVO {

    private Long merchantId;
    private String monthlyCardMasked;
    private LocalDateTime updateTime;
}
