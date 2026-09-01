package cn.iocoder.yudao.module.product.dal.dataobject.group;

import cn.iocoder.yudao.framework.tenant.core.db.TenantBaseDO;
import com.baomidou.mybatisplus.annotation.KeySequence;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.*;

@TableName("product_group_spu")
@KeySequence("product_group_spu_seq")
@Data
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ProductGroupSpuDO extends TenantBaseDO {

    @TableId
    private Long id;
    private Long groupId;
    private Long spuId;
    private Integer sort;
}
