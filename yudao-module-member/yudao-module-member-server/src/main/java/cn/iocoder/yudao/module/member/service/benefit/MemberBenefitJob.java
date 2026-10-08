package cn.iocoder.yudao.module.member.service.benefit;

import cn.iocoder.yudao.framework.tenant.core.job.TenantJob;
import com.xxl.job.core.handler.annotation.XxlJob;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Component;

/** Daily period account materialization and expiry processing. */
@Component
public class MemberBenefitJob {
    @Resource
    private MemberBenefitService benefitService;

    @XxlJob("memberBenefitPeriodJob")
    @TenantJob
    public String processPeriods() {
        benefitService.processPeriods();
        return "会员权益周期任务执行完成";
    }
}
