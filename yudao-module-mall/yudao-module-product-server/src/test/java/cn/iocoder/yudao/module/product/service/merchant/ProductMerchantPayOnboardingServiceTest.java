package cn.iocoder.yudao.module.product.service.merchant;

import cn.iocoder.yudao.framework.common.pojo.CommonResult;
import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.framework.test.core.ut.BaseDbUnitTest;
import cn.iocoder.yudao.module.infra.api.file.FileApi;
import cn.iocoder.yudao.module.infra.api.file.dto.FileCreateRespDTO;
import org.springframework.mock.web.MockMultipartFile;
import cn.iocoder.yudao.module.pay.api.fund.PayFundApi;
import cn.iocoder.yudao.module.pay.api.fund.dto.PayFundMerchantAccountRespDTO;
import cn.iocoder.yudao.module.pay.api.fund.dto.PayFundMerchantRegisterReqDTO;
import cn.iocoder.yudao.module.product.controller.admin.merchant.vo.ProductMerchantPayMaterialsReqVO;
import cn.iocoder.yudao.module.product.controller.admin.merchant.vo.ProductMerchantPayOnboardingPageReqVO;
import cn.iocoder.yudao.module.product.dal.dataobject.merchant.ProductMerchantDO;
import cn.iocoder.yudao.module.product.dal.dataobject.merchant.ProductMerchantPayOnboardingDO;
import cn.iocoder.yudao.module.product.dal.mysql.merchant.ProductMerchantPayApprovedMerchantMapper;
import cn.iocoder.yudao.module.product.dal.mysql.merchant.ProductMerchantPayOnboardingMapper;
import cn.iocoder.yudao.module.product.framework.merchant.config.ProductMerchantPayProperties;
import jakarta.annotation.Resource;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;

import javax.sql.DataSource;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@Import({ProductMerchantPayOnboardingService.class, ProductMerchantPayProperties.class})
class ProductMerchantPayOnboardingServiceTest extends BaseDbUnitTest {

    @Resource
    private ProductMerchantPayOnboardingService service;
    @Resource
    private ProductMerchantPayApprovedMerchantMapper merchantMapper;
    @Resource
    private ProductMerchantPayOnboardingMapper onboardingMapper;
    @Resource
    private DataSource dataSource;
    @MockBean
    private PayFundApi payFundApi;
    @MockBean
    private FileApi fileApi;

    @BeforeEach
    void setTenant() {
        TenantContextHolder.setTenantId(0L);
    }

    @AfterEach
    void clearTenant() {
        TenantContextHolder.clear();
    }

    @Test
    void approvalEnqueuesDurableTaskAndWaitsForOfficialMaterials() {
        ProductMerchantDO application = merchant(1, 10);

        merchantMapper.updateById(new ProductMerchantDO().setId(application.getId()).setStatus(20));
        service.enqueueApprovedMerchant(application.getId());
        service.enqueueApprovedMerchant(application.getId());
        service.processPending();

        ProductMerchantPayOnboardingDO task = task(application);
        assertThat(task.getState()).isEqualTo("WAITING_MATERIALS");
        assertThat(service.get(task(application).getId()).getMissingMaterials()).contains("merchantSubjectInfo",
                "merchantCorporationInfo", "merchantContactInfo", "businessAddressInfo",
                "functionServiceQualificationInfo.shareScene");
        assertThat(task.getMerchantApprovalTime()).isEqualTo(application.getReviewTime());
        verifyNoInteractions(payFundApi);
    }

    @Test
    void materialCompletionRetriesWithSameRequestThenPollsToSuccessfulCompletion() {
        ProductMerchantDO application = merchant(1, 20);
        service.enqueueApprovedMerchant(application.getId());
        when(fileApi.isFileCreatedByCurrentUser(71L)).thenReturn(CommonResult.success(true));
        when(fileApi.presignGetUrl(71L, 900)).thenReturn(CommonResult.success("https://files.test/short-lived"));
        service.saveMaterials(new ProductMerchantPayMaterialsReqVO().setOnboardingId(task(application).getId())
                .setMaterials(materials()).setFileReferences(Map.of("merchantSubjectInfo.businessLicenseUrl", 71L)));
        String stored = jdbc().queryForObject("SELECT materials_json FROM product_merchant_pay_onboarding WHERE id = ?",
                String.class, task(application).getId());
        assertThat(stored).startsWith("AES:v1:").doesNotContain("张三", "110101199001011234", "short-lived");
        when(payFundApi.registerMerchant(any())).thenThrow(new IllegalStateException("raw material secret"))
                .thenReturn(CommonResult.success(new PayFundMerchantAccountRespDTO().setMerchantId(application.getId())
                        .setOnboardingStatus(10).setRequestNo(task(application).getRequestNo())));

        service.processPending();
        ProductMerchantPayOnboardingDO retry = task(application);
        assertThat(retry.getState()).isEqualTo("RETRY");
        assertThat(retry.getLastError()).isEqualTo("PAY_REQUEST_FAILED").doesNotContain("secret");
        makeDue(retry);
        service.processPending();
        assertThat(task(application).getState()).isEqualTo("SUBMITTED");
        ArgumentCaptor<PayFundMerchantRegisterReqDTO> requests = ArgumentCaptor.forClass(PayFundMerchantRegisterReqDTO.class);
        verify(payFundApi, times(2)).registerMerchant(requests.capture());
        assertThat(requests.getAllValues()).extracting(PayFundMerchantRegisterReqDTO::getRequestNo).containsOnly(retry.getRequestNo());
        assertThat(requests.getValue().getMaterials().get("materialsRef")).isEqualTo(
                "product-merchant-pay-onboarding:" + task(application).getId() + ":m1");
        assertThat(((Map<?, ?>) requests.getValue().getMaterials().get("merchantSubjectInfo")).get("businessLicenseUrl"))
                .isEqualTo("https://files.test/short-lived");
        when(payFundApi.getMerchant("mall", application.getId())).thenReturn(CommonResult.success(
                new PayFundMerchantAccountRespDTO().setOnboardingStatus(100)));
        makeDue(task(application));
        service.processPending();

        assertThat(task(application).getState()).isEqualTo("COMPLETED");
        service.processPending();
        verify(payFundApi, times(1)).getMerchant("mall", application.getId());
    }

    @Test
    void payServiceRecordedSdkFailureIsRetriedAndNeverMarkedSubmitted() {
        ProductMerchantDO application = merchant(1, 20);
        service.enqueueApprovedMerchant(application.getId());
        service.saveMaterials(new ProductMerchantPayMaterialsReqVO().setOnboardingId(task(application).getId()).setMaterials(materials()));
        when(payFundApi.registerMerchant(any())).thenReturn(CommonResult.success(
                new PayFundMerchantAccountRespDTO().setOnboardingStatus(10).setLastError("SDK material body")));

        service.processPending();

        assertThat(task(application).getState()).isEqualTo("RETRY");
        assertThat(task(application).getLastError()).isEqualTo("PAY_REQUEST_FAILED");
    }

    @Test
    void failedPollingRetainsAcceptedRequestAndResumesQueryRatherThanRegisteringAgain() {
        ProductMerchantDO application = merchant(1, 20);
        service.enqueueApprovedMerchant(application.getId());
        service.saveMaterials(new ProductMerchantPayMaterialsReqVO().setOnboardingId(task(application).getId()).setMaterials(materials()));
        when(payFundApi.registerMerchant(any())).thenReturn(CommonResult.success(
                new PayFundMerchantAccountRespDTO().setOnboardingStatus(10)));
        service.processPending();
        when(payFundApi.getMerchant("mall", application.getId())).thenThrow(new IllegalStateException("raw body"))
                .thenReturn(CommonResult.success(new PayFundMerchantAccountRespDTO().setOnboardingStatus(100)));
        makeDue(task(application));
        service.processPending();
        assertThat(task(application).getState()).isEqualTo("RETRY");
        assertThat(task(application).getRequestAccepted()).isTrue();
        assertThat(task(application).getLastError()).isEqualTo("PAY_QUERY_FAILED");
        makeDue(task(application));
        service.processPending();
        assertThat(task(application).getState()).isEqualTo("COMPLETED");
        verify(payFundApi, times(1)).registerMerchant(any());
        verify(payFundApi, times(2)).getMerchant("mall", application.getId());
    }

    @Test
    void microMerchantContactIsOptionalAndChannelCredentialsAreRejected() {
        ProductMerchantDO application = merchant(1, 20);
        service.enqueueApprovedMerchant(application.getId());
        Map<String, Object> microMaterials = materials();
        microMaterials.put("merchantType", "SMALL_MICRO");
        microMaterials.remove("merchantContactInfo");
        service.saveMaterials(new ProductMerchantPayMaterialsReqVO().setOnboardingId(task(application).getId())
                .setMaterials(microMaterials));
        assertThat(task(application).getState()).isEqualTo("READY");
        microMaterials.put("accountInfo", Map.of("privateKey", "secret-key"));
        assertThatThrownBy(() -> service.saveMaterials(new ProductMerchantPayMaterialsReqVO()
                .setOnboardingId(task(application).getId()).setMaterials(microMaterials))).isInstanceOf(RuntimeException.class);
        assertThat(task(application).getMaterialsVersion()).isEqualTo(1);
    }

    @Test
    void signingAndRejectedApplicationsRequireActionAndCanResumeAfterMaterialsRevision() {
        ProductMerchantDO application = merchant(1, 20);
        service.enqueueApprovedMerchant(application.getId());
        service.saveMaterials(new ProductMerchantPayMaterialsReqVO().setOnboardingId(task(application).getId()).setMaterials(materials()));
        when(payFundApi.registerMerchant(any())).thenReturn(CommonResult.success(
                new PayFundMerchantAccountRespDTO().setOnboardingStatus(40).setSignUrl("https://pay.test/sign-token")));
        service.processPending();
        assertThat(service.get(task(application).getId()).getState()).isEqualTo("ACTION_REQUIRED");
        assertThat(service.get(task(application).getId()).getSignUrl()).isEqualTo("https://pay.test/sign-token");
        assertThat(jdbc().queryForObject("SELECT sign_url FROM product_merchant_pay_onboarding WHERE id = ?",
                String.class, task(application).getId())).startsWith("AES:v1:");
        assertThat(service.getPage(new ProductMerchantPayOnboardingPageReqVO()).getList()).singleElement()
                .satisfies(row -> {
                    assertThat(row.getMerchantName()).isEqualTo(application.getName());
                    assertThat(row.getSignUrl()).isNull();
                });
        when(payFundApi.getMerchant("mall", application.getId())).thenReturn(CommonResult.success(
                new PayFundMerchantAccountRespDTO().setOnboardingStatus(20)));
        makeDue(task(application));
        service.processPending();
        assertThat(task(application).getNextRetryTime()).isNull();
        String requestNo = task(application).getRequestNo();
        service.saveMaterials(new ProductMerchantPayMaterialsReqVO().setOnboardingId(task(application).getId()).setMaterials(materials()));
        assertThat(task(application).getMaterialsVersion()).isEqualTo(2);
        assertThat(task(application).getRequestNo()).isEqualTo(requestNo);
        assertThat(task(application).getState()).isEqualTo("READY");
        assertThat(jdbc().queryForList("SELECT materials_version FROM product_merchant_pay_material_revision"
                + " WHERE onboarding_id = ? ORDER BY materials_version", Integer.class, task(application).getId()))
                .containsExactly(1, 2);
        assertThat(jdbc().queryForList("SELECT materials_json FROM product_merchant_pay_material_revision"
                + " WHERE onboarding_id = ?", String.class, task(application).getId()))
                .allSatisfy(json -> assertThat(json).startsWith("AES:v1:").doesNotContain("张三", "110101199001011234"));
    }

    @Test
    void incompleteMaterialsAndForeignFilesDoNotDispatchOrOverwriteStoredMaterials() {
        ProductMerchantDO application = merchant(1, 20);
        service.enqueueApprovedMerchant(application.getId());
        assertThatThrownBy(() -> service.saveMaterials(new ProductMerchantPayMaterialsReqVO()
                .setOnboardingId(task(application).getId()).setMaterials(Map.of("merchantSubjectInfo", Map.of("name", "企业")))))
                .isInstanceOf(RuntimeException.class);
        when(fileApi.isFileCreatedByCurrentUser(99L)).thenReturn(CommonResult.success(false));
        assertThatThrownBy(() -> service.saveMaterials(new ProductMerchantPayMaterialsReqVO()
                .setOnboardingId(task(application).getId()).setMaterials(materials())
                .setFileReferences(Map.of("merchantSubjectInfo.businessLicenseUrl", 99L))))
                .isInstanceOf(RuntimeException.class);
        assertThat(task(application).getMaterialsVersion()).isZero();
        verifyNoInteractions(payFundApi);
    }

    @Test
    void laterApprovalSnapshotSupersedesOldTaskAndTenantCannotReadIt() {
        ProductMerchantDO merchant = merchant(1, 20);
        Long firstId = service.enqueueApprovedMerchant(merchant.getId());
        String requestNo = onboardingMapper.selectById(firstId).getRequestNo();
        merchantMapper.updateById(new ProductMerchantDO().setId(merchant.getId())
                .setReviewTime(merchant.getReviewTime().plusSeconds(1)));
        Long secondId = service.enqueueApprovedMerchant(merchant.getId());

        assertThat(onboardingMapper.selectById(firstId).getState()).isEqualTo("SUPERSEDED");
        assertThat(onboardingMapper.selectById(secondId).getRequestNo()).isEqualTo(requestNo);
        TenantContextHolder.setTenantId(9L);
        assertThat(service.getPage(new ProductMerchantPayOnboardingPageReqVO()).getList()).isEmpty();
        assertThatThrownBy(() -> service.get(secondId)).isInstanceOf(RuntimeException.class);
        verifyNoInteractions(payFundApi);
    }

    @Test
    void onlyApprovedMerchantsCanEnqueueAndFrozenTasksWaitForReapproval() {
        ProductMerchantDO pending = merchant(1, 10);
        assertThatThrownBy(() -> service.enqueueApprovedMerchant(pending.getId())).isInstanceOf(RuntimeException.class);
        merchantMapper.updateById(new ProductMerchantDO().setId(pending.getId()).setStatus(20));
        Long id = service.enqueueApprovedMerchant(pending.getId());
        service.saveMaterials(new ProductMerchantPayMaterialsReqVO().setOnboardingId(id).setMaterials(materials()));
        merchantMapper.updateById(new ProductMerchantDO().setId(pending.getId()).setStatus(40));
        service.processPending();
        assertThat(onboardingMapper.selectById(id).getState()).isEqualTo("READY");
        verifyNoInteractions(payFundApi);
    }

    @Test
    void invalidFileIdsAndCredentialBearingLinksAreRejected() {
        ProductMerchantDO merchant = merchant(1, 20);
        Long id = service.enqueueApprovedMerchant(merchant.getId());
        assertThatThrownBy(() -> service.saveMaterials(new ProductMerchantPayMaterialsReqVO().setOnboardingId(id)
                .setMaterials(materials()).setFileReferences(Map.of("merchantSubjectInfo.file", -1L))))
                .isInstanceOf(RuntimeException.class);
        service.saveMaterials(new ProductMerchantPayMaterialsReqVO().setOnboardingId(id).setMaterials(materials()));
        when(payFundApi.registerMerchant(any())).thenReturn(CommonResult.success(new PayFundMerchantAccountRespDTO()
                .setOnboardingStatus(40).setSignUrl("https://user:secret@pay.test/sign")));
        service.processPending();
        assertThat(service.get(id).getSignUrl()).isNull();
    }

    @Test
    void uploadUsesPrivateStorageAndReturnsOnlyAuthorizedFileId() {
        ProductMerchantDO merchant = merchant(1, 20);
        Long id = service.enqueueApprovedMerchant(merchant.getId());
        when(fileApi.isPrivateMasterSupported()).thenReturn(CommonResult.success(true));
        when(fileApi.createPrivateFile(any(byte[].class), anyString(), anyString(), eq("image/png")))
                .thenReturn(new FileCreateRespDTO().setId(71L));
        MockMultipartFile image = new MockMultipartFile("file", "sensitive-name.png", "image/png", new byte[] {1, 2, 3});

        assertThat(service.uploadPrivateFile(id, image)).isEqualTo(71L);
        verify(fileApi).createPrivateFile(any(byte[].class), argThat(name -> !name.contains("sensitive")),
                eq("merchant-pay/0/" + id), eq("image/png"));
        TenantContextHolder.setTenantId(9L);
        assertThatThrownBy(() -> service.uploadPrivateFile(id, image)).isInstanceOf(RuntimeException.class);
    }

    @Test
    void privateUploadRejectsUnsupportedStorageOrFileType() {
        ProductMerchantDO merchant = merchant(1, 20);
        Long id = service.enqueueApprovedMerchant(merchant.getId());
        MockMultipartFile image = new MockMultipartFile("file", "photo.png", "image/png", new byte[] {1, 2, 3});
        when(fileApi.isPrivateMasterSupported()).thenReturn(CommonResult.success(false));
        assertThatThrownBy(() -> service.uploadPrivateFile(id, image)).isInstanceOf(RuntimeException.class);
        MockMultipartFile script = new MockMultipartFile("file", "photo.html", "text/html", new byte[] {1});
        assertThatThrownBy(() -> service.uploadPrivateFile(id, script)).isInstanceOf(RuntimeException.class);
        verify(fileApi, never()).createPrivateFile(any(byte[].class), anyString(), anyString(), anyString());
    }

    private ProductMerchantDO merchant(int version, int status) {
        ProductMerchantDO merchant = new ProductMerchantDO().setName("商家 " + version).setStatus(status)
                .setReviewTime(LocalDateTime.of(2026, 10, 1, 12, 0, version));
        merchantMapper.insert(merchant);
        return merchant;
    }

    private ProductMerchantPayOnboardingDO task(ProductMerchantDO merchant) {
        return onboardingMapper.selectByMerchantId(merchant.getId(), 0L).stream()
                .max(java.util.Comparator.comparing(ProductMerchantPayOnboardingDO::getId)).orElseThrow();
    }

    private void makeDue(ProductMerchantPayOnboardingDO task) {
        onboardingMapper.updateById(new ProductMerchantPayOnboardingDO().setId(task.getId())
                .setNextRetryTime(LocalDateTime.now().minusSeconds(1)));
    }

    private JdbcTemplate jdbc() {
        return new JdbcTemplate(dataSource);
    }

    private Map<String, Object> materials() {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("merchantSubjectInfo", Map.of("merchantType", "ENTERPRISE"));
        result.put("merchantCorporationInfo", Map.of("legalPersonName", "张三", "idCardNo", "110101199001011234"));
        result.put("merchantContactInfo", Map.of("contactName", "李四"));
        result.put("businessAddressInfo", Map.of("province", "北京市"));
        result.put("functionServiceQualificationInfo", Map.of("shareScene", "MINI_PROGRAM"));
        return result;
    }
}
