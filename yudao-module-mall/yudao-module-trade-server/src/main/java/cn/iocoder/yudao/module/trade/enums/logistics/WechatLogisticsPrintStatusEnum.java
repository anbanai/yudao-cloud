package cn.iocoder.yudao.module.trade.enums.logistics;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * 微信 PC 打单软件只能由仓库人工确认打印结果，因此不建模本地打印回执。
 */
@Getter
@RequiredArgsConstructor
public enum WechatLogisticsPrintStatusEnum {

    PENDING("待确认打印"),
    CONFIRMED("已确认打印");

    private final String name;
}
