package cn.iocoder.yudao.module.member.dal.dataobject.benefit;

import cn.iocoder.yudao.framework.mybatis.core.dataobject.BaseDO;
import com.baomidou.mybatisplus.annotation.KeySequence;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.*;

@TableName("member_level_benefit")
@KeySequence("member_level_benefit_seq")
@Data @EqualsAndHashCode(callSuper = true) @ToString(callSuper = true)
@Builder @NoArgsConstructor @AllArgsConstructor
public class MemberLevelBenefitDO extends BaseDO {
    @TableId private Long id;
    private Long levelId;
    private Long benefitId;
    private Integer quantity;
    private Integer periodType;
    private Integer claimType;
    private Integer validityDays;
    /** Birthday coupon issuance window in days before the birthday. */
    private Integer birthdayAdvanceDays;
    private Integer status;
    private Integer sort;
}
