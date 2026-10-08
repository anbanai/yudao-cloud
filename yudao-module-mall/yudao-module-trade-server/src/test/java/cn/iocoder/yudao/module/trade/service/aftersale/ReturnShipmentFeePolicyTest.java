package cn.iocoder.yudao.module.trade.service.aftersale;

import cn.iocoder.yudao.framework.common.pojo.CommonResult;
import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.infra.api.config.ConfigApi;
import cn.iocoder.yudao.module.trade.dal.dataobject.aftersale.AfterSaleDO;
import cn.iocoder.yudao.module.trade.enums.aftersale.ReturnShipmentFeePayerEnum;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ReturnShipmentFeePolicyTest {

    @Mock
    private ConfigApi configApi;

    @AfterEach
    void clearTenant() {
        TenantContextHolder.clear();
    }

    @Test
    void resolve_prefersTenantRuleAndFallsBackToGlobalRule() {
        ReturnShipmentFeePolicy policy = new ReturnShipmentFeePolicy();
        ReflectionTestUtils.setField(policy, "configApi", configApi);
        TenantContextHolder.setTenantId(9L);
        when(configApi.getConfigValueByKey("trade.return-shipment.fee-policy.9"))
                .thenReturn(CommonResult.success("{\"BUYER_DEDUCT_REFUND\":[\"个人原因\"]}"));

        assertThat(policy.resolve(new AfterSaleDO().setApplyReason("个人原因")))
                .isEqualTo(ReturnShipmentFeePayerEnum.BUYER_DEDUCT_REFUND);
    }

    @Test
    void resolve_usesGlobalRuleWhenTenantRuleIsMissing() {
        ReturnShipmentFeePolicy policy = new ReturnShipmentFeePolicy();
        ReflectionTestUtils.setField(policy, "configApi", configApi);
        TenantContextHolder.setTenantId(9L);
        when(configApi.getConfigValueByKey("trade.return-shipment.fee-policy.9"))
                .thenReturn(CommonResult.success(null));
        when(configApi.getConfigValueByKey("trade.return-shipment.fee-policy"))
                .thenReturn(CommonResult.success("{\"MERCHANT\":[\"质量问题\"]}"));

        assertThat(policy.resolve(new AfterSaleDO().setApplyReason("质量问题")))
                .isEqualTo(ReturnShipmentFeePayerEnum.MERCHANT);
    }
}
