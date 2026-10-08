package cn.iocoder.yudao.module.member.controller.app.identity;

import cn.iocoder.yudao.framework.common.pojo.CommonResult;
import cn.iocoder.yudao.module.member.service.identity.MemberIdentityService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import static cn.iocoder.yudao.framework.common.pojo.CommonResult.success;
import static cn.iocoder.yudao.framework.security.core.util.SecurityFrameworkUtils.getLoginUserId;

@RestController
@RequestMapping("/member/identity")
@RequiredArgsConstructor
public class AppIdentityController {
    private final MemberIdentityService identity;
    public record SharingRequest(@NotNull Boolean enabled, String consentRevision) { }
    @GetMapping("/status")
    @cn.iocoder.yudao.framework.apilog.core.annotation.ApiAccessLog(requestEnable = false, responseEnable = false)
    public CommonResult<MemberIdentityService.Status> status() { return success(identity.status(getLoginUserId())); }
    @PostMapping("/phone-sharing")
    @cn.iocoder.yudao.framework.apilog.core.annotation.ApiAccessLog(requestEnable = false, responseEnable = false)
    public CommonResult<MemberIdentityService.Status> phoneSharing(@RequestBody @Valid SharingRequest request) {
        return success(identity.setPhoneSharing(getLoginUserId(), request.enabled(), request.consentRevision()));
    }
}
