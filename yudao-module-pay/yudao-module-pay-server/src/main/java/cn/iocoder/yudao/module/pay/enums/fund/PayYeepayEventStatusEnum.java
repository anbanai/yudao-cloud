package cn.iocoder.yudao.module.pay.enums.fund;

import lombok.AllArgsConstructor;
import lombok.Getter;

import java.util.Objects;

/** 易宝事件处理状态。 */
@Getter
@AllArgsConstructor
public enum PayYeepayEventStatusEnum {

    RECEIVED(0, "已接收"),
    PROCESSING(5, "处理中"),
    PROCESSED(10, "已处理"),
    FAILED(20, "处理失败"),
    IGNORED(30, "已忽略");

    public static final PayYeepayEventStatusEnum PENDING = RECEIVED;

    private final Integer status;
    private final String name;

    public static boolean isReceived(Integer status) {
        return Objects.equals(status, RECEIVED.status);
    }

    public static boolean isProcessing(Integer status) {
        return Objects.equals(status, PROCESSING.status);
    }

    public static boolean isProcessed(Integer status) {
        return Objects.equals(status, PROCESSED.status);
    }

    public static boolean isFailed(Integer status) {
        return Objects.equals(status, FAILED.status);
    }
}
