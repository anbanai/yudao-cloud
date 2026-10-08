package cn.iocoder.yudao.module.trade.service.aftersale;

import cn.iocoder.yudao.module.trade.controller.app.aftersale.vo.AppReturnShipmentCreateReqVO;
import cn.iocoder.yudao.module.trade.dal.dataobject.aftersale.AfterSaleReturnShipmentDO;
import cn.iocoder.yudao.module.trade.dal.dataobject.aftersale.AfterSaleReturnTraceDO;
import cn.iocoder.yudao.module.trade.dal.dataobject.aftersale.AfterSaleDO;

import java.time.LocalDateTime;

import java.util.List;

public interface AfterSaleReturnShipmentService {

    AfterSaleReturnShipmentDO preview(Long userId, Long afterSaleId);

    AfterSaleReturnShipmentDO create(Long userId, Long afterSaleId, AppReturnShipmentCreateReqVO request);

    AfterSaleReturnShipmentDO get(Long userId, Long afterSaleId);

    AfterSaleReturnShipmentDO getAdminShipment(Long afterSaleId);

    List<AfterSaleReturnTraceDO> getTraces(Long userId, Long afterSaleId);

    List<AfterSaleReturnTraceDO> getAdminTraces(Long afterSaleId);

    void cancel(Long userId, Long afterSaleId);

    void cancelAdmin(Long afterSaleId);

    AfterSaleReturnShipmentDO retry(Long afterSaleId);

    /** Query a provider order whose create response was unknown. */
    void recoverUnknown(Long returnShipmentId);

    void manualDelivery(Long afterSaleId, Long logisticsId, String logisticsNo);

    /** Apply buyer-deducted return freight exactly once before refund creation. */
    void applyRefundDeduction(AfterSaleDO afterSale);

    void syncStatus(Long returnShipmentId, String status, String eventId, String description,
                    String location);

    void syncStatus(Long returnShipmentId, String status, String eventId, String description,
                    String location, LocalDateTime occurredTime);

    void syncStatusByProviderRefs(String providerOrderNo, String waybillNo, String status, String eventId,
                                  String description, String location, Long tenantId);
}
