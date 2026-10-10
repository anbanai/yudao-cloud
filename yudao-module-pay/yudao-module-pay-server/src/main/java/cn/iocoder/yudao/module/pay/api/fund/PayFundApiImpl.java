package cn.iocoder.yudao.module.pay.api.fund;

import cn.iocoder.yudao.framework.common.pojo.CommonResult;
import cn.iocoder.yudao.framework.apilog.core.annotation.ApiAccessLog;
import cn.iocoder.yudao.module.pay.api.fund.dto.*;
import cn.iocoder.yudao.module.pay.service.payfund.PayFundService;
import jakarta.annotation.Resource;
import cn.iocoder.yudao.module.pay.service.payfund.PayFundOperationsService;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.RestController;

import static cn.iocoder.yudao.framework.common.pojo.CommonResult.success;

/** 支付资金 API 实现。 */
@RestController
@Validated
public class PayFundApiImpl implements PayFundApi {

    @Resource
    private PayFundService payFundService;
    @Resource private PayFundOperationsService operations;

    @Override
    @ApiAccessLog(requestEnable = false, responseEnable = false)
    public CommonResult<PayFundMerchantAccountRespDTO> registerMerchant(PayFundMerchantRegisterReqDTO reqDTO) {
        try {
            return success(payFundService.registerMerchant(reqDTO));
        } catch (RuntimeException ex) {
            return CommonResult.error(500, "商家入网请求失败，请检查资料和支付配置后重试");
        }
    }

    @Override
    @ApiAccessLog(responseEnable = false)
    public CommonResult<PayFundMerchantAccountRespDTO> getMerchant(String appKey, Long merchantId) {
        return success(payFundService.getMerchant(appKey, merchantId));
    }

    @Override
    public CommonResult<Boolean> retryDivide(PayFundRetryReqDTO command) {
        operations.retryDivide(command);
        return success(true);
    }

    @Override
    public CommonResult<Boolean> retryRefund(PayFundRetryReqDTO command) {
        operations.retryRefund(command);
        return success(true);
    }
    @Override
    public CommonResult<PayFundStatusRespDTO> getStatus(Long payOrderId) { return success(operations.getStatus(payOrderId)); }
    @Override
    public CommonResult<PayRefundFundStatusRespDTO> getRefundStatus(Long refundId) { return success(operations.getRefundStatus(refundId)); }
}
