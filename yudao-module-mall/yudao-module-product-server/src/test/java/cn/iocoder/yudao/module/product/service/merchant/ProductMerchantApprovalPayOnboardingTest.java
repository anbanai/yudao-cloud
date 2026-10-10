package cn.iocoder.yudao.module.product.service.merchant;

import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.framework.test.core.ut.BaseDbUnitTest;
import cn.iocoder.yudao.module.infra.api.file.FileApi;
import cn.iocoder.yudao.module.pay.api.fund.PayFundApi;
import cn.iocoder.yudao.module.product.dal.dataobject.merchant.ProductMerchantDO;
import cn.iocoder.yudao.module.product.dal.dataobject.merchant.ProductMerchantPayOnboardingDO;
import cn.iocoder.yudao.module.product.dal.mysql.merchant.ProductMerchantMapper;
import cn.iocoder.yudao.module.product.dal.mysql.merchant.ProductMerchantPayOnboardingMapper;
import cn.iocoder.yudao.module.product.framework.merchant.config.ProductMerchantPayProperties;
import jakarta.annotation.Resource;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.boot.test.mock.mockito.SpyBean;
import org.springframework.context.annotation.Import;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verifyNoInteractions;

/** 原有审批入口与持久化入网任务必须在同一事务成功或回滚。 */
@Import({ProductMerchantServiceImpl.class, ProductMerchantPayOnboardingService.class,
        ProductMerchantPayProperties.class})
class ProductMerchantApprovalPayOnboardingTest extends BaseDbUnitTest {

    @Resource
    private ProductMerchantService merchantService;
    @Resource
    private ProductMerchantMapper merchantMapper;
    @Resource
    private ProductMerchantPayOnboardingMapper onboardingMapper;
    @SpyBean
    private ProductMerchantPayOnboardingService onboardingService;
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
    void testApproveMerchant_commitsApprovalAndPendingTaskWithoutNetworkRequest() {
        ProductMerchantDO merchant = merchant(10);

        merchantService.approveMerchant(merchant.getId(), 99L);

        ProductMerchantDO approved = merchantMapper.selectById(merchant.getId());
        ProductMerchantPayOnboardingDO task = onboardingMapper.selectByMerchantId(merchant.getId(), 0L).get(0);
        assertThat(approved.getStatus()).isEqualTo(20);
        assertThat(approved.getReviewerId()).isEqualTo(99L);
        assertThat(approved.getReviewTime()).isNotNull();
        assertThat(task.getMerchantApprovalTime()).isEqualTo(approved.getReviewTime());
        assertThat(task.getState()).isEqualTo("WAITING_MATERIALS");
        verifyNoInteractions(payFundApi, fileApi);
    }

    @Test
    void testApproveMerchant_rollsBackApprovalWhenTaskCannotBePersisted() {
        ProductMerchantDO merchant = merchant(10);
        doThrow(new IllegalStateException("simulated task persistence failure"))
                .when(onboardingService).enqueueApprovedMerchant(merchant.getId());

        assertThatThrownBy(() -> merchantService.approveMerchant(merchant.getId(), 99L))
                .isInstanceOf(IllegalStateException.class);

        ProductMerchantDO pending = merchantMapper.selectById(merchant.getId());
        assertThat(pending.getStatus()).isEqualTo(10);
        assertThat(pending.getReviewerId()).isNull();
        assertThat(pending.getReviewTime()).isNull();
        assertThat(onboardingMapper.selectByMerchantId(merchant.getId(), 0L)).isEmpty();
        verifyNoInteractions(payFundApi, fileApi);
    }

    @Test
    void testApproveMerchant_rejectsInvalidStatusWithoutCreatingTask() {
        ProductMerchantDO merchant = merchant(0);

        assertThatThrownBy(() -> merchantService.approveMerchant(merchant.getId(), 99L))
                .isInstanceOf(RuntimeException.class);

        assertThat(merchantMapper.selectById(merchant.getId()).getStatus()).isEqualTo(0);
        assertThat(onboardingMapper.selectByMerchantId(merchant.getId(), 0L)).isEmpty();
        verifyNoInteractions(payFundApi, fileApi);
    }

    private ProductMerchantDO merchant(int status) {
        ProductMerchantDO merchant = new ProductMerchantDO().setName("原有审批入口商家").setStatus(status);
        merchantMapper.insert(merchant);
        return merchant;
    }
}
