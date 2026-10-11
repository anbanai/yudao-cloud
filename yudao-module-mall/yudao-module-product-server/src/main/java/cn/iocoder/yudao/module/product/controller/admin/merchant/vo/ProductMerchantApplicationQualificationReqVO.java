package cn.iocoder.yudao.module.product.controller.admin.merchant.vo;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.time.LocalDate;

@Data
public class ProductMerchantApplicationQualificationReqVO {

    @NotBlank
    private String code;
    @Size(max = 128)
    private String certificateNo;
    private Long fileId;
    private LocalDate validStart;
    private LocalDate validEnd;
}
