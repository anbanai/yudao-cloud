package cn.iocoder.yudao.module.trade.dal.dataobject.logistics;

import cn.iocoder.yudao.framework.tenant.core.db.TenantBaseDO;
import com.baomidou.mybatisplus.annotation.KeySequence;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

@TableName("trade_logistics_trace")
@KeySequence("trade_logistics_trace_seq")
@Data
@EqualsAndHashCode(callSuper = true)
public class TradeLogisticsTraceDO extends TenantBaseDO {

    @TableId
    private Long id;
    private Long waybillId;
    private String providerEventId;
    private String status;
    private String content;
    private String location;
    private LocalDateTime operateTime;
    private String rawData;
}
