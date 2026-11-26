package cn.iocoder.yudao.module.member.service.youzan;

import cn.iocoder.yudao.framework.common.biz.system.oauth2.OAuth2TokenCommonApi;
import cn.iocoder.yudao.framework.common.enums.CommonStatusEnum;
import cn.iocoder.yudao.framework.common.enums.UserTypeEnum;
import cn.iocoder.yudao.framework.common.util.servlet.ServletUtils;
import cn.iocoder.yudao.framework.security.config.SecurityProperties;
import cn.iocoder.yudao.framework.security.core.util.SecurityFrameworkUtils;
import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.member.service.auth.WeixinClaimSession;
import cn.iocoder.yudao.module.member.service.user.MemberUserService;
import cn.iocoder.yudao.module.system.api.social.SocialClientApi;
import cn.iocoder.yudao.module.system.api.social.SocialUserApi;
import cn.iocoder.yudao.module.system.enums.social.SocialTypeEnum;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;

import static cn.iocoder.yudao.framework.common.exception.enums.GlobalErrorCodeConstants.FORBIDDEN;
import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception;

@Service
@RequiredArgsConstructor
public class WeixinClaimIdentityService {
    private static final Integer MEMBER = UserTypeEnum.MEMBER.getValue();
    private final SecurityProperties security;
    private final OAuth2TokenCommonApi tokens;
    private final MemberUserService users;
    private final SocialUserApi bindings;
    private final WeixinClaimSession sessions;
    private final SocialClientApi weixin;

    public String verifiedMobile(String phoneCode) {
        var principal = SecurityFrameworkUtils.getLoginUser();
        var tenant = TenantContextHolder.getTenantId();
        var request = ServletUtils.getRequest();
        if (tenant == null || TenantContextHolder.isIgnore() || request == null || principal == null
                || !MEMBER.equals(principal.getUserType()) || !tenant.equals(principal.getTenantId())
                || principal.getId() == null || !StringUtils.hasText(phoneCode) || phoneCode.length() > 512) {
            throw exception(FORBIDDEN);
        }
        var userId = principal.getId();
        String token = SecurityFrameworkUtils.obtainAuthorization(request, security.getTokenHeader(), security.getTokenParameter());
        if (!StringUtils.hasText(token)) {
            throw exception(FORBIDDEN);
        }
        try {
            var live = tokens.checkAccessToken(token).getCheckedData();
            if (live == null || !MEMBER.equals(live.getUserType()) || !tenant.equals(live.getTenantId())
                    || !userId.equals(live.getUserId()) || live.getExpiresTime() == null
                    || !live.getExpiresTime().isAfter(LocalDateTime.now())) {
                throw exception(FORBIDDEN);
            }
            var member = users.getUser(userId);
            if (member == null || !userId.equals(member.getId()) || !tenant.equals(member.getTenantId())
                    || !CommonStatusEnum.isEnable(member.getStatus())) {
                throw exception(FORBIDDEN);
            }
            var binding = bindings.getSocialUserByUserId(MEMBER, userId, SocialTypeEnum.WECHAT_MINI_PROGRAM.getType()).getCheckedData();
            if (binding == null || !userId.equals(binding.getUserId()) || !sessions.matches(tenant, userId, token, binding.getOpenid())) {
                throw exception(FORBIDDEN);
            }
            var phone = weixin.exchangeWxMaPhoneNumber(MEMBER, phoneCode).getCheckedData();
            if (phone != null && "86".equals(phone.getCountryCode()) && phone.getPurePhoneNumber() != null
                    && phone.getPurePhoneNumber().matches("1[3-9][0-9]{9}")) {
                return phone.getPurePhoneNumber();
            }
        } catch (RuntimeException ignored) {
            throw exception(FORBIDDEN);
        }
        throw exception(FORBIDDEN);
    }
}
