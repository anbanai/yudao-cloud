package cn.iocoder.yudao.module.pay.dal.dataobject.payfund;

import cn.iocoder.yudao.framework.tenant.core.db.TenantBaseDO;
import cn.iocoder.yudao.module.pay.enums.fund.PayDivideRequestStatusEnum;
import com.baomidou.mybatisplus.annotation.KeySequence;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.ToString;
import lombok.experimental.Accessors;

import java.time.LocalDateTime;

/** 分账请求 DO。 */
@TableName("pay_divide_request")
@KeySequence("pay_divide_request_seq")
@Data
@Accessors(chain = true)
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
public class PayDivideRequestDO extends TenantBaseDO {

    @TableId
    private Long id;
    private String no;
    private String requestNo;
    private Long appId;
    private Long channelId;
    private String channelCode;
    private Long fundOrderId;
    private String fundOrderNo;
    private Long fundDetailId;
    private String fundDetailNo;
    private Long tradeOrderId;
    private Long tradeOrderSubId;
    private Long merchantId;
    private String divideDetailNo;
    private String merchantOrderId;
    private String merchantRequestNo;
    private Integer amount;
    private Integer price;
    private Integer status;
    private Integer retryCount;
    private LocalDateTime nextRetryTime;
    private String channelRequestNo;
    private String requestData;
    private String responseData;
    private String channelErrorCode;
    private String channelErrorMsg;
    private LocalDateTime successTime;

    /** 枚举 {@link PayDivideRequestStatusEnum}。 */
}
