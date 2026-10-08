package cn.iocoder.yudao.module.trade.controller.app.aftersale.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@Schema(description = "用户 App - 创建退货上门取件 Request VO")
public class AppReturnShipmentCreateReqVO {

    @NotNull
    private Long addressId;
    @NotNull
    @DecimalMin(value = "0.01")
    private BigDecimal packageWeight;
    @NotNull
    private LocalDateTime appointmentTime;
    @Size(max = 64)
    private String idempotencyKey;
    /** 小程序历史字段别名，服务端统一作为幂等键处理。 */
    @Size(max = 64)
    private String requestNo;
    @Size(max = 64)
    private String contactName;
    @Size(max = 32)
    private String contactMobile;
    @Size(max = 64)
    private String provinceName;
    @Size(max = 64)
    private String cityName;
    @Size(max = 64)
    private String districtName;
    @Size(max = 255)
    private String detailAddress;

    public String effectiveIdempotencyKey() {
        return idempotencyKey != null && !idempotencyKey.isBlank() ? idempotencyKey : requestNo;
    }
}
