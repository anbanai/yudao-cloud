package cn.iocoder.yudao.module.trade.controller.admin.logistics.vo;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class SfLogisticsAccountSaveReqVO {
    private Long id;
    @NotBlank @Size(max = 64)
    private String name = "顺丰开放平台";
    @Size(max = 64)
    private String partnerId;
    @Size(max = 128)
    private String checkWord;
    private Boolean defaultFlag = true;
    @NotNull @Min(0) @Max(1)
    private Integer status = 0;
}
