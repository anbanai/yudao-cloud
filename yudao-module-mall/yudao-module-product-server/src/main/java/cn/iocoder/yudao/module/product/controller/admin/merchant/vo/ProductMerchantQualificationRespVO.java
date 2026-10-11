package cn.iocoder.yudao.module.product.controller.admin.merchant.vo;

import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
public class ProductMerchantQualificationRespVO {
    private Long id;
    private Long merchantId;
    private String type;
    private String certificateNo;
    private String fileUrl;
    private Long fileId;
    private String previewUrl;
    private LocalDate validStart;
    private LocalDate validEnd;
    private Integer status;
    private Long reviewerId;
    private String reviewReason;
    private LocalDateTime createTime;
}
