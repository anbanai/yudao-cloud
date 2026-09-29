package cn.iocoder.yudao.module.member.dal.dataobject.benefit;

import cn.iocoder.yudao.framework.mybatis.core.dataobject.BaseDO;
import com.baomidou.mybatisplus.annotation.KeySequence;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.*;
import java.time.LocalDateTime;

@TableName("member_user_benefit")
@KeySequence("member_user_benefit_seq")
@Data @EqualsAndHashCode(callSuper = true) @ToString(callSuper = true)
@Builder @NoArgsConstructor @AllArgsConstructor
public class MemberUserBenefitDO extends BaseDO {
    @TableId private Long id;
    private Long userId;
    private Long levelId;
    private Long benefitId;
    private LocalDateTime periodStart;
    private LocalDateTime periodEnd;
    private Integer grantedQuantity;
    private Integer availableQuantity;
    private Integer usedQuantity;
    private Integer claimedQuantity;
    /** 领取方式：0 自动到账，1 用户领取 */
    private Integer claimType;
    private Integer claimStatus;
    private Integer status;
}
