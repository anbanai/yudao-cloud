package cn.iocoder.yudao.module.pay.framework.pay.core.client.impl.yeepay;

import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class YeepayFeeGuaranteeTest {

    @Test
    void newPaymentsRemainBlockedUntilFullAmountGuaranteeIsConfirmed() {
        YeepayPayClientConfig config = new YeepayPayClientConfig();
        assertThrows(IllegalStateException.class, config::validateFullAmountDivideGuarantee);
        config.setFullAmountDivideConfirmed(true);
        assertThrows(IllegalStateException.class, config::validateFullAmountDivideGuarantee);
        config.setFeeBearerMode("PLATFORM_FEE_ACCOUNT");
        assertDoesNotThrow(config::validateFullAmountDivideGuarantee);
        assertNull(config.getFeeSubsidyInfo());
    }

    @Test
    void fullSubsidyUsesOfficialPreconfiguredPromotionContract() {
        YeepayPayClientConfig config = new YeepayPayClientConfig()
                .setFullAmountDivideConfirmed(true).setFeeBearerMode("FULL_SUBSIDY_PROMOTION");
        assertThrows(IllegalStateException.class, config::validateFullAmountDivideGuarantee);
        config.setFeeSubsidyPromotionNo("PROMOTION_100_PERCENT");
        assertDoesNotThrow(config::validateFullAmountDivideGuarantee);
        assertEquals(List.of(Map.of("feeSubsidyPromotionNo", "PROMOTION_100_PERCENT")),
                JsonUtils.parseObject(config.getFeeSubsidyInfo(), List.class));
        config.setFeeSubsidyPromotionNo("x".repeat(256));
        assertThrows(IllegalStateException.class, config::validateFullAmountDivideGuarantee);
    }
}
