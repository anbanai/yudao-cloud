package cn.iocoder.yudao.module.trade.controller.merchant.logistics.vo;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Data
public class MerchantSfLogisticsConfigRespVO {

    private Long merchantId;
    private String monthlyCardMasked;
    private LocalDateTime updateTime;
    private boolean configured;
    private List<String> supportedPaperSpecs;
    private String serviceCode;
    private BigDecimal defaultWeightKg;
    private Integer paperWidthMm;
    private Integer paperHeightMm;
    private Integer dpi;
    private String senderName;
    private String senderPhone;
    private String senderProvince;
    private String senderCity;
    private String senderDistrict;
    private String senderAddress;
    private Boolean returnSameAsSender;
    private String returnName;
    private String returnPhone;
    private String returnProvince;
    private String returnCity;
    private String returnDistrict;
    private String returnAddress;
}
