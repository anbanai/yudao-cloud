package cn.iocoder.yudao.module.trade.dal.mysql.aftersale;

import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.yudao.module.trade.dal.dataobject.aftersale.AfterSaleReturnShipmentDO;
import cn.iocoder.yudao.framework.tenant.core.aop.TenantIgnore;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import org.apache.ibatis.annotations.Mapper;

import java.util.Collection;
import java.util.List;

@Mapper
public interface AfterSaleReturnShipmentMapper extends BaseMapperX<AfterSaleReturnShipmentDO> {

    @TenantIgnore
    default AfterSaleReturnShipmentDO selectByProviderRefs(String providerOrderNo, String waybillNo, Long tenantId) {
        if ((providerOrderNo == null || providerOrderNo.isBlank())
                && (waybillNo == null || waybillNo.isBlank())) {
            return null;
        }
        LambdaQueryWrapper<AfterSaleReturnShipmentDO> wrapper = new LambdaQueryWrapper<AfterSaleReturnShipmentDO>()
                .eq(tenantId != null, AfterSaleReturnShipmentDO::getTenantId, tenantId);
        boolean hasProviderOrderNo = providerOrderNo != null && !providerOrderNo.isBlank();
        boolean hasWaybillNo = waybillNo != null && !waybillNo.isBlank();
        if (hasProviderOrderNo && hasWaybillNo) {
            wrapper.and(w -> w.eq(AfterSaleReturnShipmentDO::getProviderOrderNo, providerOrderNo)
                    .or().eq(AfterSaleReturnShipmentDO::getWaybillNo, waybillNo));
        } else if (hasProviderOrderNo) {
            wrapper.eq(AfterSaleReturnShipmentDO::getProviderOrderNo, providerOrderNo);
        } else {
            wrapper.eq(AfterSaleReturnShipmentDO::getWaybillNo, waybillNo);
        }
        wrapper.last("LIMIT 1");
        return selectOne(wrapper);
    }

    default List<AfterSaleReturnShipmentDO> selectListByStatuses(Collection<String> statuses) {
        return selectList(new LambdaQueryWrapper<AfterSaleReturnShipmentDO>()
                .in(AfterSaleReturnShipmentDO::getStatus, statuses)
                .orderByAsc(AfterSaleReturnShipmentDO::getId));
    }

    default AfterSaleReturnShipmentDO selectByAfterSaleId(Long afterSaleId) {
        return selectOne(new LambdaQueryWrapper<AfterSaleReturnShipmentDO>()
                .eq(AfterSaleReturnShipmentDO::getAfterSaleId, afterSaleId)
                .orderByDesc(AfterSaleReturnShipmentDO::getId).last("LIMIT 1"));
    }

    default AfterSaleReturnShipmentDO selectByAfterSaleIdAndIdempotencyKey(Long afterSaleId, String key) {
        return selectOne(new LambdaQueryWrapper<AfterSaleReturnShipmentDO>()
                .eq(AfterSaleReturnShipmentDO::getAfterSaleId, afterSaleId)
                .eq(AfterSaleReturnShipmentDO::getIdempotencyKey, key)
                .last("LIMIT 1"));
    }

    default AfterSaleReturnShipmentDO selectByIdForUpdate(Long id) {
        return selectOne(new LambdaQueryWrapper<AfterSaleReturnShipmentDO>()
                .eq(AfterSaleReturnShipmentDO::getId, id).last("FOR UPDATE"));
    }
}
