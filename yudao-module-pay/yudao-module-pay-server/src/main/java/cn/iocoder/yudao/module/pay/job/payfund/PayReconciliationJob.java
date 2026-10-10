package cn.iocoder.yudao.module.pay.job.payfund;

import cn.iocoder.yudao.framework.tenant.core.job.TenantJob;
import cn.iocoder.yudao.module.pay.service.payfund.reconciliation.PayReconciliationService;
import com.xxl.job.core.handler.annotation.XxlJob;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Component;
import java.time.LocalDate;
import java.time.ZoneId;

/** 每日 09:00 后由 XXL-JOB 配置执行，租户上下文包含已关闭渠道的历史账单。 */
@Component
public class PayReconciliationJob {
    @Resource private PayReconciliationService service;
    @XxlJob("payYeepayReconciliationJob")
    @TenantJob
    public String execute() {
        return "易宝日账单对账批次：" + service.processDaily(LocalDate.now(ZoneId.of("Asia/Shanghai")).minusDays(1));
    }
}
