package cn.iocoder.yudao.module.product.dal.dataobject.group;

import cn.iocoder.yudao.framework.mybatis.core.dataobject.BaseDO;
import com.baomidou.mybatisplus.annotation.KeySequence;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.Accessors;

@TableName("product_group")
@KeySequence("product_group_seq")
@Data
@EqualsAndHashCode(callSuper = true)
@Accessors(chain = true)
public class ProductGroupDO extends BaseDO {
    @TableId private Long id;
    private String name;
    private Boolean storefrontVisible;
    private Integer sort;
    private Integer status;
    private String remark;
}
