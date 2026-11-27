package cn.iocoder.yudao.module.trade.dal.dataobject.logistics;

import cn.iocoder.yudao.framework.mybatis.core.type.EncryptTypeHandler;
import cn.iocoder.yudao.framework.tenant.core.db.TenantBaseDO;
import com.baomidou.mybatisplus.annotation.KeySequence;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;

@TableName(value = "trade_merchant_logistics_config", autoResultMap = true)
@KeySequence("trade_merchant_logistics_config_seq")
@Data
@EqualsAndHashCode(callSuper = true)
public class TradeMerchantLogisticsConfigDO extends TenantBaseDO {

    @TableId
    private Long id;
    private Long merchantId;
    @TableField(typeHandler = EncryptTypeHandler.class)
    private String monthlyCard;
    private String serviceCode;
    private BigDecimal defaultWeightKg;
    private Integer paperWidthMm;
    private Integer paperHeightMm;
    private Integer dpi;
    private String senderName;
    private String senderPhone;
    private String senderProvince;
    private String senderCity;
    private String senderDistrict;
    private String senderAddress;
    private Boolean returnSameAsSender;
    private String returnName;
    private String returnPhone;
    private String returnProvince;
    private String returnCity;
    private String returnDistrict;
    private String returnAddress;
}
