package cn.iocoder.yudao.module.member.enums.point;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 积分流水状态。
 */
@Getter
@AllArgsConstructor
public enum MemberPointRecordStatusEnum {

    EFFECTIVE(1),
    PENDING(2),
    CANCELLED(3);

    private final Integer status;
}
