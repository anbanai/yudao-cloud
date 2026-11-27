package cn.iocoder.yudao.module.trade.service.logistics;

import cn.hutool.core.util.StrUtil;
import cn.iocoder.yudao.module.trade.dal.dataobject.logistics.TradeLogisticsAccountDO;
import cn.iocoder.yudao.module.trade.enums.logistics.SfLabelSpec;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.List;

import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.yudao.module.trade.enums.ErrorCodeConstants.LOGISTICS_CONFIGURATION_INVALID;

@Component
public class SfLabelTemplateResolver {

    private final String largeTemplateCode;

    public SfLabelTemplateResolver(@Value("${yudao.trade.logistics.sf.template-100x150-code:}") String largeTemplateCode) {
        this.largeTemplateCode = largeTemplateCode;
    }

    public List<String> supportedPaperSpecs(TradeLogisticsAccountDO account) {
        return Arrays.stream(SfLabelSpec.values())
                .filter(spec -> templateFor(account, spec) != null)
                .map(spec -> spec.getWidthMm() + "x" + spec.getHeightMm()).toList();
    }

    public String requireTemplate(TradeLogisticsAccountDO account, Integer widthMm, Integer heightMm, Integer dpi) {
        if (SfLabelSpec.supports(widthMm, heightMm, dpi)) {
            String code = templateFor(account, SfLabelSpec.of(widthMm, heightMm, dpi));
            if (code != null) {
                return code;
            }
        }
        throw exception(LOGISTICS_CONFIGURATION_INVALID, "当前顺丰开放平台账号未配置所选面单规格的模板");
    }

    private String templateFor(TradeLogisticsAccountDO account, SfLabelSpec spec) {
        if (account == null || StrUtil.isBlank(account.getPartnerId())) {
            return null;
        }
        if (spec == SfLabelSpec.FM_76_130) {
            return "fm_76130_standard_" + account.getPartnerId();
        }
        // 升级前已验证的专属大纸张模板属于原开放平台账号。
        String code = Integer.valueOf(100).equals(account.getPaperWidthMm())
                && Integer.valueOf(150).equals(account.getPaperHeightMm())
                && StrUtil.isNotBlank(account.getTemplateCode()) ? account.getTemplateCode() : largeTemplateCode;
        return code != null && code.matches("[A-Za-z0-9_-]{3,64}") && !StrUtil.startWithIgnoreCase(code, "fm_76130_")
                ? code : null;
    }
}
