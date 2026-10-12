package cn.iocoder.yudao.module.system.controller.merchant.auth;

import cn.iocoder.yudao.framework.common.enums.UserTypeEnum;
import cn.iocoder.yudao.framework.common.exception.enums.GlobalErrorCodeConstants;
import cn.iocoder.yudao.framework.common.pojo.CommonResult;
import cn.iocoder.yudao.framework.security.config.SecurityProperties;
import cn.iocoder.yudao.framework.security.core.util.SecurityFrameworkUtils;
import cn.iocoder.yudao.framework.tenant.core.aop.TenantIgnore;
import cn.iocoder.yudao.framework.web.core.util.WebFrameworkUtils;
import cn.iocoder.yudao.module.system.controller.merchant.auth.vo.MerchantAuthLoginReqVO;
import cn.iocoder.yudao.module.system.controller.merchant.auth.vo.MerchantAuthLoginRespVO;
import cn.iocoder.yudao.module.system.controller.merchant.auth.vo.MerchantAuthRegisterReqVO;
import cn.iocoder.yudao.module.system.controller.merchant.auth.vo.MerchantUserProfileRespVO;
import cn.iocoder.yudao.module.system.controller.merchant.auth.vo.MerchantUserProfileUpdateReqVO;
import cn.iocoder.yudao.module.system.service.merchant.MerchantAuthService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Resource;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.annotation.security.PermitAll;
import org.springframework.security.access.prepost.PreAuthorize;
import jakarta.validation.Valid;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Objects;

import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception0;
import static cn.iocoder.yudao.framework.common.pojo.CommonResult.success;

@Tag(name = "商家端 - 认证")
@RestController
@RequestMapping("/system/auth")
@Validated
@TenantIgnore
public class MerchantAuthController {

    @Resource
    private MerchantAuthService merchantAuthService;
    @Resource
    private SecurityProperties securityProperties;

    @PostMapping("/register")
    @PermitAll
    @Operation(summary = "商家账号注册")
    public CommonResult<MerchantAuthLoginRespVO> register(@RequestBody @Valid MerchantAuthRegisterReqVO reqVO) {
        return success(merchantAuthService.register(reqVO));
    }

    @PostMapping("/login")
    @PermitAll
    @Operation(summary = "商家账号登录")
    public CommonResult<MerchantAuthLoginRespVO> login(@RequestBody @Valid MerchantAuthLoginReqVO reqVO) {
        return success(merchantAuthService.login(reqVO));
    }

    @PostMapping("/refresh-token")
    @PermitAll
    @Operation(summary = "刷新商家访问令牌")
    public CommonResult<MerchantAuthLoginRespVO> refreshToken(@RequestParam("refreshToken") String refreshToken) {
        return success(merchantAuthService.refreshToken(refreshToken));
    }

    @PostMapping("/logout")
    @Operation(summary = "商家账号登出")
    public CommonResult<Boolean> logout(HttpServletRequest request) {
        String token = SecurityFrameworkUtils.obtainAuthorization(request,
                securityProperties.getTokenHeader(), securityProperties.getTokenParameter());
        if (token != null && !token.isBlank()) {
            merchantAuthService.logout(token);
        }
        return success(true);
    }

    @GetMapping("/profile")
    @Operation(summary = "获取商家账号资料")
    @PreAuthorize("denyAll()")
    public CommonResult<MerchantUserProfileRespVO> getProfile() {
        return success(merchantAuthService.getProfile(getCurrentMerchantUserId()));
    }

    @PutMapping("/profile")
    @Operation(summary = "更新商家账号资料")
    @PreAuthorize("denyAll()")
    public CommonResult<Boolean> updateProfile(@RequestBody @Valid MerchantUserProfileUpdateReqVO reqVO) {
        merchantAuthService.updateProfile(getCurrentMerchantUserId(), reqVO);
        return success(true);
    }

    private Long getCurrentMerchantUserId() {
        if (!Objects.equals(UserTypeEnum.MERCHANT.getValue(), WebFrameworkUtils.getLoginUserType())) {
            throw exception0(GlobalErrorCodeConstants.UNAUTHORIZED.getCode(), "当前用户不是商家账号");
        }
        Long userId = SecurityFrameworkUtils.getLoginUserId();
        if (userId == null) {
            throw exception0(GlobalErrorCodeConstants.UNAUTHORIZED.getCode(), "用户未登录");
        }
        return userId;
    }

}
