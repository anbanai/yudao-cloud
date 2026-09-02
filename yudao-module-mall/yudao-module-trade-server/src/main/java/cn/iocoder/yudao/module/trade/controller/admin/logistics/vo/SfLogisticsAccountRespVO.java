package cn.iocoder.yudao.module.trade.controller.admin.logistics.vo;

import lombok.Data;
import lombok.experimental.Accessors;

import java.math.BigDecimal;

@Data
@Accessors(chain = true)
public class SfLogisticsAccountRespVO {
    private Long id;
    private String name;
    private String partnerIdMasked;
    private String monthlyCardMasked;
    private String serviceCode;
    private String senderName;
    private String senderPhone;
    private String senderProvince;
    private String senderCity;
    private String senderDistrict;
    private String senderAddress;
    private BigDecimal defaultWeightKg;
    private Integer paperWidthMm;
    private Integer paperHeightMm;
    private Integer dpi;
    private Boolean defaultFlag;
    private Integer status;
}
