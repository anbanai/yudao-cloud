package cn.iocoder.yudao.module.trade.service.logistics;

import cn.iocoder.yudao.module.trade.controller.internal.logistics.vo.PrintBridgeStatusReqVO;
import cn.iocoder.yudao.module.trade.controller.internal.logistics.vo.PrintBridgeTaskRespVO;
import cn.iocoder.yudao.module.trade.dal.dataobject.logistics.TradeLogisticsPrintDeviceDO;

public interface LogisticsPrintBridgeService {

    TradeLogisticsPrintDeviceDO authenticate(String token, String deviceCode);

    PrintBridgeTaskRespVO pull(TradeLogisticsPrintDeviceDO device, String deviceName);

    void report(TradeLogisticsPrintDeviceDO device, PrintBridgeStatusReqVO request);

    int compensateDeliveredOrders();
}
