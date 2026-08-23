package cn.iocoder.yudao.module.trade.job.logistics;

import cn.hutool.core.util.StrUtil;
import cn.iocoder.yudao.framework.tenant.core.job.TenantJob;
import cn.iocoder.yudao.module.trade.dal.dataobject.logistics.TradeWechatLogisticsWaybillDO;
import cn.iocoder.yudao.module.trade.dal.mysql.logistics.TradeWechatLogisticsWaybillMapper;
import cn.iocoder.yudao.module.trade.service.logistics.WechatLogisticsService;
import com.xxl.job.core.handler.annotation.XxlJob;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Component;

/**
 * 定时同步微信物流助手轨迹。微信 PC 打单软件不提供回执，所以轨迹同步只更新运单信息。
 */
@Component
public class WechatLogisticsTraceSyncJob {

    @Resource
    private TradeWechatLogisticsWaybillMapper waybillMapper;
    @Resource
    private WechatLogisticsService wechatLogisticsService;

    @XxlJob("wechatLogisticsTraceSyncJob")
    @TenantJob
    public String execute(String param) {
        int count = 0;
        for (TradeWechatLogisticsWaybillDO waybill : waybillMapper.selectListForTraceSync()) {
            try {
                wechatLogisticsService.syncTrace(waybill.getId());
                count++;
            } catch (Exception ignored) {
                // 单个运单失败不能阻塞其他租户/订单的轨迹同步。
            }
        }
        return StrUtil.format("同步微信物流轨迹 {} 个", count);
    }
}
