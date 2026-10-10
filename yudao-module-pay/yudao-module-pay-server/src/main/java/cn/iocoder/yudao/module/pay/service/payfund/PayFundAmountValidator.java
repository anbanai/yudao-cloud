package cn.iocoder.yudao.module.pay.service.payfund;

import java.math.BigDecimal;

/**
 * Validates monetary amounts represented in the smallest currency unit.
 *
 * <p>Fund records use positive integer amounts in cents. Keeping this check
 * in one small, dependency-free utility makes it safe to reuse from command
 * handlers and scheduled reconciliation jobs.</p>
 */
public final class PayFundAmountValidator {

    private PayFundAmountValidator() {
    }

    public static boolean isPositive(Number amount) {
        if (amount == null) {
            return false;
        }
        if (amount instanceof BigDecimal decimal) {
            return decimal.signum() > 0;
        }
        double value = amount.doubleValue();
        return !Double.isNaN(value) && !Double.isInfinite(value) && value > 0;
    }

    public static <T extends Number> T requirePositive(T amount) {
        if (!isPositive(amount)) {
            throw new IllegalArgumentException("资金金额必须大于 0");
        }
        return amount;
    }

    public static boolean isNonNegative(Number amount) {
        if (amount == null) {
            return false;
        }
        if (amount instanceof BigDecimal decimal) {
            return decimal.signum() >= 0;
        }
        double value = amount.doubleValue();
        return !Double.isNaN(value) && !Double.isInfinite(value) && value >= 0;
    }

    public static <T extends Number> T requireNonNegative(T amount) {
        if (!isNonNegative(amount)) {
            throw new IllegalArgumentException("资金金额不能为负数");
        }
        return amount;
    }
}
