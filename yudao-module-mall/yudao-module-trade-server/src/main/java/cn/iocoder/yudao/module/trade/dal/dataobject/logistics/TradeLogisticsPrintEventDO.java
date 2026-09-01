package cn.iocoder.yudao.module.trade.dal.dataobject.logistics;

import cn.iocoder.yudao.framework.tenant.core.db.TenantBaseDO;
import com.baomidou.mybatisplus.annotation.KeySequence;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

@TableName("trade_logistics_print_event")
@KeySequence("trade_logistics_print_event_seq")
@Data
@EqualsAndHashCode(callSuper = true)
public class TradeLogisticsPrintEventDO extends TenantBaseDO {

    @TableId
    private Long id;
    private String eventId;
    private Long taskId;
    private Long deviceId;
    private String jobId;
    private String eventType;
    private String status;
    private String message;
    private LocalDateTime eventTime;
    private String rawPayload;
}
