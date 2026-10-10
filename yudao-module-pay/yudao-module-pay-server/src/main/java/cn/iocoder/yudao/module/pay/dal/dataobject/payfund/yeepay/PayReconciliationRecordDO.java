package cn.iocoder.yudao.module.pay.dal.dataobject.payfund.yeepay;

import cn.iocoder.yudao.framework.tenant.core.db.TenantBaseDO;
import cn.iocoder.yudao.module.pay.enums.fund.PayReconciliationRecordStatusEnum;
import com.baomidou.mybatisplus.annotation.KeySequence;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.ToString;
import lombok.experimental.Accessors;

import java.time.LocalDate;
import java.time.LocalDateTime;

/** 易宝资金对账记录 DO。 */
@TableName("pay_reconciliation_record")
@KeySequence("pay_reconciliation_record_seq")
@Data
@Accessors(chain = true)
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
public class PayReconciliationRecordDO extends TenantBaseDO {

    @TableId
    private Long id;
    private String batchNo;
    private Long appId;
    private Long channelId;
    private String merchantNo;
    private String channelCode;
    private LocalDate reconciliationDate;
    private Integer totalCount;
    private Long totalAmount;
    private Long totalPrice;
    private Integer matchedCount;
    private Long matchedAmount;
    private Long differenceAmount;
    private Integer status;
    private String fileUrl;
    private String rawData;
    private String errorMsg;
    private LocalDateTime startedTime;
    private LocalDateTime completedTime;

    /** 枚举 {@link PayReconciliationRecordStatusEnum}。 */
}
