package cn.iocoder.yudao.module.trade.framework.logistics.returnshipment;

import cn.iocoder.yudao.module.trade.dal.dataobject.aftersale.AfterSaleReturnShipmentDO;

import java.util.List;

public interface ReturnShipmentProvider {

    String getCode();

    boolean isAvailable();

    Result create(AfterSaleReturnShipmentDO shipment);

    Result query(AfterSaleReturnShipmentDO shipment);

    void cancel(AfterSaleReturnShipmentDO shipment);

    /** Proactively queries provider traces when callbacks are delayed or unavailable. */
    default List<Trace> queryTraces(AfterSaleReturnShipmentDO shipment) {
        return List.of();
    }

    record Result(String providerOrderNo, String waybillNo, String status, String response,
                  Integer estimatedFee, Integer actualFee) {
        public Result(String providerOrderNo, String waybillNo, String status, String response) {
            this(providerOrderNo, waybillNo, status, response, null, null);
        }
    }

    record Trace(String eventId, String status, String description, String location,
                 java.time.LocalDateTime occurredTime, String rawData) {
    }
}
