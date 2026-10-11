package cn.iocoder.yudao.module.product.dal.dataobject.merchant;

import cn.iocoder.yudao.framework.mybatis.core.dataobject.BaseDO;
import cn.iocoder.yudao.module.product.framework.merchant.core.MerchantSensitiveTypeHandler;
import com.baomidou.mybatisplus.annotation.KeySequence;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.ToString;

import java.time.LocalDate;

/** Qualification data attached to an application version; locked when the version is submitted. */
@TableName(value = "product_merchant_application_qualification", autoResultMap = true)
@KeySequence("product_merchant_application_qualification_seq")
@Data
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
public class ProductMerchantApplicationQualificationDO extends BaseDO {

    @TableId
    private Long id;
    private Long applicationId;
    private String code;
    private String name;
    private Boolean required;
    private Boolean certificateNoRequired;
    private String certificateNoPattern;
    private Boolean validityRequired;
    @TableField(typeHandler = MerchantSensitiveTypeHandler.class)
    @ToString.Exclude
    private String certificateNo;
    private Long fileId;
    @TableField(typeHandler = MerchantSensitiveTypeHandler.class)
    @ToString.Exclude
    private String legacyFileUrl;
    private LocalDate validStart;
    private LocalDate validEnd;
}
