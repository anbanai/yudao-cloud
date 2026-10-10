package cn.iocoder.yudao.module.pay.dal.dataobject.payfund;

import cn.iocoder.yudao.module.pay.enums.fund.PayFundOrderStatusEnum;
import cn.iocoder.yudao.framework.tenant.core.db.TenantBaseDO;
import com.baomidou.mybatisplus.annotation.KeySequence;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.ToString;
import lombok.experimental.Accessors;

import java.time.LocalDateTime;

/**
 * 资金订单 DO。
 *
 * <p>该表记录支付成功后进入资金域的主流水，渠道扩展字段保留原始
 * 请求/通知内容，便于异步重试和对账。</p>
 */
@TableName("pay_fund_order")
@KeySequence("pay_fund_order_seq")
@Data
@Accessors(chain = true)
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
public class PayFundOrderDO extends TenantBaseDO {

    @TableId
    private Long id;

    /** 资金订单号。 */
    private String no;
    private Long appId;
    private Long channelId;
    private String channelCode;

    /** 关联支付订单。 */
    private Long payOrderId;
    private String payOrderNo;
    private String merchantOrderId;
    private Long userId;
    private Integer userType;

    /** 创建支付单时保存的交易/分账快照。 */
    private Long tradeOrderId;
    private Long tradeOrderSubId;
    private String dividePlanData;

    /** 资金总额，单位：分。 */
    private Integer amount;
    /** 与支付域保持一致的金额别名，单位：分。 */
    private Integer price;
    private Integer feePrice;
    private Integer dividePrice;
    private Integer refundPrice;

    /** 清算状态与分账状态分别维护，支付成功不等于资金处理成功。 */
    private Integer clearingStatus;
    private Integer divideStatus;
    private Integer retryCount;
    private LocalDateTime nextRetryTime;
    private String lastError;
    /** 易宝请求使用的商编快照，后续配置变更不得影响原订单。 */
    private String parentMerchantNo;
    private String collectionMerchantNo;

    /** 枚举 {@link PayFundOrderStatusEnum}。 */
    private Integer status;
    private String currency;
    private String notifyUrl;
    private LocalDateTime successTime;
    private String channelOrderNo;
    private String channelRequestNo;
    private String channelErrorCode;
    private String channelErrorMsg;
    private String channelNotifyData;
}
