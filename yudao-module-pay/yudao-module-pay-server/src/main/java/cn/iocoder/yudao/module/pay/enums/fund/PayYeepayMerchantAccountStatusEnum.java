package cn.iocoder.yudao.module.pay.enums.fund;

import lombok.AllArgsConstructor;
import lombok.Getter;

import java.util.Objects;

/** 易宝商户资金账户状态。 */
@Getter
@AllArgsConstructor
public enum PayYeepayMerchantAccountStatusEnum {

    ENABLED(0, "启用"),
    DISABLED(1, "停用"),
    PENDING(2, "待审核"),
    FAILED(3, "审核失败");

    public static final PayYeepayMerchantAccountStatusEnum ACTIVE = ENABLED;

    private final Integer status;
    private final String name;

    public static boolean isEnabled(Integer status) {
        return Objects.equals(status, ENABLED.status);
    }

    public static boolean isDisabled(Integer status) {
        return Objects.equals(status, DISABLED.status);
    }

    public static boolean isPending(Integer status) {
        return Objects.equals(status, PENDING.status);
    }
}
