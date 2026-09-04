package cn.iocoder.yudao.module.trade.dal.dataobject.logistics;

import cn.iocoder.yudao.framework.tenant.core.db.TenantBaseDO;
import com.baomidou.mybatisplus.annotation.KeySequence;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

@TableName("trade_logistics_waybill")
@KeySequence("trade_logistics_waybill_seq")
@Data
@EqualsAndHashCode(callSuper = true)
public class TradeLogisticsWaybillDO extends TenantBaseDO {

    @TableId
    private Long id;
    private Long orderId;
    private String orderNo;
    private Long accountId;
    private Long logisticsId;
    private Long requestedDeviceId;
    private String providerOrderNo;
    private String waybillNo;
    private String status;
    private Long labelFileId;
    private String labelUrl;
    private String labelContentType;
    private String labelChecksum;
    private Long labelSize;
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
