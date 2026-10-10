package cn.iocoder.yudao.module.pay.controller.admin.fund;

import cn.iocoder.yudao.framework.apilog.core.annotation.ApiAccessLog;
import cn.iocoder.yudao.framework.common.pojo.*;
import cn.iocoder.yudao.module.pay.dal.dataobject.payfund.yeepay.*;
import cn.iocoder.yudao.module.pay.service.payfund.reconciliation.PayReconciliationService;
import jakarta.annotation.Resource;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import lombok.Data;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import java.time.LocalDate;
import static cn.iocoder.yudao.framework.common.pojo.CommonResult.success;
import static cn.iocoder.yudao.framework.security.core.util.SecurityFrameworkUtils.getLoginUserId;

@RestController
@RequestMapping("/pay/reconciliation")
@Validated
public class PayReconciliationController {
    @Resource private PayReconciliationService service;
    @GetMapping("/page")
    @PreAuthorize("@ss.hasPermission('pay:fund:query')")
    public CommonResult<PageResult<PayReconciliationRecordDO>> page(@Valid PageParam page,
            @RequestParam(required = false) Integer status, @RequestParam(required = false) LocalDate date) {
        return success(service.page(page, status, date));
    }
    @GetMapping("/get")
    @PreAuthorize("@ss.hasPermission('pay:fund:query')")
    public CommonResult<PayReconciliationRecordDO> get(@RequestParam Long id) { return success(service.get(id)); }
    @GetMapping("/difference-page")
    @PreAuthorize("@ss.hasPermission('pay:fund:query')")
    public CommonResult<PageResult<PayReconciliationDifferenceDO>> differences(@Valid PageParam page,
            @RequestParam Long recordId, @RequestParam(required = false) Integer status) {
        return success(service.differences(page, recordId, status));
    }
    @PostMapping("/resolve")
    @PreAuthorize("@ss.hasPermission('pay:fund:update')")
    @ApiAccessLog(requestEnable = false)
    public CommonResult<Boolean> resolve(@Valid @RequestBody ResolveReqVO request) {
        service.resolve(request.getId(), getLoginUserId(), request.getReason(), request.getExternalEvidence());
        return success(true);
    }
    @PostMapping("/retry")
    @PreAuthorize("@ss.hasPermission('pay:fund:update')")
    @ApiAccessLog(requestEnable = false)
    public CommonResult<Long> retry(@Valid @RequestBody RetryReqVO request) {
        return success(service.retry(request.getChannelId(), request.getDate(), request.getParentMerchantNo(),
                getLoginUserId(), request.getReason(), request.getExternalEvidence()));
    }
    @Data
    public static class ResolveReqVO {
        @NotNull private Long id;
        @NotBlank @Size(max = 1000) private String reason;
        @NotBlank @Size(max = 1000) private String externalEvidence;
    }
    @Data
    public static class RetryReqVO {
        @NotNull private Long channelId;
        @NotNull private LocalDate date;
        @NotBlank @Size(max = 64) private String parentMerchantNo;
        @NotBlank @Size(max = 1000) private String reason;
        @NotBlank @Size(max = 1000) private String externalEvidence;
    }
}
