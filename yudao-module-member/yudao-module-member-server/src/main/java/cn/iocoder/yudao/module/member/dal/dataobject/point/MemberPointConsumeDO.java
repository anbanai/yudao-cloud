package cn.iocoder.yudao.module.member.dal.dataobject.point;

import cn.iocoder.yudao.framework.mybatis.core.dataobject.BaseDO;
import com.baomidou.mybatisplus.annotation.KeySequence;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.*;

@TableName("member_point_consume")
@KeySequence("member_point_consume_seq")
@Data
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MemberPointConsumeDO extends BaseDO {
    @TableId
    private Long id;
    private Long userId;
    private Long batchId;
    private Long recordId;
    private Integer quantity;
    private String bizId;
    private Integer restored;
}
