package cn.iocoder.yudao.module.trade.controller.app.aftersale.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import cn.iocoder.yudao.framework.desensitize.core.slider.annotation.MobileDesensitize;
import lombok.Data;
import lombok.experimental.Accessors;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Data
@Schema(description = "用户 App - 退货上门取件 Response VO")
public class AppReturnShipmentRespVO {

    private Long id;
    private Long afterSaleId;
    private String provider;
    private String providerOrderNo;
    private String waybillNo;
    private String status;
    private String statusName;
    private Long addressId;
    private String pickupName;
    @MobileDesensitize
    private String pickupMobile;
    private String pickupContactName;
    @MobileDesensitize
    private String pickupContactMobile;
    private String pickupProvince;
    private String pickupCity;
    private String pickupDistrict;
    private String pickupAddress;
    private String warehouseName;
    private String warehouseAddress;
    private String returnAddress;
    private BigDecimal packageWeight;
    private LocalDateTime appointmentTime;
    private String feePayer;
    private String feePayerName;
    private Integer estimatedFee;
    private Integer actualFee;
    private Integer refundDeductedFee;
    private String errorCode;
    private String errorMessage;
    private List<Trace> traces;

    @Data
    @Accessors(chain = true)
    public static class Trace {
        private String status;
        private String description;
        private String location;
        private LocalDateTime occurredTime;
    }
}
