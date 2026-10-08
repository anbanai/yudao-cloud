package cn.iocoder.yudao.module.trade.service.aftersale;

import cn.hutool.core.util.StrUtil;
import cn.iocoder.yudao.module.trade.dal.dataobject.aftersale.AfterSaleDO;
import cn.iocoder.yudao.module.trade.enums.aftersale.ReturnShipmentFeePayerEnum;
import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.infra.api.config.ConfigApi;
import com.fasterxml.jackson.databind.JsonNode;
import jakarta.annotation.Resource;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/** Resolves the default payer from configurable after-sale reason mappings. */
@Component
public class ReturnShipmentFeePolicy {

    private static final String CONFIG_KEY = "trade.return-shipment.fee-policy";

    @Resource
    private ConfigApi configApi;

    @Value("${yudao.trade.logistics.return-shipment.merchant-responsibility-keywords:质量,发错,少件}")
    private String merchantResponsibilityKeywords;

    public ReturnShipmentFeePayerEnum resolve(AfterSaleDO afterSale) {
        String reason = StrUtil.nullToEmpty(afterSale.getApplyReason());
        ReturnShipmentFeePayerEnum configured = resolveConfigured(reason);
        if (configured != null) return configured;
        boolean merchantResponsibility = StrUtil.split(merchantResponsibilityKeywords, ',').stream()
                .map(String::trim).filter(StrUtil::isNotBlank).anyMatch(reason::contains);
        return merchantResponsibility ? ReturnShipmentFeePayerEnum.MERCHANT
                : ReturnShipmentFeePayerEnum.BUYER_PAY_ON_DELIVERY;
    }

    private ReturnShipmentFeePayerEnum resolveConfigured(String reason) {
        if (configApi == null) return null;
        try {
            String value = null;
            Long tenantId = TenantContextHolder.getTenantId();
            if (tenantId != null) {
                value = configApi.getConfigValueByKey(CONFIG_KEY + "." + tenantId).getCheckedData();
            }
            if (StrUtil.isBlank(value)) {
                value = configApi.getConfigValueByKey(CONFIG_KEY).getCheckedData();
            }
            if (StrUtil.isBlank(value)) return null;
            JsonNode root = JsonUtils.parseTree(value);
            for (ReturnShipmentFeePayerEnum payer : ReturnShipmentFeePayerEnum.values()) {
                JsonNode reasons = root.get(payer.name());
                if (reasons != null && reasons.isArray()) {
                    for (JsonNode item : reasons) if (reason.contains(item.asText())) return payer;
                }
            }
            String defaultPayer = root.path("defaultPayer").asText(null);
            return StrUtil.isBlank(defaultPayer) ? null : ReturnShipmentFeePayerEnum.valueOf(defaultPayer);
        } catch (Exception ignored) {
            return null;
        }
    }
}
