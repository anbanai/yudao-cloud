package cn.iocoder.yudao.module.pay.controller.admin.fund;

import cn.iocoder.yudao.framework.common.pojo.CommonResult;
import cn.iocoder.yudao.framework.apilog.core.annotation.ApiAccessLog;
import cn.iocoder.yudao.module.pay.api.fund.dto.*;
import cn.iocoder.yudao.module.pay.service.payfund.PayFundService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Resource;
import cn.iocoder.yudao.module.pay.service.payfund.PayFundOperationsService;
import cn.iocoder.yudao.framework.common.pojo.PageParam;
import cn.iocoder.yudao.framework.common.pojo.PageResult;
import static cn.iocoder.yudao.framework.security.core.util.SecurityFrameworkUtils.getLoginUserId;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import static cn.iocoder.yudao.framework.common.pojo.CommonResult.success;

/**
 * 管理后台资金处理入口。
 *
 * <p>后台只提交商家资料引用和人工重试指令，易宝密钥、签名和渠道报文仍由支付资金服务持有。</p>
 */
@Tag(name = "管理后台 - 易宝资金")
@RestController
@RequestMapping("/pay/fund")
@Validated
public class PayFundController {

    @Resource
    private PayFundService payFundService;
    @Resource private PayFundOperationsService operations;

    @PostMapping("/merchant/register")
    @Operation(summary = "提交或补交易宝商家入网资料")
    @PreAuthorize("@ss.hasPermission('pay:fund:merchant:update')")
    @ApiAccessLog(requestEnable = false, responseEnable = false)
    public CommonResult<PayFundMerchantAccountRespDTO> registerMerchant(
            @Valid @RequestBody PayFundMerchantRegisterReqDTO reqDTO) {
        try {
            return success(payFundService.registerMerchant(reqDTO));
        } catch (RuntimeException ex) {
            // 渠道/参数异常可能携带资料，不能交给记录 body 的全局错误处理。
            return CommonResult.error(500, "商家入网请求失败，请检查资料和支付配置后重试");
        }
    }

    @GetMapping("/merchant/get")
    @Operation(summary = "查询易宝商家入网状态")
    @PreAuthorize("@ss.hasPermission('pay:fund:merchant:query')")
    @ApiAccessLog(responseEnable = false)
    public CommonResult<PayFundMerchantAccountRespDTO> getMerchant(
            @RequestParam("appKey") String appKey, @RequestParam("merchantId") Long merchantId) {
        return success(payFundService.getMerchant(appKey, merchantId));
    }

    @PostMapping("/divide/retry")
    @Operation(summary = "人工重试易宝分账")
    @PreAuthorize("@ss.hasPermission('pay:fund:divide:retry')")
    public CommonResult<Boolean> retryDivide(@Valid @RequestBody PayFundRetryReqDTO command) {
        command.setOperatorId(getLoginUserId());
        operations.retryDivide(command);
        return success(true);
    }

    @PostMapping("/refund/retry")
    @Operation(summary = "人工重试易宝退款或资金归还")
    @PreAuthorize("@ss.hasPermission('pay:fund:refund:retry')")
    public CommonResult<Boolean> retryRefund(@Valid @RequestBody PayFundRetryReqDTO command) {
        command.setOperatorId(getLoginUserId());
        operations.retryRefund(command);
        return success(true);
    }
    @GetMapping("/order/get")
    @PreAuthorize("@ss.hasPermission('pay:fund:query')")
    public CommonResult<PayFundStatusRespDTO> getStatus(@RequestParam("payOrderId") Long payOrderId) {
        return success(operations.getStatus(payOrderId));
    }
    @GetMapping("/order/page")
    @PreAuthorize("@ss.hasPermission('pay:fund:query')")
    public CommonResult<PageResult<PayFundStatusRespDTO>> getPage(@Valid PageParam page) {
        return success(operations.getPage(page));
    }
    @GetMapping("/refund/get")
    @PreAuthorize("@ss.hasPermission('pay:fund:query')")
    public CommonResult<PayRefundFundStatusRespDTO> getRefundStatus(@RequestParam("refundId") Long refundId) {
        return success(operations.getRefundStatus(refundId));
    }
}
