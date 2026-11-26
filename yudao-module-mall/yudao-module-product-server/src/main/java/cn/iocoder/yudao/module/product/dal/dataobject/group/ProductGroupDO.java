package cn.iocoder.yudao.module.product.dal.dataobject.group;

import cn.iocoder.yudao.framework.common.enums.CommonStatusEnum;
import cn.iocoder.yudao.framework.tenant.core.db.TenantBaseDO;
import com.baomidou.mybatisplus.annotation.KeySequence;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.*;

@TableName("product_group")
@KeySequence("product_group_seq")
@Data
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ProductGroupDO extends TenantBaseDO {

    @TableId
    private Long id;
    private String name;
    private Integer sort;
    private Integer status;
    private String remark;

    public boolean isEnabled() {
        return CommonStatusEnum.ENABLE.getStatus().equals(status);
    }
}
