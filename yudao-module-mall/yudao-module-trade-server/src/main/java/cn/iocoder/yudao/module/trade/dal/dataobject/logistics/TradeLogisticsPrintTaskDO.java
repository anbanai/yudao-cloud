package cn.iocoder.yudao.module.trade.dal.dataobject.logistics;

import cn.iocoder.yudao.framework.tenant.core.db.TenantBaseDO;
import com.baomidou.mybatisplus.annotation.KeySequence;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

@TableName("trade_logistics_print_task")
@KeySequence("trade_logistics_print_task_seq")
@Data
@EqualsAndHashCode(callSuper = true)
public class TradeLogisticsPrintTaskDO extends TenantBaseDO {

    @TableId
    private Long id;
    private String requestId;
    private String jobId;
    private Long orderId;
    private Long waybillId;
    private Long deviceId;
    private String status;
    private String format;
    private String labelUrl;
    private String checksum;
    private Integer paperWidthMm;
    private Integer paperHeightMm;
    private Integer dpi;
    private Integer copies;
    private Boolean testFlag;
    private LocalDateTime leaseExpireTime;
    private LocalDateTime dispatchedTime;
    private LocalDateTime acceptedTime;
    private LocalDateTime completedTime;
    private String lastError;
}
