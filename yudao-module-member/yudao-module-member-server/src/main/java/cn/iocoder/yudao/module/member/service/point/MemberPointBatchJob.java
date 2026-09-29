package cn.iocoder.yudao.module.member.service.point;

import cn.iocoder.yudao.framework.tenant.core.job.TenantJob;
import com.xxl.job.core.handler.annotation.XxlJob;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Component;

@Component
public class MemberPointBatchJob {
    @Resource
    private MemberPointBatchService batchService;

    @XxlJob("memberPointBatchExpireJob")
    @TenantJob
    public String expire() {
        batchService.expire();
        return "会员积分过期任务执行完成";
    }
}
