package cn.iocoder.yudao.module.pay.api.fund;

import cn.iocoder.yudao.framework.common.pojo.CommonResult;
import cn.iocoder.yudao.module.pay.api.fund.dto.*;
import cn.iocoder.yudao.module.pay.enums.ApiConstants;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;

/** 支付资金能力 API，商家模块通过此接口发起和查询入网。 */
@FeignClient(name = ApiConstants.NAME)
@Tag(name = "RPC 服务 - 支付资金")
public interface PayFundApi {

    String PREFIX = ApiConstants.PREFIX + "/fund";

    @PostMapping(PREFIX + "/merchant/register")
    @Operation(summary = "发起易宝商家入网")
    CommonResult<PayFundMerchantAccountRespDTO> registerMerchant(
            @Valid @RequestBody PayFundMerchantRegisterReqDTO reqDTO);

    @GetMapping(PREFIX + "/merchant/get")
    @Operation(summary = "查询易宝商家入网状态")
    @Parameter(name = "appKey", description = "应用标识", required = true)
    @Parameter(name = "merchantId", description = "平台商家编号", required = true)
    CommonResult<PayFundMerchantAccountRespDTO> getMerchant(
            @RequestParam("appKey") String appKey, @RequestParam("merchantId") Long merchantId);

    @PostMapping(PREFIX + "/divide/retry")
    @Operation(summary = "人工重试分账")
    CommonResult<Boolean> retryDivide(@Valid @RequestBody PayFundRetryReqDTO command);

    @PostMapping(PREFIX + "/refund/retry")
    @Operation(summary = "人工重试退款资金任务")
    CommonResult<Boolean> retryRefund(@Valid @RequestBody PayFundRetryReqDTO command);
    @GetMapping(PREFIX + "/order/get")
    CommonResult<PayFundStatusRespDTO> getStatus(@RequestParam("payOrderId") Long payOrderId);

    @GetMapping(PREFIX + "/refund/get")
    CommonResult<PayRefundFundStatusRespDTO> getRefundStatus(@RequestParam("refundId") Long refundId);
}
