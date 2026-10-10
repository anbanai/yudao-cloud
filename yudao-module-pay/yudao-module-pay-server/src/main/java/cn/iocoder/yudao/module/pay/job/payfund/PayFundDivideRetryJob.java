package cn.iocoder.yudao.module.pay.job.payfund;

import cn.hutool.core.util.StrUtil;
import cn.iocoder.yudao.framework.tenant.core.job.TenantJob;
import cn.iocoder.yudao.module.pay.service.payfund.PayFundService;
import com.xxl.job.core.handler.annotation.XxlJob;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/** 易宝清算/分账失败的最终一致性补偿任务。 */
@Component
@Slf4j
public class PayFundDivideRetryJob {

    @Resource
    private PayFundService payFundService;

    @XxlJob("payFundDivideRetryJob")
    @TenantJob
    public String execute() {
        int count = payFundService.processPendingDivides();
        int eventCount = payFundService.processFailedYeepayEvents();
        log.info("[execute][易宝分账补偿 ({}) 个，失败事件重放 ({}) 个]", count, eventCount);
        return StrUtil.format("易宝分账补偿 ({}) 个，失败事件重放 ({}) 个", count, eventCount);
    }
}
