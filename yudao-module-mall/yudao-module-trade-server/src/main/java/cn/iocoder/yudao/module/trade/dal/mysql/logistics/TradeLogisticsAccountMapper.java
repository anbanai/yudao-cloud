package cn.iocoder.yudao.module.trade.dal.mysql.logistics;

import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.yudao.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.iocoder.yudao.module.trade.dal.dataobject.logistics.TradeLogisticsAccountDO;
import org.apache.ibatis.annotations.Mapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;

import java.util.List;

@Mapper
public interface TradeLogisticsAccountMapper extends BaseMapperX<TradeLogisticsAccountDO> {

    default TradeLogisticsAccountDO selectDefaultEnabled() {
        return selectOne(new LambdaQueryWrapperX<TradeLogisticsAccountDO>()
                .eq(TradeLogisticsAccountDO::getStatus, 0)
                .eq(TradeLogisticsAccountDO::getDefaultFlag, true)
                .last("LIMIT 1"));
    }

    default List<TradeLogisticsAccountDO> selectListAll() {
        return selectList(new LambdaQueryWrapperX<TradeLogisticsAccountDO>().orderByDesc(TradeLogisticsAccountDO::getId));
    }

    default void clearDefault(Long exceptId) {
        update(null, new LambdaUpdateWrapper<TradeLogisticsAccountDO>()
                .ne(exceptId != null, TradeLogisticsAccountDO::getId, exceptId)
                .set(TradeLogisticsAccountDO::getDefaultFlag, false));
    }
}
