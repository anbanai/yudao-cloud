package cn.iocoder.yudao.module.pay.dal.dataobject.payfund.yeepay;

import cn.iocoder.yudao.framework.tenant.core.db.TenantBaseDO;
import cn.iocoder.yudao.module.pay.enums.fund.PayYeepayEventStatusEnum;
import com.baomidou.mybatisplus.annotation.KeySequence;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.ToString;
import lombok.experimental.Accessors;

import java.time.LocalDateTime;

/** 易宝异步事件接收记录 DO。 */
@TableName("pay_yeepay_event")
@KeySequence("pay_yeepay_event_seq")
@Data
@Accessors(chain = true)
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
public class PayYeepayEventDO extends TenantBaseDO {

    @TableId
    private Long id;
    private String eventId;
    private String eventNo;
    private String eventType;
    private String merchantNo;
    private String requestId;
    private String fundOrderNo;
    private String payload;
    private String rawDataRef;
    /** 回调原文及 SDK 安全头的 AES-GCM 密文，禁止通过普通查询或日志导出。 */
    @JsonIgnore
    @ToString.Exclude
    private String encryptedOriginal;
    private String decryptedSummary;
    private Integer status;
    private Integer ackStatus;
    private Integer retryCount;
    private String lastError;
    private LocalDateTime eventTime;
    private LocalDateTime processedTime;

    /** 枚举 {@link PayYeepayEventStatusEnum}。 */
}
