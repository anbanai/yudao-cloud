package cn.iocoder.yudao.module.pay.dal.dataobject.payfund;

import cn.iocoder.yudao.framework.tenant.core.db.TenantBaseDO;
import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import lombok.EqualsAndHashCode;

/** 追加式人工操作审计，业务不提供修改或删除接口。 */
@TableName("pay_fund_operation")
@KeySequence("pay_fund_operation_seq")
@Data
@EqualsAndHashCode(callSuper = true)
public class PayFundOperationDO extends TenantBaseDO {
    @TableId
    private Long id;
    private String action;
    private Long targetId;
    private Long operatorId;
    private String reason;
    private String externalEvidence;
    private String previousState;
}
