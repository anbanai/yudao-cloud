package cn.iocoder.yudao.module.pay.dal.dataobject.payfund;

import cn.iocoder.yudao.framework.tenant.core.db.TenantBaseDO;
import cn.iocoder.yudao.module.pay.enums.fund.PayRefundFundDetailStatusEnum;
import com.baomidou.mybatisplus.annotation.KeySequence;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.ToString;
import lombok.experimental.Accessors;

import java.time.LocalDateTime;

/** 退款资金明细 DO。 */
@TableName("pay_refund_fund_detail")
@KeySequence("pay_refund_fund_detail_seq")
@Data
@Accessors(chain = true)
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
public class PayRefundFundDetailDO extends TenantBaseDO {

    @TableId
    private Long id;
    private String no;
    private Long refundId;
    private String refundNo;
    private Long payOrderId;
    private String payOrderNo;
    private Long fundOrderId;
    private String fundOrderNo;
    private Long fundDetailId;
    private String fundDetailNo;
    private Long afterSaleId;
    private Long merchantId;
    private String divideDetailNo;
    private String divideBackRequestId;
    private Long appId;
    private Long channelId;
    private String channelCode;
    private String merchantRefundId;
    private Integer amount;
    private Integer price;
    /** 退款与资金归还分别追踪，避免退款成功掩盖归还异常。 */
    private Integer refundStatus;
    private Integer divideBackStatus;
    private Integer status;
    private String reason;
    private String channelRefundNo;
    private String channelErrorCode;
    private String channelErrorMsg;
    private String channelNotifyData;
    private Integer retryCount;
    private LocalDateTime nextRetryTime;
    private String lastError;
    private LocalDateTime successTime;
    /** 资金归还独立完成时间，用于按归还日对账，不能复用退款完成时间。 */
    private LocalDateTime divideBackSuccessTime;

    /** 枚举 {@link PayRefundFundDetailStatusEnum}。 */
}
