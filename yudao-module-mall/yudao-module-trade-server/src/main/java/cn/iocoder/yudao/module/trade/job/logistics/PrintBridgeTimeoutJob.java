package cn.iocoder.yudao.module.trade.job.logistics;

import cn.hutool.core.util.StrUtil;
import cn.iocoder.yudao.framework.tenant.core.job.TenantJob;
import cn.iocoder.yudao.module.trade.dal.dataobject.logistics.TradeLogisticsPrintTaskDO;
import cn.iocoder.yudao.module.trade.dal.mysql.logistics.TradeLogisticsPrintTaskMapper;
import cn.iocoder.yudao.module.trade.enums.logistics.LogisticsPrintTaskStatusEnum;
import com.xxl.job.core.handler.annotation.XxlJob;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

@Component
public class PrintBridgeTimeoutJob {
    @Resource private TradeLogisticsPrintTaskMapper taskMapper;

    @XxlJob("printBridgeTimeoutJob")
    @TenantJob
    public String execute(String param) {
        int count = 0;
        for (TradeLogisticsPrintTaskDO task : taskMapper.selectAcceptedExpired(LocalDateTime.now().minusMinutes(5))) {
            taskMapper.updateById(new TradeLogisticsPrintTaskDO().setId(task.getId())
                    .setStatus(LogisticsPrintTaskStatusEnum.UNKNOWN.name())
                    .setLastError("PrintBridge accepted 后 5 分钟未返回结果，禁止自动重打"));
            count++;
        }
        return StrUtil.format("标记 PrintBridge 未知任务 {} 个", count);
    }
}
