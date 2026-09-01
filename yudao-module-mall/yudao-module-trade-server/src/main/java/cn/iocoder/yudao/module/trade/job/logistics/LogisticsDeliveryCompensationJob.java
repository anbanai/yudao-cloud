package cn.iocoder.yudao.module.trade.job.logistics;

import cn.hutool.core.util.StrUtil;
import cn.iocoder.yudao.framework.tenant.core.job.TenantJob;
import cn.iocoder.yudao.module.trade.service.logistics.LogisticsPrintBridgeService;
import com.xxl.job.core.handler.annotation.XxlJob;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Component;

@Component
public class LogisticsDeliveryCompensationJob {
    @Resource private LogisticsPrintBridgeService printBridgeService;

    @XxlJob("logisticsDeliveryCompensationJob")
    @TenantJob
    public String execute(String param) {
        return StrUtil.format("补偿打印成功订单发货 {} 个", printBridgeService.compensateDeliveredOrders());
    }
}
