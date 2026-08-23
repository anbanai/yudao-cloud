package cn.iocoder.yudao.module.trade.enums.logistics;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * 微信物流助手运单状态。
 */
@Getter
@RequiredArgsConstructor
public enum WechatLogisticsWaybillStatusEnum {

    CREATING("创建中"),
    CREATED("已创建"),
    UNKNOWN("待确认"),
    FAILED("失败"),
    CANCELLED("已取消");

    private final String name;
}
