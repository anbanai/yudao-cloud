package cn.iocoder.yudao.module.trade.dal.dataobject.aftersale;

import cn.iocoder.yudao.framework.tenant.core.db.TenantBaseDO;
import com.baomidou.mybatisplus.annotation.KeySequence;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.Accessors;

import java.time.LocalDateTime;

@TableName("trade_after_sale_return_trace")
@KeySequence("trade_after_sale_return_trace_seq")
@Data
@Accessors(chain = true)
@EqualsAndHashCode(callSuper = true)
public class AfterSaleReturnTraceDO extends TenantBaseDO {

    private Long id;
    private Long returnShipmentId;
    private String provider;
    private String status;
    private String description;
    private String location;
    private LocalDateTime occurredTime;
    private String eventId;
}
