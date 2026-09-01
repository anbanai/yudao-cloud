package cn.iocoder.yudao.module.trade.dal.dataobject.logistics;

import cn.iocoder.yudao.framework.mybatis.core.type.EncryptTypeHandler;
import cn.iocoder.yudao.framework.tenant.core.db.TenantBaseDO;
import com.baomidou.mybatisplus.annotation.KeySequence;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;

@TableName(value = "trade_logistics_account", autoResultMap = true)
@KeySequence("trade_logistics_account_seq")
@Data
@EqualsAndHashCode(callSuper = true)
public class TradeLogisticsAccountDO extends TenantBaseDO {

    @TableId
    private Long id;
    private String name;
    private Long logisticsId;
    private String endpoint;
    @TableField(typeHandler = EncryptTypeHandler.class)
    @JsonProperty(access = JsonProperty.Access.WRITE_ONLY)
    private String partnerId;
    @TableField(typeHandler = EncryptTypeHandler.class)
    @JsonProperty(access = JsonProperty.Access.WRITE_ONLY)
    private String checkWord;
    @TableField(typeHandler = EncryptTypeHandler.class)
    @JsonProperty(access = JsonProperty.Access.WRITE_ONLY)
    private String monthlyCard;
    private String serviceCode;
    private String templateCode;
    private String senderName;
    private String senderPhone;
    private String senderProvince;
    private String senderCity;
    private String senderDistrict;
    private String senderAddress;
    private BigDecimal defaultWeightKg;
    private Integer paperWidthMm;
    private Integer paperHeightMm;
    private Integer dpi;
    private Boolean defaultFlag;
    private Integer status;
}
