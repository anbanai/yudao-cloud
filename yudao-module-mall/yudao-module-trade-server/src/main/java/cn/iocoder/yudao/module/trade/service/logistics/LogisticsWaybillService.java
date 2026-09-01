package cn.iocoder.yudao.module.trade.service.logistics;

import cn.iocoder.yudao.module.trade.controller.admin.logistics.vo.LogisticsPendingOrderRespVO;
import cn.iocoder.yudao.module.trade.controller.admin.logistics.vo.LogisticsWaybillCreateReqVO;
import cn.iocoder.yudao.module.trade.controller.admin.logistics.vo.LogisticsWaybillRespVO;
import cn.iocoder.yudao.module.trade.controller.admin.logistics.vo.LogisticsPrintTaskRespVO;
import cn.iocoder.yudao.module.trade.controller.admin.logistics.vo.LogisticsTraceRespVO;

import java.util.List;

public interface LogisticsWaybillService {
    LogisticsWaybillRespVO createWaybill(LogisticsWaybillCreateReqVO request);
    LogisticsWaybillRespVO getWaybill(Long id);
    List<LogisticsWaybillRespVO> getWaybills();
    List<LogisticsPendingOrderRespVO> getPendingOrders();
    List<LogisticsPrintTaskRespVO> getPrintTasks();
    LogisticsWaybillRespVO reprint(Long id, Long deviceId);
    void cancelWaybill(Long id);
    List<LogisticsTraceRespVO> syncTrace(Long id);
    List<LogisticsTraceRespVO> getTrace(Long id);
    void validateManualDelivery(Long orderId, Long logisticsId, String logisticsNo);
}
