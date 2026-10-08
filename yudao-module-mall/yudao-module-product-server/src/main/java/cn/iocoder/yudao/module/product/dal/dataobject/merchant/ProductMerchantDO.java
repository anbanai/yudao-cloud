package cn.iocoder.yudao.module.product.dal.dataobject.merchant;

import cn.iocoder.yudao.framework.mybatis.core.dataobject.BaseDO;
import cn.iocoder.yudao.module.product.enums.merchant.ProductMerchantStatusEnum;
import com.baomidou.mybatisplus.annotation.KeySequence;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.*;

import java.time.LocalDateTime;

/** 平台商家 DO。 */
@TableName("product_merchant")
@KeySequence("product_merchant_seq")
@Data
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ProductMerchantDO extends BaseDO {

    @TableId
    private Long id;
    private String name;
    private String storeName;
    private String logoUrl;
    private String companyName;
    private String creditCode;
    private String legalPerson;
    private String contactName;
    private String contactMobile;
    private Integer status;
    private Long reviewerId;
    private LocalDateTime reviewTime;
    private String rejectReason;
}
