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

    default TradeLogisticsPrintDeviceDO selectByIdAndMerchantId(Long id, Long merchantId) {
        return selectOne(TradeLogisticsPrintDeviceDO::getId, id, TradeLogisticsPrintDeviceDO::getMerchantId, merchantId);
    }

    default List<TradeLogisticsPrintDeviceDO> selectListByMerchantId(Long merchantId) {
        return selectList(new LambdaQueryWrapperX<TradeLogisticsPrintDeviceDO>()
                .eq(TradeLogisticsPrintDeviceDO::getMerchantId, merchantId)
                .orderByDesc(TradeLogisticsPrintDeviceDO::getId));
    }

    default TradeLogisticsPrintDeviceDO selectDefaultEnabled(Long merchantId) {
        return selectOne(new LambdaQueryWrapperX<TradeLogisticsPrintDeviceDO>()
                .eq(TradeLogisticsPrintDeviceDO::getMerchantId, merchantId)
                .eq(TradeLogisticsPrintDeviceDO::getStatus, 0)
                .eq(TradeLogisticsPrintDeviceDO::getDefaultFlag, true)
                .last("LIMIT 1"));
    }

    default List<TradeLogisticsPrintDeviceDO> selectListAll() {
        return selectList(new LambdaQueryWrapperX<TradeLogisticsPrintDeviceDO>().orderByDesc(TradeLogisticsPrintDeviceDO::getId));
    }

    default TradeLogisticsPrintDeviceDO selectPendingEnrollmentForUpdate(Long merchantId) {
        return selectOne(new LambdaQueryWrapperX<TradeLogisticsPrintDeviceDO>()
                .eq(TradeLogisticsPrintDeviceDO::getMerchantId, merchantId)
                .eq(TradeLogisticsPrintDeviceDO::getEnrollmentKey, "ACTIVE")
                .last("LIMIT 1 FOR UPDATE"));
    }

    default void updateSettings(TradeLogisticsPrintDeviceDO device) {
        update(null, new LambdaUpdateWrapper<TradeLogisticsPrintDeviceDO>()
                .eq(TradeLogisticsPrintDeviceDO::getId, device.getId())
                .eq(TradeLogisticsPrintDeviceDO::getMerchantId, device.getMerchantId())
                .set(TradeLogisticsPrintDeviceDO::getPrinterName, device.getPrinterName())
                .set(TradeLogisticsPrintDeviceDO::getStatus, device.getStatus())
                .set(TradeLogisticsPrintDeviceDO::getDefaultFlag, device.getDefaultFlag()));
    }

    default void clearDefault(Long merchantId, Long exceptId) {
        update(null, new LambdaUpdateWrapper<TradeLogisticsPrintDeviceDO>()
                .eq(TradeLogisticsPrintDeviceDO::getMerchantId, merchantId)
                .ne(exceptId != null, TradeLogisticsPrintDeviceDO::getId, exceptId)
                .set(TradeLogisticsPrintDeviceDO::getDefaultFlag, false));
    }

    @TenantIgnore
    default int bindPendingDevice(Long id, String tokenHash, String deviceCode, String deviceName) {
        return update(null, new LambdaUpdateWrapper<TradeLogisticsPrintDeviceDO>()
                .eq(TradeLogisticsPrintDeviceDO::getId, id)
                .eq(TradeLogisticsPrintDeviceDO::getTokenHash, tokenHash)
                .eq(TradeLogisticsPrintDeviceDO::getStatus, 0)
                .eq(TradeLogisticsPrintDeviceDO::getEnrollmentKey, "ACTIVE")
                .set(TradeLogisticsPrintDeviceDO::getDeviceCode, deviceCode)
                .set(TradeLogisticsPrintDeviceDO::getDeviceName, deviceName)
                .set(TradeLogisticsPrintDeviceDO::getEnrollmentKey, null));
    }
}
