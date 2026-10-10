package cn.iocoder.yudao.module.pay.service.payfund;

import cn.iocoder.yudao.framework.test.core.ut.BaseDbUnitTest;
import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.pay.api.fund.dto.PayFundMerchantAccountRespDTO;
import cn.iocoder.yudao.module.pay.api.fund.dto.PayFundMerchantRegisterReqDTO;
import cn.iocoder.yudao.module.pay.dal.dataobject.app.PayAppDO;
import cn.iocoder.yudao.module.pay.dal.dataobject.channel.PayChannelDO;
import cn.iocoder.yudao.module.pay.dal.dataobject.payfund.yeepay.PayYeepayMerchantAccountDO;
import cn.iocoder.yudao.module.pay.dal.mysql.payfund.yeepay.PayYeepayMerchantAccountMapper;
import cn.iocoder.yudao.module.pay.dal.redis.no.PayNoRedisDAO;
import cn.iocoder.yudao.module.pay.enums.PayChannelEnum;
import cn.iocoder.yudao.module.pay.framework.pay.core.client.impl.yeepay.YeepayPayClient;
import cn.iocoder.yudao.module.pay.framework.pay.core.client.impl.yeepay.YeepayPayClientConfig;
import cn.iocoder.yudao.module.pay.service.app.PayAppService;
import cn.iocoder.yudao.module.pay.service.channel.PayChannelService;
import jakarta.annotation.Resource;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;

import javax.sql.DataSource;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** 入网账户必须在网络调用前提交，且多实例并发只能领取一次提交租约。 */
@Import(PayFundServiceImpl.class)
class PayFundMerchantTransactionTest extends BaseDbUnitTest {

    @Resource private PayFundServiceImpl service;
    @Resource private PayYeepayMerchantAccountMapper mapper;
    @Resource private DataSource dataSource;
    @Resource private PlatformTransactionManager transactionManager;
    @MockitoBean private PayAppService appService;
    @MockitoBean private PayChannelService channelService;
    @MockitoBean private PayNoRedisDAO noRedisDAO;
    private YeepayPayClient client;

    @BeforeEach
    void setup() {
        TenantContextHolder.setTenantId(0L);
        PayChannelDO channel = new PayChannelDO().setId(10L).setAppId(1L)
                .setCode(PayChannelEnum.YEEPAY_WX_LITE.getCode()).setConfig(new YeepayPayClientConfig());
        client = mock(YeepayPayClient.class);
        when(appService.validPayApp("mall")).thenReturn(new PayAppDO().setId(1L));
        when(channelService.validPayChannel(1L, PayChannelEnum.YEEPAY_WX_LITE.getCode())).thenReturn(channel);
        when(channelService.getPayClient(10L)).thenReturn(client);
        when(client.buildMerchantRegisterParams(anyMap(), anyString(), anyString())).thenAnswer(invocation ->
                Map.of("requestNo", invocation.getArgument(1), "businessRole", invocation.getArgument(2)));
    }

    @AfterEach
    void cleanup() {
        TenantContextHolder.clear();
    }

    @Test
    void firstRequestIsCommittedAndCallerTransactionIsSuspendedBeforeNetwork() {
        when(client.registerMerchant(anyMap())).thenAnswer(invocation -> {
            assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isFalse();
            ExecutorService reader = Executors.newSingleThreadExecutor();
            try {
                PayYeepayMerchantAccountDO committed = reader.submit(() -> mapper.selectByAppIdAndMerchantId(1L, 7L))
                        .get(5, TimeUnit.SECONDS);
                assertThat(committed).isNotNull();
                assertThat(committed.getRequestNo()).isEqualTo("MERCHANT-7");
                assertThat(committed.getLastRequestTime()).isNotNull();
                assertThat(committed.getMaterialsVersion()).isEqualTo(1);
            } finally {
                reader.shutdownNow();
            }
            return Map.of("applicationStatus", "REVIEWING", "applicationNo", "APP-7");
        });

        new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
            service.registerMerchant(request(7L, "MERCHANT-7"));
            status.setRollbackOnly();
        });

        assertThat(mapper.selectByAppIdAndMerchantId(1L, 7L).getApplicationNo()).isEqualTo("APP-7");
    }

    @Test
    void concurrentSubmissionsShareOneCommittedAccountAndSendOnlyOnce() throws Exception {
        CountDownLatch networkStarted = new CountDownLatch(1);
        CountDownLatch releaseNetwork = new CountDownLatch(1);
        when(client.registerMerchant(anyMap())).thenAnswer(invocation -> {
            networkStarted.countDown();
            assertThat(releaseNetwork.await(5, TimeUnit.SECONDS)).isTrue();
            return Map.of("applicationStatus", "REVIEWING");
        });
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            Future<PayFundMerchantAccountRespDTO> first = executor.submit(() -> register(7L, "MERCHANT-7"));
            assertThat(networkStarted.await(5, TimeUnit.SECONDS)).isTrue();
            Future<PayFundMerchantAccountRespDTO> second = executor.submit(() -> register(7L, "DIFFERENT-REQUEST"));
            PayFundMerchantAccountRespDTO pending = second.get(3, TimeUnit.SECONDS);
            assertThat(pending.getRequestNo()).isEqualTo("MERCHANT-7");
            assertThat(pending.getLastError()).isEqualTo("PAY_ONBOARDING_IN_PROGRESS");
            assertThat(service.getMerchant("mall", 7L).getLastError()).isEqualTo("PAY_ONBOARDING_IN_PROGRESS");
            verify(client, never()).queryMerchant(anyString());
            releaseNetwork.countDown();
            assertThat(first.get(5, TimeUnit.SECONDS).getRequestNo()).isEqualTo("MERCHANT-7");
        } finally {
            releaseNetwork.countDown();
            executor.shutdownNow();
        }
        assertThat(jdbc().queryForObject("SELECT COUNT(*) FROM pay_yeepay_merchant_account", Integer.class)).isEqualTo(1);
        verify(client, times(1)).registerMerchant(anyMap());
    }

    @Test
    void failedNetworkPreservesAccountAndRetryUsesOriginalRequestNumber() {
        when(client.registerMerchant(anyMap())).thenThrow(new IllegalStateException("raw-sensitive-body"))
                .thenReturn(Map.of("applicationStatus", "COMPLETED", "merchantNo", "YEE-7"));

        PayFundMerchantAccountRespDTO failed = service.registerMerchant(request(7L, "MERCHANT-7"));
        assertThat(failed.getLastError()).doesNotContain("raw-sensitive-body");
        PayFundMerchantAccountRespDTO retry = service.registerMerchant(request(7L, "REPLACEMENT"));

        assertThat(retry.getId()).isEqualTo(failed.getId());
        assertThat(retry.getRequestNo()).isEqualTo("MERCHANT-7");
        assertThat(retry.getOnboardingStatus()).isEqualTo(100);
        verify(client, times(2)).buildMerchantRegisterParams(anyMap(), eq("MERCHANT-7"), eq("SHARE_MERCHANT"));
    }

    @Test
    void separatePendingMerchantsCanCoexistWithoutAssignedChannelMerchantNumbers() {
        when(client.registerMerchant(anyMap())).thenReturn(Map.of("applicationStatus", "REVIEWING"));
        service.registerMerchant(request(7L, "MERCHANT-7"));
        service.registerMerchant(request(8L, "MERCHANT-8"));
        assertThat(jdbc().queryForObject("SELECT COUNT(*) FROM pay_yeepay_merchant_account", Integer.class)).isEqualTo(2);
    }

    @Test
    void queryDoesNotHoldTransactionAndCannotRegressCompletedCallback() {
        when(client.registerMerchant(anyMap())).thenReturn(Map.of("applicationStatus", "REVIEWING"));
        service.registerMerchant(request(7L, "MERCHANT-7"));
        when(client.queryMerchant("MERCHANT-7")).thenAnswer(invocation -> {
            assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isFalse();
            service.handleMerchantOnboarding(Map.of("requestNo", "MERCHANT-7", "applicationStatus", "COMPLETED",
                    "merchantNo", "YEE-7"));
            return Map.of("applicationStatus", "REVIEWING");
        });

        PayFundMerchantAccountRespDTO response = new TransactionTemplate(transactionManager)
                .execute(status -> service.getMerchant("mall", 7L));

        assertThat(response.getOnboardingStatus()).isEqualTo(100);
        assertThat(response.getMerchantNo()).isEqualTo("YEE-7");
    }

    @Test
    void expiredLeaseRetryIgnoresEarlierLateResponse() {
        AtomicInteger calls = new AtomicInteger();
        when(client.registerMerchant(anyMap())).thenAnswer(invocation -> {
            if (calls.incrementAndGet() == 1) {
                jdbc().update("UPDATE pay_yeepay_merchant_account SET submission_expire_time = ? WHERE merchant_id = 7",
                        LocalDateTime.now().minusSeconds(1));
                service.registerMerchant(request(7L, "MERCHANT-7"));
                return Map.of("applicationStatus", "REVIEWING", "applicationNo", "OLD-APPLICATION");
            }
            return Map.of("applicationStatus", "AGREEMENT_SIGNING", "applicationNo", "NEW-APPLICATION");
        });

        PayFundMerchantAccountRespDTO response = service.registerMerchant(request(7L, "MERCHANT-7"));

        assertThat(response.getOnboardingStatus()).isEqualTo(40);
        assertThat(mapper.selectByAppIdAndMerchantId(1L, 7L).getApplicationNo()).isEqualTo("NEW-APPLICATION");
        verify(client, times(2)).registerMerchant(anyMap());
    }

    private PayFundMerchantAccountRespDTO register(Long merchantId, String requestNo) {
        TenantContextHolder.setTenantId(0L);
        try {
            return service.registerMerchant(request(merchantId, requestNo));
        } finally {
            TenantContextHolder.clear();
        }
    }

    private PayFundMerchantRegisterReqDTO request(Long merchantId, String requestNo) {
        return new PayFundMerchantRegisterReqDTO().setAppKey("mall").setMerchantId(merchantId).setRequestNo(requestNo)
                .setMerchantName("商家").setBusinessRole("SHARE_MERCHANT")
                .setMaterials(Map.of("materialsRef", "application:1:materials:1", "materialsVersion", 1));
    }

    private JdbcTemplate jdbc() {
        return new JdbcTemplate(dataSource);
    }
}
