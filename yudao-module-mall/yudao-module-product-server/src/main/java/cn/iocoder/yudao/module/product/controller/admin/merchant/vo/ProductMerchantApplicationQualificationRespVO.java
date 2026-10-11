package cn.iocoder.yudao.module.product.controller.admin.merchant.vo;

import lombok.Data;

import java.time.LocalDate;

@Data
public class ProductMerchantApplicationQualificationRespVO {

    private String code;
    private String name;
    private Boolean required;
    private Boolean certificateNoRequired;
    private String certificateNoPattern;
    private Boolean validityRequired;
    private String certificateNo;
    private Long fileId;
    private String previewUrl;
    private Boolean previewUnavailable;
    private LocalDate validStart;
    private LocalDate validEnd;
}
