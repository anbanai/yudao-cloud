package cn.iocoder.yudao.module.trade.service.logistics;

import cn.iocoder.yudao.module.trade.controller.admin.logistics.vo.TradeMerchantLogisticsConfigRespVO;
import cn.iocoder.yudao.module.trade.controller.merchant.logistics.vo.MerchantSfLogisticsConfigRespVO;
import cn.iocoder.yudao.module.trade.controller.merchant.logistics.vo.MerchantSfLogisticsConfigSaveReqVO;
import cn.iocoder.yudao.module.trade.dal.dataobject.logistics.TradeMerchantLogisticsConfigDO;

public interface TradeMerchantLogisticsConfigService {

    TradeMerchantLogisticsConfigRespVO get(Long merchantId);

    MerchantSfLogisticsConfigRespVO getForMerchant(Long merchantId);

    void saveForMerchant(Long merchantId, MerchantSfLogisticsConfigSaveReqVO request);

    TradeMerchantLogisticsConfigDO getRequiredShippingConfig(Long merchantId);

    TradeMerchantLogisticsConfigDO getRequiredReturnConfig(Long merchantId);
}
