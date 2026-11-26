package cn.iocoder.yudao.module.member.controller.app.user;

import cn.iocoder.yudao.framework.apilog.core.annotation.ApiAccessLog;
import cn.iocoder.yudao.framework.common.pojo.CommonResult;
import cn.iocoder.yudao.module.member.controller.app.user.vo.AppMemberUserUpdateMobileByWeixinReqVO;
import cn.iocoder.yudao.module.member.service.youzan.WeixinClaimIdentityService;
import cn.iocoder.yudao.module.member.service.youzan.YouzanAssetClaimService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import static cn.iocoder.yudao.framework.common.pojo.CommonResult.success;
import static cn.iocoder.yudao.framework.security.core.util.SecurityFrameworkUtils.getLoginUserId;

@RestController
@RequiredArgsConstructor
public class AppYouzanClaimController {
    private final WeixinClaimIdentityService identity;
    private final YouzanAssetClaimService claims;

    @PostMapping("/member/user/recover-youzan-assets")
    @ApiAccessLog(requestEnable = false, responseEnable = false)
    public CommonResult<YouzanAssetClaimService.Result> recover(@RequestBody AppMemberUserUpdateMobileByWeixinReqVO request) {
        String mobile = identity.verifiedMobile(request.getCode());
        return success(claims.recover(getLoginUserId(), mobile));
    }
}
