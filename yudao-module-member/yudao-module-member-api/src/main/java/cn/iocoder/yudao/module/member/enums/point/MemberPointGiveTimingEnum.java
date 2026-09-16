package cn.iocoder.yudao.module.member.enums.point;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 订单赠送积分发放时机。
 */
@Getter
@AllArgsConstructor
public enum MemberPointGiveTimingEnum {

    PAY(1, "支付后发放"),
    RECEIVE(2, "收货后发放");

    private final Integer type;
    private final String name;

    public static MemberPointGiveTimingEnum getByType(Integer type) {
        for (MemberPointGiveTimingEnum value : values()) {
            if (value.type.equals(type)) {
                return value;
            }
        }
        return null;
    }
}
