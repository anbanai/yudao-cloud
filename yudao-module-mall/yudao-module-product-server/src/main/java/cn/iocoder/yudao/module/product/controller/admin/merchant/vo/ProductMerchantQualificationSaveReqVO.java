package cn.iocoder.yudao.module.product.controller.admin.merchant.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.time.LocalDate;

@Schema(description = "管理后台 - 商家资质新增/更新 Request VO")
@Data
public class ProductMerchantQualificationSaveReqVO {
    private Long id;
    @NotNull(message = "商家编号不能为空")
    private Long merchantId;
    @NotBlank(message = "资质类型不能为空")
    private String type;
    private String certificateNo;
    @NotBlank(message = "资质文件不能为空")
    private String fileUrl;
    private LocalDate validStart;
    private LocalDate validEnd;
}
