package cn.iocoder.yudao.module.trade.dal.mysql.logistics;

import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.yudao.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.iocoder.yudao.module.trade.dal.dataobject.logistics.TradeMerchantLogisticsConfigDO;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface TradeMerchantLogisticsConfigMapper extends BaseMapperX<TradeMerchantLogisticsConfigDO> {

    default TradeMerchantLogisticsConfigDO selectByMerchantId(Long merchantId) {
        return selectOne(new LambdaQueryWrapperX<TradeMerchantLogisticsConfigDO>()
                .eq(TradeMerchantLogisticsConfigDO::getMerchantId, merchantId));
    }

    default TradeMerchantLogisticsConfigDO selectByMerchantIdForUpdate(Long merchantId) {
        return selectOne(new LambdaQueryWrapperX<TradeMerchantLogisticsConfigDO>()
                .eq(TradeMerchantLogisticsConfigDO::getMerchantId, merchantId)
                .last("FOR UPDATE"));
    }
}
