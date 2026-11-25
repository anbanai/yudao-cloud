package cn.iocoder.yudao.module.trade.service.logistics;

import cn.iocoder.yudao.module.trade.controller.admin.logistics.vo.TradeMerchantLogisticsConfigRespVO;
import cn.iocoder.yudao.module.trade.controller.admin.logistics.vo.TradeMerchantLogisticsConfigSaveReqVO;
import cn.iocoder.yudao.module.trade.controller.merchant.logistics.vo.MerchantSfLogisticsConfigRespVO;
import cn.iocoder.yudao.module.trade.controller.merchant.logistics.vo.MerchantSfLogisticsConfigSaveReqVO;

public interface TradeMerchantLogisticsConfigService {

    TradeMerchantLogisticsConfigRespVO get(Long merchantId);

    Long save(TradeMerchantLogisticsConfigSaveReqVO request);

    MerchantSfLogisticsConfigRespVO getForMerchant(Long merchantId);

    void saveForMerchant(Long merchantId, MerchantSfLogisticsConfigSaveReqVO request);

    String getRequiredMonthlyCard(Long merchantId);
}
