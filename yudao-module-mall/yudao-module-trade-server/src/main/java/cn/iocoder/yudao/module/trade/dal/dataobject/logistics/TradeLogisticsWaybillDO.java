package cn.iocoder.yudao.module.trade.dal.dataobject.logistics;

import cn.iocoder.yudao.framework.mybatis.core.type.EncryptTypeHandler;
import cn.iocoder.yudao.framework.tenant.core.db.TenantBaseDO;
import com.baomidou.mybatisplus.annotation.FieldStrategy;
import com.baomidou.mybatisplus.annotation.KeySequence;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.Data;
import lombok.ToString;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

@TableName(value = "trade_logistics_waybill", autoResultMap = true)
@KeySequence("trade_logistics_waybill_seq")
@Data
@EqualsAndHashCode(callSuper = true)
public class TradeLogisticsWaybillDO extends TenantBaseDO {

    @TableId
    private Long id;
    private Long orderId;
    private Long subOrderId;
    private Long merchantId;
    @TableField(insertStrategy = FieldStrategy.NEVER, updateStrategy = FieldStrategy.NEVER)
    private Long activeOrderId;
    private String orderNo;
    private Long accountId;
    private Long logisticsId;
    private Long requestedDeviceId;
    private String providerOrderNo;
    private String waybillNo;
    private String status;
    private Boolean creationRejected;
    private Long labelFileId;
    private String labelUrl;
    private String labelContentType;
    private String labelChecksum;
    private Long labelSize;
    /** 本次建单的完整顺丰请求，含月结卡与寄收件信息，加密保存供重试及追溯使用。 */
    @TableField(typeHandler = EncryptTypeHandler.class)
    @ToString.Exclude
    @JsonIgnore
    private String createRequestSnapshot;
    private String templateCode;
    private Integer paperWidthMm;
    private Integer paperHeightMm;
    private Integer dpi;
    private String deliveryStatus;
    private String errorCode;
    private String errorMessage;
    private String providerResponse;
    private LocalDateTime lastSyncTime;
    private LocalDateTime deliveredTime;
    private LocalDateTime cancelledTime;
}
