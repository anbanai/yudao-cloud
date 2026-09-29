package cn.iocoder.yudao.module.member.dal.dataobject.benefit;

import cn.iocoder.yudao.framework.mybatis.core.dataobject.BaseDO;
import com.baomidou.mybatisplus.annotation.KeySequence;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.*;

@TableName("member_benefit_ledger")
@KeySequence("member_benefit_ledger_seq")
@Data @EqualsAndHashCode(callSuper = true) @ToString(callSuper = true)
@Builder @NoArgsConstructor @AllArgsConstructor
public class MemberBenefitLedgerDO extends BaseDO {
    @TableId private Long id;
    private Long userId;
    private Long accountId;
    private Long benefitId;
    private Integer eventType;
    private Integer quantity;
    private String bizId;
    private String reason;
    private Long operatorId;
}
