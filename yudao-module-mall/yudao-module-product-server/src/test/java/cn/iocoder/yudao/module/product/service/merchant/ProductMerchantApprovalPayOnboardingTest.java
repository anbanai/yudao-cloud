package cn.iocoder.yudao.module.product.service.merchant;

import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.framework.test.core.ut.BaseDbUnitTest;
import cn.iocoder.yudao.module.infra.api.file.FileApi;
import cn.iocoder.yudao.module.pay.api.fund.PayFundApi;
import cn.iocoder.yudao.module.product.dal.dataobject.merchant.ProductMerchantApplicationDO;
import cn.iocoder.yudao.module.product.dal.dataobject.merchant.ProductMerchantApplicationQualificationDO;
import cn.iocoder.yudao.module.product.dal.dataobject.merchant.ProductMerchantDO;
import cn.iocoder.yudao.module.product.dal.dataobject.merchant.ProductMerchantPayOnboardingDO;
import cn.iocoder.yudao.module.product.dal.dataobject.merchant.ProductMerchantQualificationDO;
import cn.iocoder.yudao.module.product.dal.mysql.merchant.ProductMerchantApplicationMapper;
import cn.iocoder.yudao.module.product.dal.mysql.merchant.ProductMerchantApplicationQualificationMapper;
import cn.iocoder.yudao.module.product.dal.mysql.merchant.ProductMerchantMapper;
import cn.iocoder.yudao.module.product.dal.mysql.merchant.ProductMerchantPayOnboardingMapper;
import cn.iocoder.yudao.module.product.dal.mysql.merchant.ProductMerchantQualificationMapper;
import cn.iocoder.yudao.module.product.dal.mysql.merchant.ProductMerchantReviewRecordMapper;
import cn.iocoder.yudao.module.product.enums.merchant.ProductMerchantApplicationStatusEnum;
import cn.iocoder.yudao.module.product.enums.merchant.ProductMerchantStatusEnum;
import cn.iocoder.yudao.module.product.framework.merchant.config.ProductMerchantApplicationConfig;
import cn.iocoder.yudao.module.product.framework.merchant.config.ProductMerchantPayProperties;
import cn.iocoder.yudao.module.system.api.merchant.SystemMerchantUserApi;
import jakarta.annotation.Resource;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.boot.test.mock.mockito.SpyBean;
import org.springframework.cloud.autoconfigure.RefreshAutoConfiguration;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.TestPropertySource;

import java.time.LocalDateTime;
import java.util.List;

import static cn.iocoder.yudao.module.product.enums.ErrorCodeConstants.MERCHANT_APPLICATION_STATUS_ERROR;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

/** 共享工作区申请审批入口的兼容测试；审批、审核记录与入网任务必须同事务提交或回滚。 */
@Import({ProductMerchantApplicationServiceImpl.class, ProductMerchantServiceImpl.class,
        ProductMerchantApplicationConfig.class, RefreshAutoConfiguration.class,
        ProductMerchantPayOnboardingService.class, ProductMerchantPayProperties.class})
@TestPropertySource(properties = {
        "yudao.product.merchant.application.agreement-version=merchant-agreement-2026-10",
        "yudao.product.merchant.application.agreement-url=/merchant-agreement/merchant-agreement-2026-10.pdf",
        "yudao.product.merchant.application.privacy-policy-version=privacy-policy-2026-10",
        "yudao.product.merchant.application.privacy-policy-url=/merchant-agreement/privacy-policy-2026-10.pdf"
})
class ProductMerchantApprovalPayOnboardingTest extends BaseDbUnitTest {

    private static final LocalDateTime PREVIOUS_APPROVAL_TIME = LocalDateTime.of(2026, 10, 1, 12, 0);

    @Resource
    private ProductMerchantApplicationService applicationService;
    @Resource
    private ProductMerchantMapper merchantMapper;
    @Resource
    private ProductMerchantApplicationMapper applicationMapper;
    @Resource
    private ProductMerchantApplicationQualificationMapper applicationQualificationMapper;
    @Resource
    private ProductMerchantQualificationMapper qualificationMapper;
    @Resource
    private ProductMerchantReviewRecordMapper reviewRecordMapper;
    @Resource
    private ProductMerchantPayOnboardingMapper onboardingMapper;
    @SpyBean
    private ProductMerchantPayOnboardingService onboardingService;
    @MockBean
    private PayFundApi payFundApi;
    @MockBean
    private FileApi fileApi;
    @MockBean
    private SystemMerchantUserApi systemMerchantUserApi;

    @BeforeEach
    void setTenant() {
        TenantContextHolder.setTenantId(0L);
        org.mockito.Mockito.when(fileApi.isMerchantQualificationFileOwnedBy(92L, 1001L))
                .thenReturn(cn.iocoder.yudao.framework.common.pojo.CommonResult.success(true));
    }

    @AfterEach
    void clearTenant() {
        TenantContextHolder.clear();
    }

    @ParameterizedTest
    @ValueSource(strings = {"INITIAL", "NEW", "CHANGE"})
    void testApproveApplication_commitsApprovalAndPendingTaskWithoutNetworkRequest(String type) {
        ProductMerchantDO merchant = merchant(type, pendingMerchantStatus(type));
        ProductMerchantApplicationDO application = application(merchant, type,
                ProductMerchantApplicationStatusEnum.PENDING.getStatus());
        ProductMerchantPayOnboardingDO previousTask = previousTask(merchant, type);

        applicationService.approve(application.getId(), 99L);

        ProductMerchantDO approved = merchantMapper.selectById(merchant.getId());
        assertThat(approved.getStatus()).isEqualTo(ProductMerchantStatusEnum.APPROVED.getStatus());
        assertThat(approved.getName()).isEqualTo(application.getMerchantName());
        assertThat(approved.getApprovedRevision()).isEqualTo(application.getVersionNo());
        assertThat(approved.getReviewerId()).isEqualTo(99L);
        assertThat(approved.getReviewTime()).isNotNull();
        ProductMerchantApplicationDO reviewed = applicationMapper.selectById(application.getId());
        assertThat(reviewed.getStatus()).isEqualTo(ProductMerchantApplicationStatusEnum.APPROVED.getStatus());
        assertThat(reviewed.getReviewerId()).isEqualTo(99L);
        assertThat(reviewed.getReviewTime()).isNotNull();
        assertThat(qualificationMapper.selectListByMerchantId(merchant.getId())).singleElement()
                .satisfies(qualification -> {
                    assertThat(qualification.getFileId()).isEqualTo(92L);
                    assertThat(qualification.getCertificateNo()).isEqualTo("新证件编号");
                });
        assertThat(reviewRecordMapper.selectListByMerchantId(merchant.getId())).singleElement()
                .satisfies(record -> {
                    assertThat(record.getAction()).isEqualTo("APPROVE");
                    assertThat(record.getApplicationId()).isEqualTo(application.getId());
                    assertThat(record.getReviewerId()).isEqualTo(99L);
                });
        List<ProductMerchantPayOnboardingDO> tasks = onboardingMapper.selectByMerchantId(merchant.getId(), 0L);
        assertThat(tasks).filteredOn(task -> "WAITING_MATERIALS".equals(task.getState())).singleElement()
                .satisfies(task -> {
                    assertThat(task.getMerchantApprovalTime()).isEqualTo(approved.getReviewTime());
                    assertThat(task.getMaterialsVersion()).isZero();
                    assertThat(task.getAttemptCount()).isZero();
                });
        assertThat(tasks).hasSize(previousTask == null ? 1 : 2);
        if (previousTask != null) {
            assertThat(onboardingMapper.selectById(previousTask.getId()).getState()).isEqualTo("SUPERSEDED");
        }
        verify(onboardingService).enqueueApprovedMerchant(merchant.getId());
        verifyNoInteractions(payFundApi, systemMerchantUserApi);
    }

    @ParameterizedTest
    @ValueSource(strings = {"INITIAL", "CHANGE"})
    void testApproveApplication_rollsBackEveryApprovalWriteWhenEnqueueFails(String type) {
        ProductMerchantDO merchant = merchant(type, pendingMerchantStatus(type));
        ProductMerchantApplicationDO application = application(merchant, type,
                ProductMerchantApplicationStatusEnum.PENDING.getStatus());
        ProductMerchantPayOnboardingDO previousTask = previousTask(merchant, type);
        doAnswer(invocation -> {
            invocation.callRealMethod();
            assertThat(onboardingMapper.selectByMerchantId(merchant.getId(), 0L))
                    .hasSize(previousTask == null ? 1 : 2);
            throw new IllegalStateException("simulated task persistence failure after enqueue");
        }).when(onboardingService).enqueueApprovedMerchant(merchant.getId());

        assertThatThrownBy(() -> applicationService.approve(application.getId(), 99L))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("simulated task persistence failure after enqueue");

        ProductMerchantDO unchanged = merchantMapper.selectById(merchant.getId());
        assertThat(unchanged.getStatus()).isEqualTo(merchant.getStatus());
        assertThat(unchanged.getName()).isEqualTo(merchant.getName());
        assertThat(unchanged.getApprovedRevision()).isEqualTo(merchant.getApprovedRevision());
        assertThat(unchanged.getReviewerId()).isEqualTo(merchant.getReviewerId());
        assertThat(unchanged.getReviewTime()).isEqualTo(merchant.getReviewTime());
        ProductMerchantApplicationDO pending = applicationMapper.selectById(application.getId());
        assertThat(pending.getStatus()).isEqualTo(ProductMerchantApplicationStatusEnum.PENDING.getStatus());
        assertThat(pending.getReviewerId()).isNull();
        assertThat(pending.getReviewTime()).isNull();
        assertThat(qualificationMapper.selectListByMerchantId(merchant.getId())).singleElement()
                .satisfies(qualification -> {
                    assertThat(qualification.getFileId()).isEqualTo(91L);
                    assertThat(qualification.getCertificateNo()).isEqualTo("原证件编号");
                });
        assertThat(reviewRecordMapper.selectListByMerchantId(merchant.getId())).isEmpty();
        List<ProductMerchantPayOnboardingDO> tasks = onboardingMapper.selectByMerchantId(merchant.getId(), 0L);
        assertThat(tasks).hasSize(previousTask == null ? 0 : 1);
        if (previousTask != null) {
            assertThat(tasks.get(0).getId()).isEqualTo(previousTask.getId());
            assertThat(tasks.get(0).getState()).isEqualTo("COMPLETED");
        }
        verify(onboardingService).enqueueApprovedMerchant(merchant.getId());
        verifyNoInteractions(payFundApi, systemMerchantUserApi);
    }

    @ParameterizedTest
    @CsvSource({"0, 10", "10, 0"})
    void testApproveApplication_rejectsInvalidStateWithoutCreatingTask(int merchantStatus, int applicationStatus) {
        ProductMerchantDO merchant = merchant("INITIAL", merchantStatus);
        ProductMerchantApplicationDO application = application(merchant, "INITIAL", applicationStatus);

        assertThatThrownBy(() -> applicationService.approve(application.getId(), 99L))
                .extracting("code").isEqualTo(MERCHANT_APPLICATION_STATUS_ERROR.getCode());

        assertThat(merchantMapper.selectById(merchant.getId()).getStatus()).isEqualTo(merchantStatus);
        assertThat(applicationMapper.selectById(application.getId()).getStatus()).isEqualTo(applicationStatus);
        assertThat(reviewRecordMapper.selectListByMerchantId(merchant.getId())).isEmpty();
        assertThat(onboardingMapper.selectByMerchantId(merchant.getId(), 0L)).isEmpty();
        verify(onboardingService, never()).enqueueApprovedMerchant(merchant.getId());
        verifyNoInteractions(payFundApi, systemMerchantUserApi);
    }

    private Integer pendingMerchantStatus(String type) {
        return "CHANGE".equals(type) ? ProductMerchantStatusEnum.CHANGE_PENDING.getStatus()
                : ProductMerchantStatusEnum.PENDING.getStatus();
    }

    private ProductMerchantDO merchant(String type, Integer status) {
        ProductMerchantDO merchant = new ProductMerchantDO().setOwnerAccountId(1001L)
                .setName("原商家名称").setStoreName("原店铺名称").setStatus(status);
        if ("CHANGE".equals(type)) {
            merchant.setApprovedRevision(1).setReviewerId(88L).setReviewTime(PREVIOUS_APPROVAL_TIME);
        }
        merchantMapper.insert(merchant);
        qualificationMapper.insert(new ProductMerchantQualificationDO().setMerchantId(merchant.getId())
                .setType("BUSINESS_LICENSE").setCertificateNo("原证件编号")
                .setFileId(91L).setFileUrl("https://private.example/previous").setStatus(20));
        return merchant;
    }

    private ProductMerchantApplicationDO application(ProductMerchantDO merchant, String type, Integer status) {
        ProductMerchantApplicationDO application = new ProductMerchantApplicationDO()
                .setMerchantId(merchant.getId()).setOwnerAccountId(merchant.getOwnerAccountId())
                .setVersionNo("INITIAL".equals(type) ? 1 : 2).setType(type).setStatus(status)
                .setBusinessType("RETAIL").setBusinessTypes(List.of("RETAIL"))
                .setMerchantName("审核后商家名称").setStoreName("审核后店铺名称").setCompanyName("测试企业")
                .setCreditCode("91310000MA1FL8X123").setLegalPerson("张三")
                .setContactName("李四").setContactMobile("13800138000");
        applicationMapper.insert(application);
        applicationQualificationMapper.insert(new ProductMerchantApplicationQualificationDO()
                .setApplicationId(application.getId()).setCode("BUSINESS_LICENSE").setName("营业执照")
                .setCertificateNo("新证件编号").setFileId(92L));
        return application;
    }

    private ProductMerchantPayOnboardingDO previousTask(ProductMerchantDO merchant, String type) {
        if (!"CHANGE".equals(type)) {
            return null;
        }
        ProductMerchantPayOnboardingDO task = new ProductMerchantPayOnboardingDO()
                .setMerchantId(merchant.getId()).setMerchantApprovalTime(PREVIOUS_APPROVAL_TIME)
                .setAppKey("mall").setRequestNo("PREVIOUS-" + merchant.getId())
                .setBusinessRole("SHARE_MERCHANT").setState("COMPLETED")
                .setMaterialsVersion(1).setAttemptCount(1).setRequestAccepted(true);
        task.setTenantId(0L);
        onboardingMapper.insert(task);
        return task;
    }
}
