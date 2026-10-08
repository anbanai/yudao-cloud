package cn.iocoder.yudao.module.member.dal.dataobject.benefit;

import cn.iocoder.yudao.framework.mybatis.core.dataobject.BaseDO;
import com.baomidou.mybatisplus.annotation.KeySequence;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.*;

@TableName("member_benefit_definition")
@KeySequence("member_benefit_definition_seq")
@Data @EqualsAndHashCode(callSuper = true) @ToString(callSuper = true)
@Builder @NoArgsConstructor @AllArgsConstructor
public class MemberBenefitDefinitionDO extends BaseDO {
    @TableId private Long id;
    private String code;
    private String name;
    private Integer type;
    private String description;
    private String icon;
    private Integer actionType;
    private Long couponTemplateId;
    private Integer status;
    private Integer sort;
}
