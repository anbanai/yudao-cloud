package cn.iocoder.yudao.module.member.dal.dataobject.point;

import cn.iocoder.yudao.framework.mybatis.core.dataobject.BaseDO;
import com.baomidou.mybatisplus.annotation.KeySequence;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.*;

import java.time.LocalDateTime;

@TableName("member_point_batch")
@KeySequence("member_point_batch_seq")
@Data
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MemberPointBatchDO extends BaseDO {
    @TableId
    private Long id;
    private Long userId;
    private Long sourceRecordId;
    private Integer originalPoint;
    private Integer remainingPoint;
    private LocalDateTime earnedTime;
    private LocalDateTime expireTime;
    private Integer legacy;
    private Integer status;
}
