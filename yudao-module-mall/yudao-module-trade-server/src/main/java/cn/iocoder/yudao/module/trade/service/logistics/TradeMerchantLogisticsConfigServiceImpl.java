package cn.iocoder.yudao.module.trade.service.logistics;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.bean.copier.CopyOptions;
import cn.hutool.core.util.StrUtil;
import cn.iocoder.yudao.framework.common.util.object.BeanUtils;
import cn.iocoder.yudao.module.trade.controller.admin.logistics.vo.TradeMerchantLogisticsConfigRespVO;
import cn.iocoder.yudao.module.trade.controller.merchant.logistics.vo.MerchantSfLogisticsConfigRespVO;
import cn.iocoder.yudao.module.trade.controller.merchant.logistics.vo.MerchantSfLogisticsConfigSaveReqVO;
import cn.iocoder.yudao.module.trade.dal.dataobject.logistics.TradeMerchantLogisticsConfigDO;
import cn.iocoder.yudao.module.trade.dal.mysql.logistics.TradeLogisticsAccountMapper;
import cn.iocoder.yudao.module.trade.dal.mysql.logistics.TradeMerchantLogisticsConfigMapper;
import cn.iocoder.yudao.module.trade.enums.logistics.SfLabelSpec;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.yudao.module.trade.enums.ErrorCodeConstants.TRADE_MERCHANT_MONTHLY_CARD_NOT_CONFIGURED;
import static cn.iocoder.yudao.module.trade.enums.ErrorCodeConstants.TRADE_MERCHANT_SF_CONFIG_INVALID;

@Service
@RequiredArgsConstructor
public class TradeMerchantLogisticsConfigServiceImpl implements TradeMerchantLogisticsConfigService {

    private final TradeMerchantLogisticsConfigMapper configMapper;
    private final TradeLogisticsAccountMapper accountMapper;
    private final SfLabelTemplateResolver templates;

    @Override
    public TradeMerchantLogisticsConfigRespVO get(Long merchantId) {
        TradeMerchantLogisticsConfigDO config = forDisplay(merchantId);
        return BeanUtils.toBean(config, TradeMerchantLogisticsConfigRespVO.class)
                .setMonthlyCardMasked(mask(config.getMonthlyCard())).setConfigured(configured(config));
    }

    @Override
    public MerchantSfLogisticsConfigRespVO getForMerchant(Long merchantId) {
        TradeMerchantLogisticsConfigDO config = forDisplay(merchantId);
        return BeanUtils.toBean(config, MerchantSfLogisticsConfigRespVO.class)
                .setReturnSameAsSender(!Boolean.FALSE.equals(config.getReturnSameAsSender()))
                .setMonthlyCardMasked(mask(config.getMonthlyCard())).setConfigured(configured(config))
                .setSupportedPaperSpecs(templates.supportedPaperSpecs(accountMapper.selectDefaultEnabled()));
    }

    private TradeMerchantLogisticsConfigDO forDisplay(Long merchantId) {
        TradeMerchantLogisticsConfigDO config = configMapper.selectByMerchantId(merchantId);
        return config == null ? new TradeMerchantLogisticsConfigDO().setMerchantId(merchantId) : config;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void saveForMerchant(Long merchantId, MerchantSfLogisticsConfigSaveReqVO request) {
        TradeMerchantLogisticsConfigDO config = configMapper.selectByMerchantIdForUpdate(merchantId);
        if (config == null) {
            config = new TradeMerchantLogisticsConfigDO().setMerchantId(merchantId).setReturnSameAsSender(true);
        }
        BeanUtil.copyProperties(request, config, CopyOptions.create().setIgnoreNullValue(true)
                .setIgnoreProperties("monthlyCard"));
        if (StrUtil.isNotBlank(request.getMonthlyCard())) {
            config.setMonthlyCard(request.getMonthlyCard().trim());
        }
        if (StrUtil.isBlank(config.getMonthlyCard())) {
            throw exception(TRADE_MERCHANT_MONTHLY_CARD_NOT_CONFIGURED, merchantId);
        }
        if (request.getPaperWidthMm() != null || request.getPaperHeightMm() != null || request.getDpi() != null) {
            templates.requireTemplate(accountMapper.selectDefaultEnabled(),
                    config.getPaperWidthMm(), config.getPaperHeightMm(), config.getDpi());
        }
        if (Boolean.FALSE.equals(config.getReturnSameAsSender()) && !hasReturnAddress(config)) {
            throw exception(TRADE_MERCHANT_SF_CONFIG_INVALID, "请补全独立退货地址、收件人和手机号");
        }
        if (config.getId() == null) {
            configMapper.insert(config);
        } else {
            configMapper.updateById(config);
        }
    }

    @Override
    public TradeMerchantLogisticsConfigDO getRequiredShippingConfig(Long merchantId) {
        TradeMerchantLogisticsConfigDO config = requireBilledConfig(merchantId);
        requireProduct(config);
        requireWeight(config);
        if (!hasSender(config)) {
            throw exception(TRADE_MERCHANT_SF_CONFIG_INVALID, "请补全发件人、手机号和发件地址");
        }
        if (!SfLabelSpec.supports(config.getPaperWidthMm(), config.getPaperHeightMm(), config.getDpi())) {
            throw exception(TRADE_MERCHANT_SF_CONFIG_INVALID, "请选择面单纸张");
        }
        return config;
    }

    @Override
    public TradeMerchantLogisticsConfigDO getRequiredReturnConfig(Long merchantId) {
        TradeMerchantLogisticsConfigDO config = requireBilledConfig(merchantId);
        requireProduct(config);
        requireWeight(config);
        if (!hasReturnAddress(config)) {
            throw exception(TRADE_MERCHANT_SF_CONFIG_INVALID, "请补全退货收件人、手机号和地址");
        }
        return config;
    }

    private TradeMerchantLogisticsConfigDO requireBilledConfig(Long merchantId) {
        TradeMerchantLogisticsConfigDO config = merchantId == null ? null : configMapper.selectByMerchantId(merchantId);
        if (config == null || StrUtil.isBlank(config.getMonthlyCard())) {
            throw exception(TRADE_MERCHANT_MONTHLY_CARD_NOT_CONFIGURED, merchantId);
        }
        return config;
    }

    private static void requireProduct(TradeMerchantLogisticsConfigDO config) {
        if (!validProduct(config)) {
            throw exception(TRADE_MERCHANT_SF_CONFIG_INVALID, "请选择默认寄件产品");
        }
    }

    private static void requireWeight(TradeMerchantLogisticsConfigDO config) {
        if (!validWeight(config)) {
            throw exception(TRADE_MERCHANT_SF_CONFIG_INVALID, "请设置默认包裹重量");
        }
    }

    private static boolean validProduct(TradeMerchantLogisticsConfigDO config) {
        return "1".equals(config.getServiceCode()) || "2".equals(config.getServiceCode());
    }

    private static boolean validWeight(TradeMerchantLogisticsConfigDO config) {
        return config.getDefaultWeightKg() != null && config.getDefaultWeightKg().signum() > 0;
    }

    private static boolean configured(TradeMerchantLogisticsConfigDO config) {
        return StrUtil.isNotBlank(config.getMonthlyCard()) && validProduct(config) && validWeight(config)
                && hasSender(config) && hasReturnAddress(config)
                && SfLabelSpec.supports(config.getPaperWidthMm(), config.getPaperHeightMm(), config.getDpi());
    }

    private static boolean hasSender(TradeMerchantLogisticsConfigDO config) {
        return StrUtil.isAllNotBlank(config.getSenderName(), config.getSenderPhone(), config.getSenderProvince(),
                config.getSenderCity(), config.getSenderAddress());
    }

    private static boolean hasReturnAddress(TradeMerchantLogisticsConfigDO config) {
        if (!Boolean.FALSE.equals(config.getReturnSameAsSender())) {
            return hasSender(config);
        }
        return StrUtil.isAllNotBlank(config.getReturnName(), config.getReturnPhone(), config.getReturnProvince(),
                config.getReturnCity(), config.getReturnAddress());
    }

    private static String mask(String value) {
        if (StrUtil.isBlank(value)) return null;
        return value.length() <= 4 ? "****" : "****" + StrUtil.subSuf(value, value.length() - 4);
    }
}
