package cn.iocoder.yudao.module.pay.enums.fund;

import lombok.AllArgsConstructor;
import lombok.Getter;

import java.util.Objects;

/** 资金对账记录状态。 */
@Getter
@AllArgsConstructor
public enum PayReconciliationRecordStatusEnum {

    PENDING(0, "待对账"),
    PROCESSING(5, "对账中"),
    COMPLETED(10, "已完成"),
    FAILED(20, "失败"),
    MANUAL_REQUIRED(30, "待人工处理");

    public static final PayReconciliationRecordStatusEnum SUCCESS = COMPLETED;

    private final Integer status;
    private final String name;

    public static boolean isPending(Integer status) {
        return Objects.equals(status, PENDING.status);
    }

    public static boolean isProcessing(Integer status) {
        return Objects.equals(status, PROCESSING.status);
    }

    public static boolean isCompleted(Integer status) {
        return Objects.equals(status, COMPLETED.status);
    }

    public static boolean isSuccess(Integer status) {
        return isCompleted(status);
    }

    public static boolean isFailed(Integer status) {
        return Objects.equals(status, FAILED.status);
    }
}
