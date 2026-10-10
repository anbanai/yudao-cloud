package cn.iocoder.yudao.module.pay.enums.fund;

import lombok.AllArgsConstructor;
import lombok.Getter;

import java.util.Objects;

/** 退款资金明细状态。 */
@Getter
@AllArgsConstructor
public enum PayRefundFundDetailStatusEnum {

    PENDING(0, "待处理"),
    PROCESSING(5, "处理中"),
    SUCCESS(10, "成功"),
    FAILED(20, "失败"),
    RETRYING(25, "重试中"),
    MANUAL_REQUIRED(26, "待人工处理"),
    CLOSED(30, "已关闭");

    public static final PayRefundFundDetailStatusEnum WAITING = PENDING;
    public static final PayRefundFundDetailStatusEnum FAILURE = FAILED;

    private final Integer status;
    private final String name;

    public static boolean isPending(Integer status) {
        return Objects.equals(status, PENDING.status);
    }

    public static boolean isWaiting(Integer status) {
        return isPending(status);
    }

    public static boolean isProcessing(Integer status) {
        return Objects.equals(status, PROCESSING.status);
    }

    public static boolean isSuccess(Integer status) {
        return Objects.equals(status, SUCCESS.status);
    }

    public static boolean isFailed(Integer status) {
        return Objects.equals(status, FAILED.status);
    }

    public static boolean isFailure(Integer status) {
        return isFailed(status);
    }

    public static boolean isClosed(Integer status) {
        return Objects.equals(status, CLOSED.status);
    }
}
