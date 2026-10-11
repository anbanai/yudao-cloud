package cn.iocoder.yudao.module.product.service.merchant;

import cn.iocoder.yudao.framework.test.core.ut.BaseDbUnitTest;
import cn.iocoder.yudao.module.product.controller.admin.merchant.vo.ProductMerchantApplicationDraftReqVO;
import cn.iocoder.yudao.module.product.controller.admin.merchant.vo.ProductMerchantApplicationSubmitReqVO;
import cn.iocoder.yudao.module.product.dal.dataobject.merchant.ProductMerchantApplicationDO;
import cn.iocoder.yudao.module.product.dal.dataobject.merchant.ProductMerchantApplicationQualificationDO;
import cn.iocoder.yudao.module.product.dal.dataobject.merchant.ProductMerchantBusinessTypeDO;
import cn.iocoder.yudao.module.product.dal.dataobject.merchant.ProductMerchantDO;
import cn.iocoder.yudao.module.product.dal.dataobject.merchant.ProductMerchantQualificationRequirementDO;
import cn.iocoder.yudao.module.product.dal.mysql.merchant.ProductMerchantApplicationMapper;
import cn.iocoder.yudao.module.product.dal.mysql.merchant.ProductMerchantApplicationQualificationMapper;
import cn.iocoder.yudao.module.product.dal.mysql.merchant.ProductMerchantBusinessTypeMapper;
import cn.iocoder.yudao.module.product.dal.mysql.merchant.ProductMerchantMapper;
import cn.iocoder.yudao.module.product.dal.mysql.merchant.ProductMerchantQualificationRequirementMapper;
import cn.iocoder.yudao.module.product.dal.mysql.merchant.ProductMerchantReviewRecordMapper;
import cn.iocoder.yudao.module.product.enums.merchant.ProductMerchantApplicationStatusEnum;
import cn.iocoder.yudao.module.product.enums.merchant.ProductMerchantStatusEnum;
import cn.iocoder.yudao.module.product.framework.merchant.config.ProductMerchantApplicationConfig;
import cn.iocoder.yudao.module.product.framework.merchant.config.ProductMerchantApplicationProperties;
import cn.iocoder.yudao.module.system.api.merchant.SystemMerchantUserApi;
import cn.iocoder.yudao.module.infra.api.file.FileApi;
import cn.iocoder.yudao.framework.common.pojo.CommonResult;
import jakarta.annotation.Resource;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.cloud.autoconfigure.RefreshAutoConfiguration;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.TestPropertySource;

import java.util.List;
import java.time.LocalDate;

import static cn.iocoder.yudao.module.product.enums.ErrorCodeConstants.MERCHANT_APPLICATION_QUALIFICATION_REQUIRED;
import static cn.iocoder.yudao.module.product.enums.ErrorCodeConstants.MERCHANT_APPLICATION_REJECT_REASON_REQUIRED;
import static cn.iocoder.yudao.module.product.enums.ErrorCodeConstants.MERCHANT_APPLICATION_STATUS_ERROR;
import static cn.iocoder.yudao.module.product.enums.ErrorCodeConstants.MERCHANT_APPLICATION_TERMS_INVALID;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@Import({ProductMerchantApplicationServiceImpl.class, ProductMerchantServiceImpl.class,
        ProductMerchantApplicationConfig.class, ProductMerchantBusinessTypeServiceImpl.class, RefreshAutoConfiguration.class})
@TestPropertySource(properties = {
        "yudao.product.merchant.application.agreement-version=merchant-agreement-2026-10",
        "yudao.product.merchant.application.agreement-url=/merchant-agreement/merchant-agreement-2026-10.pdf",
        "yudao.product.merchant.application.privacy-policy-version=privacy-policy-2026-10",
        "yudao.product.merchant.application.privacy-policy-url=/merchant-agreement/privacy-policy-2026-10.pdf"
})
class ProductMerchantApplicationServiceImplTest extends BaseDbUnitTest {

    @Resource
    private ProductMerchantApplicationService applicationService;
    @Resource
    private ProductMerchantMapper merchantMapper;
    @Resource
    private ProductMerchantApplicationMapper applicationMapper;
    @Resource
    private ProductMerchantApplicationQualificationMapper applicationQualificationMapper;
    @Resource
    private ProductMerchantBusinessTypeMapper businessTypeMapper;
    @Resource
    private ProductMerchantQualificationRequirementMapper requirementMapper;
    @Resource
    private ProductMerchantReviewRecordMapper reviewRecordMapper;
    @Resource
    private ProductMerchantService merchantService;
    @Resource
    private ProductMerchantApplicationProperties applicationProperties;
    @Resource
    private ProductMerchantBusinessTypeService businessTypeService;
    @Resource
    private javax.sql.DataSource dataSource;
    @MockBean
    private SystemMerchantUserApi systemMerchantUserApi;

    @MockBean
    private FileApi fileApi;

    @BeforeEach
    void setUpMerchantBinding() {
        when(systemMerchantUserApi.validateMerchantBinding(any(), any())).thenReturn(CommonResult.success(true));
        when(fileApi.isMerchantQualificationFileOwnedBy(any(), any())).thenReturn(CommonResult.success(true));
    }

    @Test
    void saveDraft_doesNotBindAccountWhenDraftValidationFails() {
        ProductMerchantBusinessTypeDO businessType = createBusinessType("RETAIL");
        when(systemMerchantUserApi.bindMerchantId(any(), any())).thenReturn(CommonResult.success(true));
        ProductMerchantApplicationDraftReqVO invalidDraft = new ProductMerchantApplicationDraftReqVO()
                .setBusinessTypes(List.of(businessType.getCode()))
                .setName("新商家")
                .setStoreName("新店铺")
                .setCompanyName("新企业")
                .setCreditCode("91310000MA1FL8X123")
                .setLegalPerson("张三")
                .setContactName("李四")
                .setContactMobile("13800138000")
                .setQualifications(List.of(new cn.iocoder.yudao.module.product.controller.admin.merchant.vo.ProductMerchantApplicationQualificationReqVO()
                        .setCode("UNKNOWN")));

        assertThatThrownBy(() -> applicationService.saveDraft(2001L, invalidDraft)).isInstanceOf(RuntimeException.class);
        verifyNoInteractions(systemMerchantUserApi);
        assertThat(merchantMapper.selectByOwnerAccountId(2001L)).isNull();
    }

    @Test
    void saveDraft_bindsCommittedMerchantAndIsIdempotent() {
        ProductMerchantBusinessTypeDO businessType = createBusinessType("RETAIL");
        when(systemMerchantUserApi.bindMerchantId(any(), any())).thenReturn(CommonResult.success(true));
        ProductMerchantApplicationDraftReqVO draft = new ProductMerchantApplicationDraftReqVO()
                .setBusinessTypes(List.of(businessType.getCode()))
                .setName("新商家").setStoreName("新店铺").setCompanyName("新企业")
                .setCreditCode("91310000MA1FL8X123").setLegalPerson("张三")
                .setContactName("李四").setContactMobile("13800138000");

        Long applicationId = applicationService.saveDraft(2002L, draft);
        ProductMerchantDO merchant = merchantMapper.selectByOwnerAccountId(2002L);

        assertThat(applicationId).isNotNull();
        assertThat(merchant).isNotNull();
        verify(systemMerchantUserApi).bindMerchantId(2002L, merchant.getId());

        applicationService.saveDraft(2002L, draft);

        verify(systemMerchantUserApi, org.mockito.Mockito.times(2))
                .bindMerchantId(2002L, merchant.getId());
    }

    @Test
    void saveDraft_retriesSameCommittedMerchantWhenBindingRpcFails() {
        ProductMerchantBusinessTypeDO businessType = createBusinessType("RETAIL");
        when(systemMerchantUserApi.bindMerchantId(any(), any()))
                .thenThrow(new IllegalStateException("temporary RPC failure"));
        ProductMerchantApplicationDraftReqVO draft = new ProductMerchantApplicationDraftReqVO()
                .setBusinessTypes(List.of(businessType.getCode())).setName("可重试商家")
                .setStoreName("可重试店铺").setCompanyName("可重试企业")
                .setCreditCode("91310000MA1FL8X123").setLegalPerson("张三")
                .setContactName("李四").setContactMobile("13800138000");

        assertThatThrownBy(() -> applicationService.saveDraft(2003L, draft))
                .extracting("code").isEqualTo(cn.iocoder.yudao.module.product.enums.ErrorCodeConstants
                        .MERCHANT_IDENTITY_BIND_FAILED.getCode());
        ProductMerchantDO merchant = merchantMapper.selectByOwnerAccountId(2003L);
        assertThat(merchant).isNotNull();
        doReturn(CommonResult.success(true)).when(systemMerchantUserApi).bindMerchantId(any(), any());
        applicationService.saveDraft(2003L, draft);
        assertThat(merchantMapper.selectByOwnerAccountId(2003L).getId()).isEqualTo(merchant.getId());
        verify(systemMerchantUserApi, org.mockito.Mockito.times(2)).bindMerchantId(2003L, merchant.getId());
    }

    @Test
    void submit_requiresEnabledRequiredQualificationAndLeavesDraftUnchanged() {
        ProductMerchantDO merchant = createMerchant(1001L, ProductMerchantStatusEnum.DRAFT.getStatus());
        ProductMerchantBusinessTypeDO businessType = createBusinessType("RETAIL");
        createBusinessType("WHOLESALE");
        ProductMerchantApplicationDO application = createApplication(merchant, businessType.getCode(),
                "初始申请", "INITIAL", ProductMerchantApplicationStatusEnum.DRAFT.getStatus());
        application.setBusinessTypes(List.of("RETAIL", "WHOLESALE"));
        applicationMapper.updateById(application);
        requirementMapper.insert(new ProductMerchantQualificationRequirementDO().setBusinessType("RETAIL")
                .setCode("BUSINESS_LICENSE").setName("营业执照").setRequired(false).setEnabled(true).setSort(1));
        requirementMapper.insert(new ProductMerchantQualificationRequirementDO().setBusinessType("WHOLESALE")
                .setCode("WHOLESALE_LICENSE").setName("批发许可").setRequired(true).setEnabled(true).setSort(1));

        assertThatThrownBy(() -> applicationService.submit(1001L, acceptedConsent(), "198.51.100.11"))
                .extracting("code").isEqualTo(MERCHANT_APPLICATION_QUALIFICATION_REQUIRED.getCode());

        assertThat(applicationMapper.selectById(application.getId()).getStatus())
                .isEqualTo(ProductMerchantApplicationStatusEnum.DRAFT.getStatus());
        assertThat(merchantMapper.selectById(merchant.getId()).getStatus())
                .isEqualTo(ProductMerchantStatusEnum.DRAFT.getStatus());
    }

    @Test
    void submit_snapshotsConsentVersionsAndServerDerivedRequestIp() {
        ProductMerchantDO merchant = createMerchant(1004L, ProductMerchantStatusEnum.DRAFT.getStatus());
        ProductMerchantBusinessTypeDO businessType = createBusinessType("RETAIL");
        ProductMerchantApplicationDO application = createApplication(merchant, businessType.getCode(),
                "同意留档申请", "INITIAL", ProductMerchantApplicationStatusEnum.DRAFT.getStatus());

        applicationService.submit(1004L, acceptedConsent(), "198.51.100.23");

        ProductMerchantApplicationDO submitted = applicationMapper.selectById(application.getId());
        assertThat(submitted.getConsentAccepted()).isTrue();
        assertThat(submitted.getAgreementVersion()).isEqualTo("merchant-agreement-2026-10");
        assertThat(submitted.getPrivacyPolicyVersion()).isEqualTo("privacy-policy-2026-10");
        assertThat(submitted.getConsentTime()).isNotNull();
        assertThat(submitted.getConsentIp()).isEqualTo("198.51.100.23");
        assertThat(reviewRecordMapper.selectListByMerchantId(merchant.getId())).singleElement().satisfies(record -> {
            assertThat(record.getAction()).isEqualTo("SUBMIT");
            assertThat(record.getOperatorId()).isEqualTo(1004L);
            assertThat(record.getOperatorType()).isEqualTo(3);
            assertThat(record.getStatusFrom()).isEqualTo(ProductMerchantStatusEnum.DRAFT.getStatus());
            assertThat(record.getStatusTo()).isEqualTo(ProductMerchantStatusEnum.PENDING.getStatus());
            assertThat(record.getReviewerId()).isNull();
        });
    }

    @Test
    void submit_rejectsConsentThatWasNotAccepted() {
        ProductMerchantDO merchant = createMerchant(1005L, ProductMerchantStatusEnum.DRAFT.getStatus());
        ProductMerchantBusinessTypeDO businessType = createBusinessType("RETAIL");
        ProductMerchantApplicationDO application = createApplication(merchant, businessType.getCode(),
                "未同意申请", "INITIAL", ProductMerchantApplicationStatusEnum.DRAFT.getStatus());
        ProductMerchantApplicationSubmitReqVO consent = acceptedConsent().setAccepted(false);

        assertThatThrownBy(() -> applicationService.submit(1005L, consent, "198.51.100.24"))
                .extracting("code").isEqualTo(MERCHANT_APPLICATION_TERMS_INVALID.getCode());
        assertThat(applicationMapper.selectById(application.getId()).getStatus())
                .isEqualTo(ProductMerchantApplicationStatusEnum.DRAFT.getStatus());
    }

    @Test
    void submit_rejectsConsentVersionsThatAreNotCurrent() {
        ProductMerchantDO merchant = createMerchant(1009L, ProductMerchantStatusEnum.DRAFT.getStatus());
        ProductMerchantBusinessTypeDO businessType = createBusinessType("RETAIL");
        ProductMerchantApplicationDO application = createApplication(merchant, businessType.getCode(),
                "使用旧协议的申请", "INITIAL", ProductMerchantApplicationStatusEnum.DRAFT.getStatus());
        ProductMerchantApplicationSubmitReqVO staleConsent = acceptedConsent()
                .setAgreementVersion("merchant-agreement-old");

        assertThatThrownBy(() -> applicationService.submit(1009L, staleConsent, "198.51.100.26"))
                .extracting("code").isEqualTo(MERCHANT_APPLICATION_TERMS_INVALID.getCode());
        assertThat(applicationMapper.selectById(application.getId()).getStatus())
                .isEqualTo(ProductMerchantApplicationStatusEnum.DRAFT.getStatus());
        assertThat(merchantMapper.selectById(merchant.getId()).getStatus())
                .isEqualTo(ProductMerchantStatusEnum.DRAFT.getStatus());
    }

    @Test
    void submit_rejectsQualificationThatIsNotYetEffective() {
        ProductMerchantDO merchant = createMerchant(1008L, ProductMerchantStatusEnum.DRAFT.getStatus());
        ProductMerchantBusinessTypeDO businessType = createBusinessType("RETAIL");
        ProductMerchantApplicationDO application = createApplication(merchant, businessType.getCode(),
                "资质未生效申请", "INITIAL", ProductMerchantApplicationStatusEnum.DRAFT.getStatus());
        requirementMapper.insert(new ProductMerchantQualificationRequirementDO().setBusinessType("RETAIL")
                .setCode("BUSINESS_LICENSE").setName("营业执照").setRequired(false)
                .setValidityRequired(true).setEnabled(true).setSort(1));
        applicationQualificationMapper.insert(new ProductMerchantApplicationQualificationDO()
                .setApplicationId(application.getId()).setCode("BUSINESS_LICENSE")
                .setValidStart(LocalDate.now().plusDays(1)).setValidEnd(LocalDate.now().plusDays(30)));

        assertThatThrownBy(() -> applicationService.submit(1008L, acceptedConsent(), "198.51.100.25"))
                .extracting("code").isEqualTo(MERCHANT_APPLICATION_QUALIFICATION_REQUIRED.getCode());
        assertThat(applicationMapper.selectById(application.getId()).getStatus())
                .isEqualTo(ProductMerchantApplicationStatusEnum.DRAFT.getStatus());
    }

    @Test
    void submit_rejectsTermsWhenDocumentLinksAreNotReadable() {
        ProductMerchantDO merchant = createMerchant(1010L, ProductMerchantStatusEnum.DRAFT.getStatus());
        ProductMerchantBusinessTypeDO businessType = createBusinessType("RETAIL");
        ProductMerchantApplicationDO application = createApplication(merchant, businessType.getCode(),
                "链接不可读的申请", "INITIAL", ProductMerchantApplicationStatusEnum.DRAFT.getStatus());

        applicationProperties.setAgreementUrl("javascript:alert(1)");
        try {
            assertThatThrownBy(() -> applicationService.submit(1010L, acceptedConsent(), "198.51.100.27"))
                    .extracting("code").isEqualTo(MERCHANT_APPLICATION_TERMS_INVALID.getCode());
        } finally {
            applicationProperties.setAgreementUrl("/merchant-agreement/merchant-agreement-2026-10.pdf");
        }
        assertThat(applicationMapper.selectById(application.getId()).getStatus())
                .isEqualTo(ProductMerchantApplicationStatusEnum.DRAFT.getStatus());
    }

    @Test
    void freezeAndUnfreeze_appendReviewAuditRecords() {
        ProductMerchantDO merchant = createMerchant(1006L, ProductMerchantStatusEnum.APPROVED.getStatus());

        merchantService.freezeMerchant(merchant.getId(), 9002L);
        merchantService.unfreezeMerchant(merchant.getId(), 9003L);

        assertThat(applicationService.getReviewRecords(merchant.getId())).extracting("action")
                .containsExactly("UNFREEZE", "FREEZE");
        assertThat(applicationService.getReviewRecords(merchant.getId())).extracting("reviewerId")
                .containsExactly(9003L, 9002L);
        assertThat(applicationService.getReviewRecords(merchant.getId())).extracting("statusFrom")
                .containsExactly(ProductMerchantStatusEnum.FROZEN.getStatus(), ProductMerchantStatusEnum.APPROVED.getStatus());
        assertThat(applicationService.getReviewRecords(merchant.getId())).extracting("statusTo")
                .containsExactly(ProductMerchantStatusEnum.APPROVED.getStatus(), ProductMerchantStatusEnum.FROZEN.getStatus());
    }

    @Test
    void updateStoreProfile_onlyChangesDisplayFieldsForApprovedMerchant() {
        ProductMerchantDO merchant = createMerchant(1007L, ProductMerchantStatusEnum.APPROVED.getStatus());

        merchantService.updateStoreProfile(merchant.getId(), "新店铺名称", "https://example.com/logo.png", "店铺介绍");

        ProductMerchantDO updated = merchantMapper.selectById(merchant.getId());
        assertThat(updated.getStoreName()).isEqualTo("新店铺名称");
        assertThat(updated.getLogoUrl()).isEqualTo("https://example.com/logo.png");
        assertThat(updated.getDescription()).isEqualTo("店铺介绍");
        assertThat(updated.getName()).isEqualTo("原店铺名称");
    }

    @Test
    void approve_changeCopiesApplicationSnapshotAndPersistsReviewRecord() {
        ProductMerchantDO merchant = createMerchant(1002L, ProductMerchantStatusEnum.CHANGE_PENDING.getStatus());
        ProductMerchantApplicationDO application = createApplication(merchant, "RETAIL", "新店铺名称",
                "CHANGE", ProductMerchantApplicationStatusEnum.PENDING.getStatus());

        applicationService.approve(application.getId(), 9001L);

        assertThat(merchantMapper.selectById(merchant.getId()).getName()).isEqualTo("新店铺名称");
        assertThat(merchantMapper.selectById(merchant.getId()).getStatus())
                .isEqualTo(ProductMerchantStatusEnum.APPROVED.getStatus());
        assertThat(applicationMapper.selectById(application.getId()).getMerchantName()).isEqualTo("新店铺名称");
        assertThat(applicationMapper.selectById(application.getId()).getStatus())
                .isEqualTo(ProductMerchantApplicationStatusEnum.APPROVED.getStatus());
        assertThat(applicationService.getReviewRecords(merchant.getId())).hasSize(1)
                .first().satisfies(record -> {
                    assertThat(record.getAction()).isEqualTo("APPROVE");
                    assertThat(record.getReviewerId()).isEqualTo(9001L);
                });
    }

    @Test
    void reject_requiresNonBlankReasonAtServiceBoundary() {
        ProductMerchantDO merchant = createMerchant(1012L, ProductMerchantStatusEnum.PENDING.getStatus());
        ProductMerchantApplicationDO application = createApplication(merchant, "RETAIL", "待审核商家",
                "INITIAL", ProductMerchantApplicationStatusEnum.PENDING.getStatus());

        assertThatThrownBy(() -> applicationService.reject(application.getId(), "  ", 9005L))
                .extracting("code").isEqualTo(MERCHANT_APPLICATION_REJECT_REASON_REQUIRED.getCode());
        assertThat(applicationMapper.selectById(application.getId()).getStatus())
                .isEqualTo(ProductMerchantApplicationStatusEnum.PENDING.getStatus());
        assertThat(merchantMapper.selectById(merchant.getId()).getStatus())
                .isEqualTo(ProductMerchantStatusEnum.PENDING.getStatus());
    }

    @Test
    void repairOfRejectedApplicationCreatesANewImmutableVersion() {
        when(systemMerchantUserApi.bindMerchantId(any(), any())).thenReturn(CommonResult.success(true));
        ProductMerchantDO merchant = createMerchant(1003L, ProductMerchantStatusEnum.REJECTED.getStatus());
        ProductMerchantApplicationDO rejected = createApplication(merchant, "RETAIL", "原申请名称",
                "INITIAL", ProductMerchantApplicationStatusEnum.REJECTED.getStatus());
        rejected.setBusinessTypes(List.of("RETAIL")).setConsentAccepted(true)
                .setAgreementVersion("old-agreement").setPrivacyPolicyVersion("old-privacy")
                .setConsentIp("198.51.100.3");
        applicationMapper.updateById(rejected);
        createBusinessType("WHOLESALE");
        ProductMerchantApplicationDraftReqVO repair = new ProductMerchantApplicationDraftReqVO()
                .setBusinessTypes(List.of("RETAIL", "WHOLESALE")).setName("更正后的名称");

        Long repairId = applicationService.saveDraft(1003L, repair);

        ProductMerchantApplicationDO savedRejected = applicationMapper.selectById(rejected.getId());
        ProductMerchantApplicationDO savedRepair = applicationMapper.selectById(repairId);
        assertThat(savedRejected.getMerchantName()).isEqualTo("原申请名称");
        assertThat(savedRejected.getStatus()).isEqualTo(ProductMerchantApplicationStatusEnum.REJECTED.getStatus());
        assertThat(savedRepair.getVersionNo()).isEqualTo(2);
        assertThat(savedRepair.getType()).isEqualTo("NEW");
        assertThat(savedRepair.getStatus()).isEqualTo(ProductMerchantApplicationStatusEnum.DRAFT.getStatus());
        assertThat(savedRepair.getMerchantName()).isEqualTo("更正后的名称");
        assertThat(savedRepair.getCreditCode()).isEqualTo("91310000MA1FL8X123");
        assertThat(savedRepair.getBusinessTypes()).containsExactly("RETAIL", "WHOLESALE");
        assertThat(savedRepair.getConsentAccepted()).isNull();
        assertThat(savedRepair.getAgreementVersion()).isNull();
        assertThat(savedRepair.getPrivacyPolicyVersion()).isNull();
        assertThat(savedRepair.getConsentIp()).isNull();
    }

    @Test
    void submit_enforcesAllNumberPatternsAndKeepsRuleSnapshot() {
        ProductMerchantDO merchant = createMerchant(2010L, ProductMerchantStatusEnum.DRAFT.getStatus());
        createBusinessType("RETAIL");
        createBusinessType("WHOLESALE");
        ProductMerchantApplicationDO application = createApplication(merchant, "RETAIL", "格式校验申请",
                "INITIAL", ProductMerchantApplicationStatusEnum.DRAFT.getStatus());
        application.setBusinessTypes(List.of("RETAIL", "WHOLESALE"));
        applicationMapper.updateById(application);
        requirementMapper.insert(new ProductMerchantQualificationRequirementDO().setBusinessType("RETAIL")
                .setCode("LICENSE").setName("执照").setRequired(false).setEnabled(true)
                .setCertificateNoRequired(true).setCertificateNoPattern("[A-Z0-9]{18}"));
        requirementMapper.insert(new ProductMerchantQualificationRequirementDO().setBusinessType("WHOLESALE")
                .setCode("LICENSE").setName("执照").setRequired(true).setEnabled(true)
                .setCertificateNoPattern("91[A-Z0-9]{16}"));
        ProductMerchantApplicationQualificationDO qualification = new ProductMerchantApplicationQualificationDO()
                .setApplicationId(application.getId()).setCode("LICENSE").setFileId(55L)
                .setCertificateNo("00110000MA1FL8X123");
        applicationQualificationMapper.insert(qualification);
        assertThatThrownBy(() -> applicationService.submit(2010L, acceptedConsent(), "198.51.100.30"))
                .extracting("code").isEqualTo(MERCHANT_APPLICATION_QUALIFICATION_REQUIRED.getCode());
        assertThat(applicationMapper.selectById(application.getId()).getStatus()).isZero();
        assertThat(applicationQualificationMapper.selectById(qualification.getId()).getCertificateNoPattern()).isNull();

        qualification.setCertificateNo("91310000MA1FL8X123");
        applicationQualificationMapper.updateById(qualification);
        applicationService.submit(2010L, acceptedConsent(), "198.51.100.30");
        assertThat(applicationQualificationMapper.selectListByApplicationId(application.getId())).singleElement()
                .satisfies(snapshot -> {
                    assertThat(snapshot.getRequired()).isTrue();
                    assertThat(java.util.regex.Pattern.matches(snapshot.getCertificateNoPattern(), "91310000MA1FL8X123")).isTrue();
                    assertThat(java.util.regex.Pattern.matches(snapshot.getCertificateNoPattern(), "00110000MA1FL8X123")).isFalse();
                });
    }

    @Test
    void submit_rechecksFileOwnershipAndExistence() {
        ProductMerchantDO merchant = createMerchant(2011L, ProductMerchantStatusEnum.DRAFT.getStatus());
        createBusinessType("RETAIL");
        ProductMerchantApplicationDO application = createApplication(merchant, "RETAIL", "文件已删除申请",
                "INITIAL", ProductMerchantApplicationStatusEnum.DRAFT.getStatus());
        requirementMapper.insert(new ProductMerchantQualificationRequirementDO().setBusinessType("RETAIL")
                .setCode("LICENSE").setName("执照").setRequired(true).setEnabled(true));
        applicationQualificationMapper.insert(new ProductMerchantApplicationQualificationDO()
                .setApplicationId(application.getId()).setCode("LICENSE").setFileId(55L));
        when(fileApi.isMerchantQualificationFileOwnedBy(55L, 2011L)).thenReturn(CommonResult.success(false));
        assertThatThrownBy(() -> applicationService.submit(2011L, acceptedConsent(), "198.51.100.31"))
                .extracting("code").isEqualTo(cn.iocoder.yudao.module.product.enums.ErrorCodeConstants
                        .MERCHANT_APPLICATION_FILE_INVALID.getCode());
        assertThat(applicationMapper.selectById(application.getId()).getStatus()).isZero();
    }

    @Test
    void approve_rejectsQualificationExpiredWhileWaitingForReview() {
        ProductMerchantDO merchant = createMerchant(2012L, ProductMerchantStatusEnum.PENDING.getStatus());
        ProductMerchantApplicationDO application = createApplication(merchant, "RETAIL", "等待期间过期申请",
                "INITIAL", ProductMerchantApplicationStatusEnum.PENDING.getStatus());
        applicationQualificationMapper.insert(new ProductMerchantApplicationQualificationDO()
                .setApplicationId(application.getId()).setCode("LICENSE").setFileId(55L)
                .setValidEnd(LocalDate.now().minusDays(1)));
        assertThatThrownBy(() -> applicationService.approve(application.getId(), 9001L))
                .extracting("code").isEqualTo(MERCHANT_APPLICATION_QUALIFICATION_REQUIRED.getCode());
        assertThat(merchantMapper.selectById(merchant.getId()).getStatus())
                .isEqualTo(ProductMerchantStatusEnum.PENDING.getStatus());
    }

    @Test
    void saveDraft_recoversWhenRemoteCommitSucceedsButResponseIsLost() {
        createBusinessType("RETAIL");
        java.util.concurrent.atomic.AtomicReference<Long> remoteMerchantId = new java.util.concurrent.atomic.AtomicReference<>();
        org.mockito.Mockito.doAnswer(invocation -> {
            Long merchantId = invocation.getArgument(1);
            // A separate connection must see the merchant: binding only runs after commit.
            try (var connection = dataSource.getConnection();
                 var statement = connection.prepareStatement("SELECT id FROM product_merchant WHERE owner_account_id = ?")) {
                statement.setLong(1, 2013L);
                try (var result = statement.executeQuery()) {
                    assertThat(result.next()).isTrue();
                    assertThat(result.getLong(1)).isEqualTo(merchantId);
                }
            }
            if (remoteMerchantId.compareAndSet(null, merchantId)) {
                throw new IllegalStateException("remote committed, response lost");
            }
            assertThat(merchantId).isEqualTo(remoteMerchantId.get());
            return CommonResult.success(true);
        }).when(systemMerchantUserApi).bindMerchantId(any(), any());
        var draft = new ProductMerchantApplicationDraftReqVO().setBusinessTypes(List.of("RETAIL"));
        assertThatThrownBy(() -> applicationService.saveDraft(2013L, draft)).isInstanceOf(RuntimeException.class);
        applicationService.saveDraft(2013L, draft);
        assertThat(merchantMapper.selectByOwnerAccountId(2013L).getId()).isEqualTo(remoteMerchantId.get());
    }

    @Test
    void submit_omittedOptionalQualificationDoesNotRequireNumberOrDates() {
        ProductMerchantDO merchant = createMerchant(2014L, ProductMerchantStatusEnum.DRAFT.getStatus());
        createBusinessType("RETAIL");
        var application = createApplication(merchant, "RETAIL", "可选资质申请", "INITIAL", 0);
        requirementMapper.insert(new ProductMerchantQualificationRequirementDO().setBusinessType("RETAIL")
                .setCode("OPTIONAL").setName("可选证件").setRequired(false).setEnabled(true)
                .setCertificateNoRequired(true).setValidityRequired(true));
        applicationQualificationMapper.insert(new ProductMerchantApplicationQualificationDO()
                .setApplicationId(application.getId()).setCode("OPTIONAL"));
        applicationService.submit(2014L, acceptedConsent(), "198.51.100.32");
        assertThat(applicationMapper.selectById(application.getId()).getStatus()).isEqualTo(10);
        assertThat(applicationQualificationMapper.selectListByApplicationId(application.getId())).isEmpty();
    }

    @Test
    void submit_providedOptionalQualificationRequiresCompleteEvidence() {
        ProductMerchantDO merchant = createMerchant(2015L, ProductMerchantStatusEnum.DRAFT.getStatus());
        createBusinessType("RETAIL");
        var application = createApplication(merchant, "RETAIL", "不完整可选资质申请", "INITIAL", 0);
        requirementMapper.insert(new ProductMerchantQualificationRequirementDO().setBusinessType("RETAIL")
                .setCode("OPTIONAL").setName("可选证件").setRequired(false).setEnabled(true)
                .setCertificateNoRequired(true).setValidityRequired(true));
        applicationQualificationMapper.insert(new ProductMerchantApplicationQualificationDO()
                .setApplicationId(application.getId()).setCode("OPTIONAL").setFileId(55L));
        assertThatThrownBy(() -> applicationService.submit(2015L, acceptedConsent(), "198.51.100.32"))
                .extracting("code").isEqualTo(MERCHANT_APPLICATION_QUALIFICATION_REQUIRED.getCode());
    }

    @Test
    void businessType_invalidPatternRollsBackConfigurationUpdate() {
        var type = createBusinessType("RETAIL");
        requirementMapper.insert(new ProductMerchantQualificationRequirementDO().setBusinessType("RETAIL")
                .setCode("LICENSE").setName("旧规则").setEnabled(true).setCertificateNoPattern("[0-9]+"));
        var request = new cn.iocoder.yudao.module.product.controller.admin.merchant.vo.ProductMerchantBusinessTypeSaveReqVO()
                .setId(type.getId()).setCode("RETAIL").setName("修改后的名称")
                .setRequirements(List.of(new cn.iocoder.yudao.module.product.controller.admin.merchant.vo.ProductMerchantQualificationRequirementSaveReqVO()
                        .setCode("LICENSE").setName("非法规则").setCertificateNoPattern("[")));
        assertThatThrownBy(() -> businessTypeService.save(request)).isInstanceOf(RuntimeException.class);
        assertThat(businessTypeMapper.selectById(type.getId()).getName()).isEqualTo(type.getName());
        assertThat(requirementMapper.selectEnabledListByBusinessType("RETAIL")).singleElement()
                .satisfies(rule -> assertThat(rule.getCertificateNoPattern()).isEqualTo("[0-9]+"));
    }

    @Test
    void legacy_pendingMerchantMustRepairThroughRegisteredAccount() {
        var merchant = createMerchant(null, 10);
        var legacy = createApplication(merchant, "LEGACY", "历史待审", "LEGACY", 10);
        assertThatThrownBy(() -> applicationService.approve(legacy.getId(), 9001L))
                .extracting("code").isEqualTo(MERCHANT_APPLICATION_STATUS_ERROR.getCode());
        when(systemMerchantUserApi.canBindMerchant(2016L, merchant.getId())).thenReturn(CommonResult.success(true));
        when(systemMerchantUserApi.bindMerchantId(2016L, merchant.getId())).thenReturn(CommonResult.success(true));
        applicationService.bindLegacyAccount(merchant.getId(), 2016L, 9001L);
        assertThat(merchantMapper.selectById(merchant.getId()).getStatus()).isEqualTo(30);
        createBusinessType("RETAIL");
        Long repairId = applicationService.saveDraft(2016L, new ProductMerchantApplicationDraftReqVO()
                .setBusinessTypes(List.of("RETAIL")).setQualifications(List.of()));
        assertThat(applicationMapper.selectById(repairId).getVersionNo()).isEqualTo(2);
        assertThat(applicationMapper.selectById(legacy.getId()).getStatus()).isEqualTo(10);
        applicationService.submit(2016L, acceptedConsent(), "198.51.100.33");
        applicationService.approve(repairId, 9001L);
        assertThat(merchantMapper.selectById(merchant.getId()).getStatus()).isEqualTo(20);
        assertThat(reviewRecordMapper.selectListByMerchantId(merchant.getId())).hasSize(3);
    }

    @Test
    void legacy_bindingRejectsUnavailableOrAlreadyBoundAccount() {
        var merchant = createMerchant(null, 0);
        createApplication(merchant, "LEGACY", "历史草稿", "LEGACY", 0);
        when(systemMerchantUserApi.canBindMerchant(2017L, merchant.getId())).thenReturn(CommonResult.success(false));
        assertThatThrownBy(() -> applicationService.bindLegacyAccount(merchant.getId(), 2017L, 9001L)).isInstanceOf(RuntimeException.class);
        assertThat(merchantMapper.selectById(merchant.getId()).getOwnerAccountId()).isNull();
        assertThat(reviewRecordMapper.selectListByMerchantId(merchant.getId())).isEmpty();
    }

    @Test
    void get_presignFailureDoesNotPreventReviewAndNeverExposesLegacyUrl() {
        var merchant = createMerchant(2018L, 10);
        var application = createApplication(merchant, "RETAIL", "待审资料", "INITIAL", 10);
        applicationQualificationMapper.insert(new ProductMerchantApplicationQualificationDO()
                .setApplicationId(application.getId()).setCode("LICENSE").setFileId(56L)
                .setLegacyFileUrl("https://legacy.example.test/private-license"));
        when(fileApi.presignGetUrl(56L, 900)).thenThrow(new IllegalStateException("storage unavailable"));

        var detail = applicationService.get(application.getId());

        assertThat(detail.getCurrentOwnerAccountId()).isEqualTo(2018L);
        assertThat(detail.getQualifications()).singleElement().satisfies(item -> {
            assertThat(item.getPreviewUnavailable()).isTrue();
            assertThat(item.getPreviewUrl()).isNull();
        });
        assertThat(cn.iocoder.yudao.framework.common.util.json.JsonUtils.toJsonString(detail))
                .doesNotContain("legacy.example.test");
        applicationService.reject(application.getId(), "请重新上传可核验的证件", 9001L);
        assertThat(merchantMapper.selectById(merchant.getId()).getStatus()).isEqualTo(30);
    }

    private ProductMerchantDO createMerchant(Long ownerAccountId, Integer status) {
        ProductMerchantDO merchant = new ProductMerchantDO().setOwnerAccountId(ownerAccountId)
                .setName("原店铺名称").setStoreName("原店铺名称").setStatus(status);
        merchantMapper.insert(merchant);
        return merchant;
    }

    private ProductMerchantBusinessTypeDO createBusinessType(String code) {
        ProductMerchantBusinessTypeDO businessType = new ProductMerchantBusinessTypeDO()
                .setCode(code).setName("零售").setEnabled(true);
        businessTypeMapper.insert(businessType);
        return businessType;
    }

    private ProductMerchantApplicationSubmitReqVO acceptedConsent() {
        return new ProductMerchantApplicationSubmitReqVO().setAgreementVersion("merchant-agreement-2026-10")
                .setPrivacyPolicyVersion("privacy-policy-2026-10").setAccepted(true);
    }

    private ProductMerchantApplicationDO createApplication(ProductMerchantDO merchant, String businessType,
                                                             String merchantName, String type, Integer status) {
        ProductMerchantApplicationDO application = new ProductMerchantApplicationDO()
                .setMerchantId(merchant.getId()).setOwnerAccountId(merchant.getOwnerAccountId())
                .setVersionNo(1).setType(type).setStatus(status).setBusinessType(businessType)
                .setMerchantName(merchantName).setStoreName(merchantName).setCompanyName("测试企业")
                .setCreditCode("91310000MA1FL8X123").setLegalPerson("张三")
                .setContactName("李四").setContactMobile("13800138000");
        applicationMapper.insert(application);
        return application;
    }
}
