package cn.iocoder.yudao.module.system.controller.admin.merchant;

import cn.iocoder.yudao.framework.common.enums.UserTypeEnum;
import cn.iocoder.yudao.framework.common.pojo.CommonResult;
import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.framework.common.util.object.BeanUtils;
import cn.iocoder.yudao.framework.security.core.LoginUser;
import cn.iocoder.yudao.framework.security.core.util.SecurityFrameworkUtils;
import cn.iocoder.yudao.module.system.controller.admin.merchant.vo.MerchantUserPageReqVO;
import cn.iocoder.yudao.module.system.controller.admin.merchant.vo.MerchantUserRespVO;
import cn.iocoder.yudao.module.system.controller.admin.merchant.vo.MerchantUserUpdatePasswordReqVO;
import cn.iocoder.yudao.module.system.controller.admin.merchant.vo.MerchantUserUpdateStatusReqVO;
import cn.iocoder.yudao.module.system.dal.dataobject.user.MerchantUserDO;
import cn.iocoder.yudao.module.system.service.merchant.MerchantUserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Resource;
import jakarta.validation.Valid;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import static cn.iocoder.yudao.framework.common.pojo.CommonResult.success;

@Tag(name = "管理后台 - 商家账号")
@RestController
@RequestMapping("/system/merchant-user")
@Validated
public class MerchantUserController {

    @Resource
    private MerchantUserService merchantUserService;

    @GetMapping("/page")
    @Operation(summary = "获得商家账号分页")
    @PreAuthorize("@ss.hasPermission('system:merchant-user:query')")
    public CommonResult<PageResult<MerchantUserRespVO>> getMerchantUserPage(@Valid MerchantUserPageReqVO reqVO) {
        validateAdminUserType();
        PageResult<MerchantUserDO> pageResult = merchantUserService.getUserPage(reqVO);
        return success(BeanUtils.toBean(pageResult, MerchantUserRespVO.class));
    }

    @PutMapping("/update-status")
    @Operation(summary = "启用或禁用商家账号")
    @PreAuthorize("@ss.hasPermission('system:merchant-user:update')")
    public CommonResult<Boolean> updateMerchantUserStatus(@Valid @RequestBody MerchantUserUpdateStatusReqVO reqVO) {
        validateAdminUserType();
        merchantUserService.updateUserStatus(reqVO.getId(), reqVO.getStatus());
        return success(true);
    }

    @PutMapping("/update-password")
    @Operation(summary = "重置商家账号密码")
    @PreAuthorize("@ss.hasPermission('system:merchant-user:update-password')")
    public CommonResult<Boolean> updateMerchantUserPassword(@Valid @RequestBody MerchantUserUpdatePasswordReqVO reqVO) {
        validateAdminUserType();
        merchantUserService.updateUserPassword(reqVO.getId(), reqVO.getPassword());
        return success(true);
    }

    private void validateAdminUserType() {
        LoginUser loginUser = SecurityFrameworkUtils.getLoginUser();
        if (loginUser == null || !UserTypeEnum.ADMIN.getValue().equals(loginUser.getUserType())) {
            throw new AccessDeniedException("当前用户不是管理员");
        }
    }

}
