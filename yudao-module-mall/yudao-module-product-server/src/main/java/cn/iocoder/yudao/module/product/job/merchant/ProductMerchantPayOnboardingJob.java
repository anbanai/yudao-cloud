package cn.iocoder.yudao.module.product.job.merchant;

import cn.iocoder.yudao.framework.tenant.core.util.TenantUtils;
import cn.iocoder.yudao.module.product.service.merchant.ProductMerchantPayOnboardingService;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** 自动恢复各租户的待发送/待轮询入网任务，不依赖审批后的内存回调。 */
@Component
@Slf4j
@ConditionalOnProperty(name = "yudao.product.merchant-pay.scheduler-enabled", havingValue = "true", matchIfMissing = true)
public class ProductMerchantPayOnboardingJob {

    @Resource
    private JdbcTemplate jdbcTemplate;
    @Resource
    private ProductMerchantPayOnboardingService onboardingService;

    @Scheduled(fixedDelayString = "${yudao.product.merchant-pay.scheduler-delay-millis:30000}")
    public void execute() {
        // 只从全局队列读取租户编号，实际任务和 RPC 均在对应租户上下文执行。
        for (Long tenantId : jdbcTemplate.queryForList("SELECT DISTINCT tenant_id FROM product_merchant_pay_onboarding"
                + " WHERE deleted = 0 AND state IN ('READY','RETRY','SUBMITTED','ACTION_REQUIRED')"
                + " AND next_retry_time <= CURRENT_TIMESTAMP", Long.class)) {
            try {
                TenantUtils.execute(tenantId, () -> onboardingService.processPending());
            } catch (RuntimeException ex) {
                // 原始异常可能含渠道资料；仅记租户编号，任务仍在数据库供下轮恢复。
                log.warn("[merchantPayOnboarding][tenant({}) 入网任务处理失败，等待下一轮恢复]", tenantId);
            }
        }
    }
}
