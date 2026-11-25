package cn.iocoder.yudao.module.trade.controller.admin.logistics.vo;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class TradeMerchantLogisticsConfigRespVO {

    private Long id;
    private Long merchantId;
    private String monthlyCardMasked;
    private LocalDateTime updateTime;
}
