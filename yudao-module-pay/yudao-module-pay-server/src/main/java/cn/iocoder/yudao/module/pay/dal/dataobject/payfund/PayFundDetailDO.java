package cn.iocoder.yudao.module.pay.dal.dataobject.payfund;

import cn.iocoder.yudao.framework.tenant.core.db.TenantBaseDO;
import cn.iocoder.yudao.module.pay.enums.fund.PayFundDetailStatusEnum;
import com.baomidou.mybatisplus.annotation.KeySequence;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.ToString;
import lombok.experimental.Accessors;

import java.time.LocalDateTime;

/** 资金账户明细 DO。 */
@TableName("pay_fund_detail")
@KeySequence("pay_fund_detail_seq")
@Data
@Accessors(chain = true)
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
public class PayFundDetailDO extends TenantBaseDO {

    @TableId
    private Long id;
    private String no;
    private Long fundOrderId;
    private String fundOrderNo;
    private Long payOrderId;
    private String payOrderNo;
    private Long tradeOrderId;
    private Long tradeOrderSubId;
    private Long tradeOrderItemId;

    /** 易宝商户或分账接收方标识。 */
    private Long accountId;
    private String accountNo;
    private Long merchantId;
    private String merchantNo;
    private String merchantName;
    private String divideDetailNo;

    /** 业务类型和资金方向由调用方约定，避免耦合支付 API。 */
    private Integer bizType;
    private Integer direction;
    private Integer amount;
    private Integer price;
    /** 实际分账执行金额快照，原 amount 不变；已退款金额从执行额扣除。 */
    private Integer divideAmount;
    private Integer feePrice;
    /** 已成功退款金额，单位：分。 */
    private Integer refundedAmount;
    /** 已成功归还金额，单位：分。 */
    private Integer returnedAmount;
    private Integer status;
    private String currency;
    private LocalDateTime successTime;
    private String channelDetailNo;
    private String channelErrorCode;
    private String channelErrorMsg;
    private String remark;

    /** 枚举 {@link PayFundDetailStatusEnum}。 */
    public Integer getFundStatus() {
        return status;
    }

    public PayFundDetailDO setFundStatus(Integer fundStatus) {
        this.status = fundStatus;
        return this;
    }
}
