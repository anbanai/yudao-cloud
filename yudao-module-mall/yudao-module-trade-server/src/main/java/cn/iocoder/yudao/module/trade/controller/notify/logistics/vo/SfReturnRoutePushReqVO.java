package cn.iocoder.yudao.module.trade.controller.notify.logistics.vo;

import lombok.Data;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

@Data
public class SfReturnRoutePushReqVO {
    @Size(max = 64)
    private String providerOrderNo;
    @Size(max = 64)
    private String waybillNo;
    @NotBlank
    @Size(max = 32)
    private String status;
    @NotBlank
    @Size(max = 128)
    private String eventId;
    @Size(max = 500)
    private String description;
    @Size(max = 255)
    private String location;
    private Long tenantId;
}
