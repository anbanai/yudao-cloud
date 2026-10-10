package cn.iocoder.yudao.module.pay.dal.dataobject.payfund.yeepay;

import cn.iocoder.yudao.framework.tenant.core.db.TenantBaseDO;
import com.baomidou.mybatisplus.annotation.KeySequence;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.Accessors;
import java.time.LocalDateTime;

/** 对账差异是独立财务异常，不能被重跑静默忽略。金额单位分。 */
@TableName("pay_reconciliation_difference")
@KeySequence("pay_reconciliation_difference_seq")
@Data
@Accessors(chain = true)
@EqualsAndHashCode(callSuper = true)
public class PayReconciliationDifferenceDO extends TenantBaseDO {
    @TableId
    private Long id;
    private Long recordId;
    private String differenceKey;
    private String bizType;
    private String requestNo;
    private String orderNo;
    private String merchantNo;
    private String detailNo;
    private Long localAmount;
    private Long channelAmount;
    private Long differenceAmount;
    private String reason;
    /** 0 待人工处理，10 已处理；处理不自动修改资金业务状态。 */
    private Integer status;
    private Long operatorId;
    private String resolutionReason;
    private String externalEvidence;
    private LocalDateTime resolvedTime;
}
