package cn.iocoder.yudao.module.product.controller.admin.merchant.vo;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class ProductMerchantReviewRecordRespVO {

    private Long id;
    private Long merchantId;
    private Long applicationId;
    private Integer versionNo;
    private String action;
    private String reason;
    private Integer statusFrom;
    private Integer statusTo;
    private Long operatorId;
    private Integer operatorType;
    private Long reviewerId;
    private LocalDateTime reviewTime;
}
