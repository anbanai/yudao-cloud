package cn.iocoder.yudao.module.promotion.enums.coupon;

import lombok.AllArgsConstructor;
import lombok.Getter;

/** 管理员批量发券批次状态。 */
@Getter
@AllArgsConstructor
public enum CouponSendBatchStatusEnum {
    PENDING(0, "待提交"),
    SUCCEEDED(1, "已完成"),
    FAILED(2, "失败"),
    CANCELED(3, "已取消");

    private final Integer status;
    private final String name;
}
