package cn.iocoder.yudao.module.trade.job.aftersale;

import cn.hutool.core.util.StrUtil;
import cn.iocoder.yudao.framework.tenant.core.job.TenantJob;
import cn.iocoder.yudao.module.trade.dal.dataobject.aftersale.AfterSaleReturnShipmentDO;
import cn.iocoder.yudao.module.trade.dal.mysql.aftersale.AfterSaleReturnShipmentMapper;
import cn.iocoder.yudao.module.trade.enums.aftersale.ReturnShipmentStatusEnum;
import cn.iocoder.yudao.module.trade.service.aftersale.AfterSaleReturnShipmentService;
import com.xxl.job.core.handler.annotation.XxlJob;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;

/** Reconciles create requests whose provider response was not received. */
@Slf4j
@Component
public class ReturnShipmentUnknownRecoveryJob {

    @Resource
    private AfterSaleReturnShipmentMapper shipmentMapper;
    @Resource
    private AfterSaleReturnShipmentService shipmentService;

    @XxlJob("returnShipmentUnknownRecoveryJob")
    @TenantJob
    public String execute(String param) {
        int count = 0;
        List<AfterSaleReturnShipmentDO> shipments = shipmentMapper
                .selectListByStatuses(List.of(ReturnShipmentStatusEnum.UNKNOWN.name()));
        for (AfterSaleReturnShipmentDO shipment : shipments) {
            try {
                shipmentService.recoverUnknown(shipment.getId());
                count++;
            } catch (Exception exception) {
                log.warn("[execute][恢复售后逆向物流未知订单失败，shipmentId={}]", shipment.getId(), exception);
            }
        }
        return StrUtil.format("恢复售后逆向物流未知订单 {} 个", count);
    }
}
