package cn.iocoder.yudao.module.pay.controller.admin.fund;

import cn.iocoder.yudao.framework.apilog.core.annotation.ApiAccessLog;
import cn.iocoder.yudao.module.pay.api.fund.PayFundApiImpl;
import cn.iocoder.yudao.module.pay.api.fund.dto.PayFundMerchantRegisterReqDTO;
import cn.iocoder.yudao.module.pay.service.payfund.PayFundService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class PayFundMerchantPrivacyTest {

    private PayFundService fundService;
    private PayFundController controller;
    private MockMvc mvc;

    @BeforeEach
    void setup() {
        fundService = mock(PayFundService.class);
        controller = new PayFundController();
        ReflectionTestUtils.setField(controller, "payFundService", fundService);
        mvc = MockMvcBuilders.standaloneSetup(controller)
                .setControllerAdvice(new PayFundMaterialExceptionAdvice()).build();
    }

    @Test
    void runtimeFailureDoesNotReachGenericRequestBodyErrorLogging() throws Exception {
        when(fundService.registerMerchant(any())).thenThrow(new IllegalStateException("secret-id-card"));

        mvc.perform(post("/pay/fund/merchant/register").contentType("application/json")
                        .content("{\"appKey\":\"mall\",\"merchantId\":1,\"materials\":{\"idCard\":\"secret-id-card\"}}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.code").value(500))
                .andExpect(jsonPath("$.msg").value("商家入网请求失败，请检查资料和支付配置后重试"))
                .andExpect(result -> assertThat(result.getResolvedException()).isNull());
    }

    @Test
    void malformedAndValidationFailuresUseLocalSanitizedHandler() throws Exception {
        mvc.perform(post("/pay/fund/merchant/register").contentType("application/json")
                        .content("{\"appKey\":\"mall\",\"merchantId\":\"secret-id-card\",\"materials\":{}}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.code").value(400))
                .andExpect(jsonPath("$.msg").value("商家入网请求参数无效"));
        mvc.perform(post("/pay/fund/merchant/register").contentType("application/json")
                        .content("{\"appKey\":\"mall\",\"materials\":{\"idCard\":\"secret-id-card\"}}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.code").value(400))
                .andExpect(jsonPath("$.msg").value("商家入网请求参数无效"));
        verifyNoInteractions(fundService);
    }

    @Test
    void adminAndRpcBodiesAreExcludedFromAccessLogsAndDtoToString() throws Exception {
        ApiAccessLog admin = PayFundController.class.getMethod("registerMerchant", PayFundMerchantRegisterReqDTO.class)
                .getAnnotation(ApiAccessLog.class);
        ApiAccessLog rpc = PayFundApiImpl.class.getMethod("registerMerchant", PayFundMerchantRegisterReqDTO.class)
                .getAnnotation(ApiAccessLog.class);
        assertThat(admin.requestEnable()).isFalse();
        assertThat(admin.responseEnable()).isFalse();
        assertThat(rpc.requestEnable()).isFalse();
        assertThat(new PayFundMerchantRegisterReqDTO().setAppKey("mall").setMerchantId(1L)
                .setMaterials(Map.of("idCard", "secret-id-card")).toString()).doesNotContain("secret-id-card");
        PayFundApiImpl api = new PayFundApiImpl();
        ReflectionTestUtils.setField(api, "payFundService", fundService);
        when(fundService.registerMerchant(any())).thenThrow(new IllegalStateException("secret-id-card"));
        assertThat(api.registerMerchant(new PayFundMerchantRegisterReqDTO()).getMsg()).doesNotContain("secret-id-card");
    }
}
