package cn.iocoder.yudao.module.trade.dal.dataobject.aftersale;

import cn.iocoder.yudao.framework.tenant.core.db.TenantBaseDO;
import cn.iocoder.yudao.module.trade.enums.aftersale.ReturnShipmentFeePayerEnum;
import cn.iocoder.yudao.module.trade.enums.aftersale.ReturnShipmentStatusEnum;
import com.baomidou.mybatisplus.annotation.KeySequence;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.experimental.Accessors;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@TableName("trade_after_sale_return_shipment")
@KeySequence("trade_after_sale_return_shipment_seq")
@Data
@Accessors(chain = true)
@EqualsAndHashCode(callSuper = true)
public class AfterSaleReturnShipmentDO extends TenantBaseDO {

    private Long id;
    private Long afterSaleId;
    private Long accountId;
    private String provider;
    private String providerOrderNo;
    private String waybillNo;
    private String status;
    private String idempotencyKey;
    private Long addressId;
    private String pickupName;
    private String pickupMobile;
    private Integer pickupAreaId;
    private String pickupProvince;
    private String pickupCity;
    private String pickupDistrict;
    private String pickupAddress;
    private String warehouseName;
    private String warehousePhone;
    private String warehouseProvince;
    private String warehouseCity;
    private String warehouseDistrict;
    private String warehouseAddress;
    private LocalDateTime appointmentTime;
    private BigDecimal packageWeight;
    private String feePayer;
    private Integer estimatedFee;
    private Integer actualFee;
    /** 实际从退款金额扣除的运费，单位：分。用于退款重试幂等。 */
    private Integer refundDeductedFee;
    private String errorCode;
    private String errorMessage;
    private String providerResponse;
    private LocalDateTime lastSyncTime;
    private LocalDateTime cancelledTime;
    private LocalDateTime deliveredTime;

    public ReturnShipmentStatusEnum statusEnum() {
        if (status == null) return null;
        try {
            return ReturnShipmentStatusEnum.valueOf(status);
        } catch (IllegalArgumentException exception) {
            return null;
        }
    }

    public ReturnShipmentFeePayerEnum feePayerEnum() {
        if (feePayer == null) return null;
        try {
            return ReturnShipmentFeePayerEnum.valueOf(feePayer);
        } catch (IllegalArgumentException exception) {
            return null;
        }
    }
}
