package cn.iocoder.yudao.module.trade.service.logistics;

import cn.hutool.core.util.StrUtil;
import cn.iocoder.yudao.module.trade.controller.admin.logistics.vo.TradeMerchantLogisticsConfigRespVO;
import cn.iocoder.yudao.module.trade.controller.admin.logistics.vo.TradeMerchantLogisticsConfigSaveReqVO;
import cn.iocoder.yudao.module.trade.controller.merchant.logistics.vo.MerchantSfLogisticsConfigRespVO;
import cn.iocoder.yudao.module.trade.controller.merchant.logistics.vo.MerchantSfLogisticsConfigSaveReqVO;
import cn.iocoder.yudao.module.trade.dal.dataobject.logistics.TradeMerchantLogisticsConfigDO;
import cn.iocoder.yudao.module.trade.dal.mysql.logistics.TradeMerchantLogisticsConfigMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.yudao.module.trade.enums.ErrorCodeConstants.TRADE_MERCHANT_MONTHLY_CARD_NOT_CONFIGURED;

@Service
@RequiredArgsConstructor
public class TradeMerchantLogisticsConfigServiceImpl implements TradeMerchantLogisticsConfigService {

    private final TradeMerchantLogisticsConfigMapper configMapper;

    @Override
    public TradeMerchantLogisticsConfigRespVO get(Long merchantId) {
        return toAdminResp(configMapper.selectByMerchantId(merchantId), merchantId);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long save(TradeMerchantLogisticsConfigSaveReqVO request) {
        TradeMerchantLogisticsConfigDO config = saveInternal(request.getMerchantId(), request.getMonthlyCard());
        return config.getId();
    }

    @Override
    public MerchantSfLogisticsConfigRespVO getForMerchant(Long merchantId) {
        TradeMerchantLogisticsConfigDO config = configMapper.selectByMerchantId(merchantId);
        return new MerchantSfLogisticsConfigRespVO().setMerchantId(merchantId)
                .setMonthlyCardMasked(mask(config == null ? null : config.getMonthlyCard()))
                .setUpdateTime(config == null ? null : config.getUpdateTime());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void saveForMerchant(Long merchantId, MerchantSfLogisticsConfigSaveReqVO request) {
        saveInternal(merchantId, request.getMonthlyCard());
    }

    @Override
    public String getRequiredMonthlyCard(Long merchantId) {
        if (merchantId == null) {
            throw exception(TRADE_MERCHANT_MONTHLY_CARD_NOT_CONFIGURED, "unknown");
        }
        TradeMerchantLogisticsConfigDO config = configMapper.selectByMerchantId(merchantId);
        if (config == null || StrUtil.isBlank(config.getMonthlyCard())) {
            throw exception(TRADE_MERCHANT_MONTHLY_CARD_NOT_CONFIGURED, merchantId);
        }
        return config.getMonthlyCard();
    }

    private TradeMerchantLogisticsConfigDO saveInternal(Long merchantId, String monthlyCard) {
        TradeMerchantLogisticsConfigDO config = configMapper.selectByMerchantIdForUpdate(merchantId);
        if (config == null) {
            config = new TradeMerchantLogisticsConfigDO().setMerchantId(merchantId).setMonthlyCard(monthlyCard);
            configMapper.insert(config);
        } else {
            config.setMonthlyCard(monthlyCard);
            configMapper.updateById(config);
        }
        return config;
    }

    private TradeMerchantLogisticsConfigRespVO toAdminResp(TradeMerchantLogisticsConfigDO config, Long merchantId) {
        return new TradeMerchantLogisticsConfigRespVO().setId(config == null ? null : config.getId())
                .setMerchantId(merchantId)
                .setMonthlyCardMasked(mask(config == null ? null : config.getMonthlyCard()))
                .setUpdateTime(config == null ? null : config.getUpdateTime());
    }

    private String mask(String value) {
        if (StrUtil.isBlank(value)) return null;
        return value.length() <= 4 ? "****" : "****" + StrUtil.subSuf(value, value.length() - 4);
    }
}
