package cn.iocoder.yudao.module.trade.dal.mysql.logistics;

import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.yudao.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.iocoder.yudao.module.trade.dal.dataobject.logistics.TradeWechatLogisticsConfigDO;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface TradeWechatLogisticsConfigMapper extends BaseMapperX<TradeWechatLogisticsConfigDO> {

    default TradeWechatLogisticsConfigDO selectByUserType(Integer userType) {
        return selectOne(new LambdaQueryWrapperX<TradeWechatLogisticsConfigDO>()
                .eq(TradeWechatLogisticsConfigDO::getUserType, userType));
    }
}
