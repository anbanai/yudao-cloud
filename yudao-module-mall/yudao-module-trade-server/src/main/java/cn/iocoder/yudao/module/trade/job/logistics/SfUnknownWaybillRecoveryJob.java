package cn.iocoder.yudao.module.trade.job.logistics;

import cn.hutool.core.util.StrUtil;
import cn.iocoder.yudao.framework.tenant.core.job.TenantJob;
import cn.iocoder.yudao.module.trade.controller.admin.logistics.vo.LogisticsWaybillCreateReqVO;
import cn.iocoder.yudao.module.trade.dal.dataobject.logistics.TradeLogisticsWaybillDO;
import cn.iocoder.yudao.module.trade.dal.mysql.logistics.TradeLogisticsWaybillMapper;
import cn.iocoder.yudao.module.trade.enums.logistics.LogisticsWaybillStatusEnum;
import cn.iocoder.yudao.module.trade.service.logistics.LogisticsWaybillService;
import com.xxl.job.core.handler.annotation.XxlJob;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;

@Slf4j
@Component
public class SfUnknownWaybillRecoveryJob {
    @Resource private TradeLogisticsWaybillMapper waybillMapper;
    @Resource private LogisticsWaybillService waybillService;

    @XxlJob("sfUnknownWaybillRecoveryJob")
    @TenantJob
    public String execute(String param) {
        int count = 0;
        List<String> statuses = List.of(LogisticsWaybillStatusEnum.CREATING.name(),
                LogisticsWaybillStatusEnum.UNKNOWN.name(), LogisticsWaybillStatusEnum.CANCELLING.name(),
                LogisticsWaybillStatusEnum.CANCEL_UNKNOWN.name());
        for (TradeLogisticsWaybillDO waybill : waybillMapper.selectListByStatuses(statuses)) {
            try {
                if (List.of(LogisticsWaybillStatusEnum.CANCELLING.name(),
                        LogisticsWaybillStatusEnum.CANCEL_UNKNOWN.name()).contains(waybill.getStatus())) {
                    waybillService.cancelWaybill(waybill.getId());
                } else {
                    waybillService.createWaybill(new LogisticsWaybillCreateReqVO().setOrderId(waybill.getOrderId())
                            .setAccountId(waybill.getAccountId()).setDeviceId(waybill.getRequestedDeviceId()));
                }
                count++;
            } catch (Exception exception) {
                log.warn("[execute][恢复顺丰未知运单失败，waybillId={}]", waybill.getId(), exception);
            }
        }
        return StrUtil.format("恢复顺丰未知运单 {} 个", count);
    }
}
