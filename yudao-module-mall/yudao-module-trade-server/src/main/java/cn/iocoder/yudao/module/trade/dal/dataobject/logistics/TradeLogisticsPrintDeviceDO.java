package cn.iocoder.yudao.module.trade.dal.dataobject.logistics;

import cn.iocoder.yudao.framework.tenant.core.db.TenantBaseDO;
import com.baomidou.mybatisplus.annotation.KeySequence;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

@TableName("trade_logistics_print_device")
@KeySequence("trade_logistics_print_device_seq")
@Data
@EqualsAndHashCode(callSuper = true)
public class TradeLogisticsPrintDeviceDO extends TenantBaseDO {

    @TableId
    private Long id;
    private String deviceCode;
    private String deviceName;
    private String tokenHash;
    private Boolean defaultFlag;
    /** 0 enabled, 1 disabled. */
    private Integer status;
    private String version;
    private LocalDateTime lastPollTime;
    private LocalDateTime tokenCreatedTime;
    private LocalDateTime disabledTime;

    @Override
    public TradeLogisticsPrintDeviceDO setTenantId(Long tenantId) {
        super.setTenantId(tenantId);
        return this;
    }
}
