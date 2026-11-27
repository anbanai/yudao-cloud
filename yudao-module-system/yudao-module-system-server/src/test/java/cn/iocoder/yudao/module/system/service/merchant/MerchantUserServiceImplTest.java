package cn.iocoder.yudao.module.system.service.merchant;

import cn.iocoder.yudao.framework.common.enums.CommonStatusEnum;
import cn.iocoder.yudao.framework.test.core.ut.BaseDbUnitTest;
import cn.iocoder.yudao.module.system.controller.merchant.auth.vo.MerchantAuthRegisterReqVO;
import cn.iocoder.yudao.module.system.dal.dataobject.user.MerchantUserDO;
import cn.iocoder.yudao.module.system.dal.mysql.user.MerchantUserMapper;
import cn.iocoder.yudao.module.system.enums.ErrorCodeConstants;
import cn.iocoder.yudao.module.system.dal.dataobject.user.AdminUserDO;
import cn.iocoder.yudao.module.system.service.member.MemberService;
import cn.iocoder.yudao.module.system.service.oauth2.OAuth2TokenService;
import cn.iocoder.yudao.module.system.service.user.AdminUserService;
import cn.iocoder.yudao.module.system.service.tenant.TenantService;
import cn.iocoder.yudao.framework.common.enums.UserTypeEnum;
import cn.iocoder.yudao.module.system.controller.admin.merchant.vo.MerchantUserPageReqVO;
import jakarta.annotation.Resource;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.Import;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.util.List;

import static cn.iocoder.yudao.framework.test.core.util.AssertUtils.assertServiceException;
import static cn.iocoder.yudao.framework.test.core.util.RandomUtils.randomLongId;
import static cn.iocoder.yudao.module.system.enums.ErrorCodeConstants.USER_USERNAME_EXISTS;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@Import(MerchantUserServiceImpl.class)
class MerchantUserServiceImplTest extends BaseDbUnitTest {

    @Resource
    private MerchantUserServiceImpl merchantUserService;
    @MockitoBean
    private TenantService tenantService;
    @Resource
    private MerchantUserMapper merchantUserMapper;

    @MockitoBean
    private PasswordEncoder passwordEncoder;
    @MockitoBean
    private AdminUserService adminUserService;
    @MockitoBean
    private MemberService memberService;
    @MockitoBean
    private OAuth2TokenService oauth2TokenService;

    @Test
    void getUserPageFiltersMerchantAccounts() {
        MerchantUserDO expected = new MerchantUserDO().setUsername("seller100").setPassword("hash")
                .setNickname("Tea Seller").setMobile("15601691300").setStatus(CommonStatusEnum.ENABLE.getStatus());
        merchantUserMapper.insert(expected);
        merchantUserMapper.insert(new MerchantUserDO().setUsername("seller200").setPassword("hash")
                .setNickname("Coffee Seller").setMobile("15601691301").setStatus(CommonStatusEnum.DISABLE.getStatus()));
        MerchantUserPageReqVO reqVO = new MerchantUserPageReqVO();
        reqVO.setUsername("seller100");
        reqVO.setStatus(CommonStatusEnum.ENABLE.getStatus());

        var page = merchantUserService.getUserPage(reqVO);

        assertEquals(1, page.getTotal());
        assertEquals(List.of(expected.getId()), page.getList().stream().map(MerchantUserDO::getId).toList());
    }

    @Test
    void updateUserStatusDisablingAccountRevokesItsTokens() {
        MerchantUserDO user = new MerchantUserDO().setUsername("seller100").setPassword("hash")
                .setNickname("Tea Seller").setStatus(CommonStatusEnum.ENABLE.getStatus());
        merchantUserMapper.insert(user);

        merchantUserService.updateUserStatus(user.getId(), CommonStatusEnum.DISABLE.getStatus());

        assertEquals(CommonStatusEnum.DISABLE.getStatus(), merchantUserMapper.selectById(user.getId()).getStatus());
        verify(oauth2TokenService).removeAccessToken(user.getId(), UserTypeEnum.MERCHANT.getValue());
    }

    @Test
    void updateUserPasswordEncodesPasswordAndRevokesItsTokens() {
        MerchantUserDO user = new MerchantUserDO().setUsername("seller100").setPassword("old-hash")
                .setNickname("Tea Seller").setStatus(CommonStatusEnum.ENABLE.getStatus());
        merchantUserMapper.insert(user);
        when(passwordEncoder.encode("newSecret123")).thenReturn("new-hash");

        merchantUserService.updateUserPassword(user.getId(), "newSecret123");

        assertEquals("new-hash", merchantUserMapper.selectById(user.getId()).getPassword());
        verify(oauth2TokenService).removeAccessToken(user.getId(), UserTypeEnum.MERCHANT.getValue());
    }

    @Test
    void registerUserPersistsEnabledAccountWithEncodedPassword() {
        MerchantAuthRegisterReqVO reqVO = new MerchantAuthRegisterReqVO();
        reqVO.setUsername("seller100");
        reqVO.setNickname("Tea Seller");
        reqVO.setPassword("secret123");
        reqVO.setMobile("15601691300");
        when(passwordEncoder.encode(eq("secret123"))).thenReturn("encoded-secret");

        MerchantUserDO created = merchantUserService.registerUser(reqVO);
        MerchantUserDO persisted = merchantUserMapper.selectById(created.getId());

        assertEquals("encoded-secret", persisted.getPassword());
        assertEquals("seller100", persisted.getUsername());
        assertEquals("15601691300", persisted.getMobile());
        assertEquals(CommonStatusEnum.ENABLE.getStatus(), persisted.getStatus());
        assertEquals(null, persisted.getMerchantId());
    }

    @Test
    void registerUserRejectsExistingUsername() {
        merchantUserMapper.insert(new MerchantUserDO().setUsername("seller100").setPassword("hash")
                .setNickname("Existing seller"));
        MerchantAuthRegisterReqVO reqVO = new MerchantAuthRegisterReqVO();
        reqVO.setUsername("seller100");
        reqVO.setNickname("New seller");
        reqVO.setPassword("secret123");
        reqVO.setMobile("15601691300");

        assertServiceException(() -> merchantUserService.registerUser(reqVO), USER_USERNAME_EXISTS);
    }

    @Test
    void registerUserAllowsUsernameAlreadyUsedByAdmin() {
        when(adminUserService.getUserByUsername("seller100"))
                .thenReturn(new AdminUserDO().setId(10L).setUsername("seller100"));
        when(passwordEncoder.encode("secret123")).thenReturn("encoded-secret");
        MerchantAuthRegisterReqVO reqVO = new MerchantAuthRegisterReqVO();
        reqVO.setUsername("seller100");
        reqVO.setNickname("Tea Seller");
        reqVO.setPassword("secret123");
        reqVO.setMobile("15601691300");

        MerchantUserDO created = merchantUserService.registerUser(reqVO);

        assertEquals("seller100", merchantUserMapper.selectById(created.getId()).getUsername());
    }

    @Test
    void registerUserAllowsMemberPhoneAsUsernameAndContactMobile() {
        when(memberService.isMemberMobileExists("15601691300")).thenReturn(true);
        when(passwordEncoder.encode("secret123")).thenReturn("encoded-secret");
        MerchantAuthRegisterReqVO reqVO = new MerchantAuthRegisterReqVO();
        reqVO.setUsername("15601691300");
        reqVO.setNickname("Tea Seller");
        reqVO.setPassword("secret123");
        reqVO.setMobile("15601691300");

        MerchantUserDO created = merchantUserService.registerUser(reqVO);

        MerchantUserDO persisted = merchantUserMapper.selectById(created.getId());
        assertEquals("15601691300", persisted.getUsername());
        assertEquals("15601691300", persisted.getMobile());
    }

    @Test
    void registerUserAllowsAdminPhoneAsUsernameAndContactMobile() {
        when(adminUserService.getUserByMobile("15601691300"))
                .thenReturn(new AdminUserDO().setId(10L).setMobile("15601691300"));
        when(passwordEncoder.encode("secret123")).thenReturn("encoded-secret");
        MerchantAuthRegisterReqVO reqVO = new MerchantAuthRegisterReqVO();
        reqVO.setUsername("15601691300");
        reqVO.setNickname("Tea Seller");
        reqVO.setPassword("secret123");
        reqVO.setMobile("15601691300");

        MerchantUserDO created = merchantUserService.registerUser(reqVO);

        MerchantUserDO persisted = merchantUserMapper.selectById(created.getId());
        assertEquals("15601691300", persisted.getUsername());
        assertEquals("15601691300", persisted.getMobile());
    }

    @Test
    void registerUserRejectsMobileAlreadyUsedByMerchant() {
        merchantUserMapper.insert(new MerchantUserDO().setUsername("seller200").setPassword("hash")
                .setNickname("Existing seller").setMobile("15601691300"));
        MerchantAuthRegisterReqVO reqVO = new MerchantAuthRegisterReqVO();
        reqVO.setUsername("seller100");
        reqVO.setNickname("Tea Seller");
        reqVO.setPassword("secret123");
        reqVO.setMobile("15601691300");

        assertServiceException(() -> merchantUserService.registerUser(reqVO),
                ErrorCodeConstants.USER_MOBILE_EXISTS);
    }

    @Test
    void approvedBindingAssignsTenantOnceAndRevokesPriorSessions() {
        MerchantUserDO user = new MerchantUserDO().setUsername("approved-seller").setPassword("hash")
                .setNickname("seller").setMerchantId(10L);
        merchantUserMapper.insert(user);

        merchantUserService.bindApprovedMerchant(user.getId(), 10L, 71L);
        merchantUserService.bindApprovedMerchant(user.getId(), 10L, 71L);

        assertEquals(71L, merchantUserMapper.selectById(user.getId()).getTenantId());
        verify(oauth2TokenService).removeAccessToken(user.getId(), UserTypeEnum.MERCHANT.getValue());
    }

    @Test
    void approvedBindingRejectsDifferentTenantOrMerchant() {
        MerchantUserDO user = new MerchantUserDO().setUsername("bound-seller").setPassword("hash")
                .setNickname("seller").setMerchantId(10L);
        user.setTenantId(71L);
        merchantUserMapper.insert(user);

        assertServiceException(() -> merchantUserService.bindApprovedMerchant(user.getId(), 10L, 72L),
                ErrorCodeConstants.SYSTEM_MERCHANT_USER_ALREADY_BOUND);
        assertServiceException(() -> merchantUserService.bindApprovedMerchant(user.getId(), 11L, 71L),
                ErrorCodeConstants.SYSTEM_MERCHANT_USER_ALREADY_BOUND);
        assertEquals(71L, merchantUserMapper.selectById(user.getId()).getTenantId());
        assertEquals(10L, merchantUserMapper.selectById(user.getId()).getMerchantId());
    }

    @Test
    void approvedBindingRejectsInvalidTenantBeforeChangingAccount() {
        MerchantUserDO user = new MerchantUserDO().setUsername("unassigned").setPassword("hash").setNickname("seller");
        merchantUserMapper.insert(user);
        org.mockito.Mockito.doThrow(cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil
                .exception(ErrorCodeConstants.TENANT_NOT_EXISTS)).when(tenantService).validTenant(71L);

        assertServiceException(() -> merchantUserService.bindApprovedMerchant(user.getId(), 10L, 71L),
                ErrorCodeConstants.TENANT_NOT_EXISTS);
        assertEquals(0L, merchantUserMapper.selectById(user.getId()).getTenantId());
    }

    @Test
    void bindMerchantIdUpdatesUnboundAccountAndRejectsRebinding() {
        MerchantUserDO user = new MerchantUserDO().setUsername("seller100").setPassword("hash")
                .setNickname("Tea Seller");
        merchantUserMapper.insert(user);
        Long merchantId = randomLongId();

        merchantUserService.bindMerchantId(user.getId(), merchantId);

        assertEquals(merchantId, merchantUserMapper.selectById(user.getId()).getMerchantId());
        assertServiceException(() -> merchantUserService.bindMerchantId(user.getId(), randomLongId()),
                ErrorCodeConstants.SYSTEM_MERCHANT_USER_ALREADY_BOUND);
    }

}
