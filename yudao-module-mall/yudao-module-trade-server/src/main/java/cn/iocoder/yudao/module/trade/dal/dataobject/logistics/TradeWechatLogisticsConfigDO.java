package cn.iocoder.yudao.module.trade.dal.dataobject.logistics;

import cn.iocoder.yudao.framework.tenant.core.db.TenantBaseDO;
import com.baomidou.mybatisplus.annotation.KeySequence;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;

/**
 * 微信物流助手租户配置。
 *
 * <p>只保存微信返回的 biz_id 和业务参数，不保存顺丰账号密码。</p>
 */
@TableName("trade_wechat_logistics_config")
@KeySequence("trade_wechat_logistics_config_seq")
@Data
@EqualsAndHashCode(callSuper = true)
public class TradeWechatLogisticsConfigDO extends TenantBaseDO {

    @TableId
    private Long id;
    private Integer userType;
    private String deliveryId;
    private String bizId;
    private Integer serviceType;
    private String serviceName;
    private Boolean enabled;

    private String senderName;
    private String senderTel;
    private String senderMobile;
    private String senderCompany;
    private String senderPostCode;
    private String senderCountry;
    private String senderProvince;
    private String senderCity;
    private String senderArea;
    private String senderAddress;

    private BigDecimal defaultWeight;
    private BigDecimal defaultSpaceLength;
    private BigDecimal defaultSpaceWidth;
    private BigDecimal defaultSpaceHeight;
}
