package cn.iocoder.yudao.module.trade.controller.admin.logistics.vo;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import lombok.Data;

import java.math.BigDecimal;

@Data
public class SfLogisticsAccountSaveReqVO {
    private Long id;
    @NotBlank private String name;
    @NotNull private Long logisticsId;
    @NotBlank private String endpoint;
    private String partnerId;
    private String checkWord;
    private String monthlyCard;
    @NotBlank
    @Pattern(regexp = "\\d+", message = "产品类型必须是顺丰数字服务代码")
    private String serviceCode;
    @NotBlank private String templateCode;
    @NotBlank private String senderName;
    @NotBlank private String senderPhone;
    @NotBlank private String senderProvince;
    @NotBlank private String senderCity;
    private String senderDistrict;
    @NotBlank private String senderAddress;
    @NotNull
    @Positive
    private BigDecimal defaultWeightKg;
    @NotNull @Min(100) @Max(100) private Integer paperWidthMm = 100;
    @NotNull @Min(150) @Max(150) private Integer paperHeightMm = 150;
    @NotNull @Min(203) @Max(203) private Integer dpi = 203;
    private Boolean defaultFlag = false;
    private Integer status = 0;
}
