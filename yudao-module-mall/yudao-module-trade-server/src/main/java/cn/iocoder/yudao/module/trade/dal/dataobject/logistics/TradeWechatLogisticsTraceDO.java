package cn.iocoder.yudao.module.trade.dal.dataobject.logistics;

import cn.iocoder.yudao.framework.tenant.core.db.TenantBaseDO;
import com.baomidou.mybatisplus.annotation.KeySequence;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

/**
 * 微信物流助手轨迹快照。
 */
@TableName("trade_wechat_logistics_trace")
@KeySequence("trade_wechat_logistics_trace_seq")
@Data
@EqualsAndHashCode(callSuper = true)
public class TradeWechatLogisticsTraceDO extends TenantBaseDO {

    @TableId
    private Long id;
    private Long waybillId;
    private LocalDateTime actionTime;
    private Integer actionType;
    private String actionMsg;
}
