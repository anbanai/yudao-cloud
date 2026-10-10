package cn.iocoder.yudao.module.pay.enums.fund;

import lombok.AllArgsConstructor;
import lombok.Getter;

import java.util.Objects;

/**
 * 易宝商户入网状态。
 *
 * <p>易宝返回的是业务状态字符串，支付库使用稳定的整数值保存，避免上游状态文案变化影响业务判断。</p>
 */
@Getter
@AllArgsConstructor
public enum PayYeepayOnboardingStatusEnum {

    REVIEWING(10, "审核中"),
    REVIEW_BACK(20, "审核驳回"),
    AUTHENTICITY_VERIFYING(30, "实名核验中"),
    AGREEMENT_SIGNING(40, "协议签署中"),
    BUSINESS_OPENING(50, "业务开通中"),
    COMPLETED(100, "已完成"),
    FAILED(110, "失败");

    private final Integer status;
    private final String name;

    public static boolean isCompleted(Integer status) {
        return Objects.equals(status, COMPLETED.status);
    }

    public static PayYeepayOnboardingStatusEnum from(String value) {
        if (value == null) {
            return null;
        }
        String normalized = value.trim().toUpperCase();
        return switch (normalized) {
            case "REVIEWING", "AUDITING", "PENDING", "10" -> REVIEWING;
            case "REVIEW_BACK", "REJECTED", "BACK", "20" -> REVIEW_BACK;
            case "AUTHENTICITY_VERIFYING", "AUTHENTICITY", "30" -> AUTHENTICITY_VERIFYING;
            case "AGREEMENT_SIGNING", "SIGNING", "40" -> AGREEMENT_SIGNING;
            case "BUSINESS_OPENING", "OPENING", "50" -> BUSINESS_OPENING;
            case "COMPLETED", "SUCCESS", "FINISHED", "100", "200" -> COMPLETED;
            case "FAILED", "FAIL", "110" -> FAILED;
            default -> null;
        };
    }
}
