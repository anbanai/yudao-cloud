package cn.iocoder.yudao.module.trade.dal.dataobject.logistics;

import cn.iocoder.yudao.framework.tenant.core.db.TenantBaseDO;
import com.baomidou.mybatisplus.annotation.KeySequence;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

/**
 * 微信物流助手运单。
 */
@TableName("trade_wechat_logistics_waybill")
@KeySequence("trade_wechat_logistics_waybill_seq")
@Data
@EqualsAndHashCode(callSuper = true)
public class TradeWechatLogisticsWaybillDO extends TenantBaseDO {

    @TableId
    private Long id;
    private Long orderId;
    private String orderNo;
    private String wechatOrderId;
    private String openid;
    private String deliveryId;
    private String bizId;
    private String waybillId;
    private String status;
    private String printStatus;
    private Integer wechatOrderStatus;
    private Integer errorCode;
    private String errorMessage;
    private String waybillData;
    private LocalDateTime lastSyncTime;
}
