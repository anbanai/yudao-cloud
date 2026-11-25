package cn.iocoder.yudao.module.trade.dal.dataobject.logistics;

import cn.iocoder.yudao.framework.mybatis.core.type.EncryptTypeHandler;
import cn.iocoder.yudao.framework.tenant.core.db.TenantBaseDO;
import com.baomidou.mybatisplus.annotation.KeySequence;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

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
}
