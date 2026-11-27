package cn.iocoder.yudao.module.trade.controller.merchant.logistics.vo;

import cn.iocoder.yudao.framework.common.validation.Mobile;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.math.BigDecimal;

@Data
public class MerchantSfLogisticsConfigSaveReqVO {

    @Size(max = 64)
    private String monthlyCard;
    @Pattern(regexp = "[12]", message = "寄件产品仅支持顺丰特快或顺丰标快")
    private String serviceCode;
    @Positive @Digits(integer = 7, fraction = 3)
    private BigDecimal defaultWeightKg;
    private Integer paperWidthMm;
    private Integer paperHeightMm;
    private Integer dpi;
    @Size(max = 64)
    private String senderName;
    @Mobile
    private String senderPhone;
    @Size(max = 64)
    private String senderProvince;
    @Size(max = 64)
    private String senderCity;
    @Size(max = 64)
    private String senderDistrict;
    @Size(max = 512)
    private String senderAddress;
    private Boolean returnSameAsSender;
    @Size(max = 64)
    private String returnName;
    @Mobile
    private String returnPhone;
    @Size(max = 64)
    private String returnProvince;
    @Size(max = 64)
    private String returnCity;
    @Size(max = 64)
    private String returnDistrict;
    @Size(max = 512)
    private String returnAddress;
}
