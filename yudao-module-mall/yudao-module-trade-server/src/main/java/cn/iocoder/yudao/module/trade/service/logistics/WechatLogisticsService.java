package cn.iocoder.yudao.module.trade.service.logistics;

import cn.iocoder.yudao.module.trade.controller.admin.logistics.vo.WechatLogisticsAccountStatusRespVO;
import cn.iocoder.yudao.module.trade.controller.admin.logistics.vo.WechatLogisticsConfigSaveReqVO;
import cn.iocoder.yudao.module.trade.controller.admin.logistics.vo.WechatLogisticsPrinterBindReqVO;
import cn.iocoder.yudao.module.trade.controller.admin.logistics.vo.WechatLogisticsPrinterRespVO;
import cn.iocoder.yudao.module.trade.controller.admin.logistics.vo.WechatLogisticsTraceRespVO;
import cn.iocoder.yudao.module.trade.controller.admin.logistics.vo.WechatLogisticsWaybillRespVO;
import cn.iocoder.yudao.module.trade.dal.dataobject.logistics.TradeWechatLogisticsConfigDO;

import jakarta.validation.Valid;
import java.util.List;

public interface WechatLogisticsService {

    void saveConfig(@Valid WechatLogisticsConfigSaveReqVO reqVO);

    TradeWechatLogisticsConfigDO getConfig();

    WechatLogisticsAccountStatusRespVO getAccountStatus();

    WechatLogisticsWaybillRespVO createWaybill(Long orderId);

    List<WechatLogisticsWaybillRespVO> batchCreateWaybills(List<Long> orderIds);

    WechatLogisticsWaybillRespVO confirmPrint(Long waybillId);

    void cancelWaybill(Long waybillId);

    WechatLogisticsWaybillRespVO getWaybill(Long waybillId);

    List<WechatLogisticsWaybillRespVO> getPendingWaybills();

    List<WechatLogisticsTraceRespVO> getTrace(Long waybillId);

    void syncTrace(Long waybillId);

    WechatLogisticsPrinterRespVO bindPrinter(@Valid WechatLogisticsPrinterBindReqVO reqVO);

    WechatLogisticsPrinterRespVO getPrinter();
}
