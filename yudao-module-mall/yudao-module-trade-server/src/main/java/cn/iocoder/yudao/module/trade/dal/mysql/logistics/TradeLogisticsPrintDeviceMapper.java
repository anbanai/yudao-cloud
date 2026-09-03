package cn.iocoder.yudao.module.trade.dal.mysql.logistics;

import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.yudao.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.iocoder.yudao.framework.tenant.core.aop.TenantIgnore;
import cn.iocoder.yudao.module.trade.dal.dataobject.logistics.TradeLogisticsPrintDeviceDO;
import org.apache.ibatis.annotations.Mapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;

import java.util.List;

@Mapper
public interface TradeLogisticsPrintDeviceMapper extends BaseMapperX<TradeLogisticsPrintDeviceDO> {

    @TenantIgnore
    default TradeLogisticsPrintDeviceDO selectByTokenHashIgnoreTenant(String tokenHash) {
        return selectOne(new LambdaQueryWrapperX<TradeLogisticsPrintDeviceDO>()
                .eq(TradeLogisticsPrintDeviceDO::getTokenHash, tokenHash)
                .last("LIMIT 1"));
    }

    default TradeLogisticsPrintDeviceDO selectDefaultEnabled() {
        return selectOne(new LambdaQueryWrapperX<TradeLogisticsPrintDeviceDO>()
                .eq(TradeLogisticsPrintDeviceDO::getStatus, 0)
                .eq(TradeLogisticsPrintDeviceDO::getDefaultFlag, true)
                .last("LIMIT 1"));
    }

    default List<TradeLogisticsPrintDeviceDO> selectListAll() {
        return selectList(new LambdaQueryWrapperX<TradeLogisticsPrintDeviceDO>().orderByDesc(TradeLogisticsPrintDeviceDO::getId));
    }

    default TradeLogisticsPrintDeviceDO selectPendingEnrollmentForUpdate() {
        return selectOne(new LambdaQueryWrapperX<TradeLogisticsPrintDeviceDO>()
                .eq(TradeLogisticsPrintDeviceDO::getEnrollmentKey, "ACTIVE")
                .last("LIMIT 1 FOR UPDATE"));
    }

    default void clearDefault(Long exceptId) {
        update(null, new LambdaUpdateWrapper<TradeLogisticsPrintDeviceDO>()
                .ne(exceptId != null, TradeLogisticsPrintDeviceDO::getId, exceptId)
                .set(TradeLogisticsPrintDeviceDO::getDefaultFlag, false));
    }

    @TenantIgnore
    default int bindPendingDevice(Long id, String deviceCode, String deviceName) {
        return update(null, new LambdaUpdateWrapper<TradeLogisticsPrintDeviceDO>()
                .eq(TradeLogisticsPrintDeviceDO::getId, id)
                .eq(TradeLogisticsPrintDeviceDO::getEnrollmentKey, "ACTIVE")
                .set(TradeLogisticsPrintDeviceDO::getDeviceCode, deviceCode)
                .set(TradeLogisticsPrintDeviceDO::getDeviceName, deviceName)
                .set(TradeLogisticsPrintDeviceDO::getEnrollmentKey, null));
    }
}
