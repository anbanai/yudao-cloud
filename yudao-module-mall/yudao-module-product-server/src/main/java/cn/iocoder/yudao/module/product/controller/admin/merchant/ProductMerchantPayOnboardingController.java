package cn.iocoder.yudao.module.product.controller.admin.merchant;

import cn.iocoder.yudao.framework.apilog.core.annotation.ApiAccessLog;
import cn.iocoder.yudao.framework.common.pojo.CommonResult;
import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.module.product.controller.admin.merchant.vo.ProductMerchantPayMaterialsReqVO;
import cn.iocoder.yudao.module.product.controller.admin.merchant.vo.ProductMerchantPayOnboardingRespVO;
import cn.iocoder.yudao.module.product.controller.admin.merchant.vo.ProductMerchantPayOnboardingPageReqVO;
import cn.iocoder.yudao.module.product.service.merchant.ProductMerchantPayOnboardingService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Resource;
import jakarta.validation.ConstraintViolationException;
import jakarta.validation.Valid;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.multipart.support.MissingServletRequestPartException;

import static cn.iocoder.yudao.framework.common.pojo.CommonResult.success;
import static cn.iocoder.yudao.module.product.enums.ErrorCodeConstants.MERCHANT_PAY_MATERIALS_INVALID;

@RestController
@RequestMapping("/product/merchant/pay-onboarding")
@Tag(name = "管理后台 - 商家支付入网资料")
public class ProductMerchantPayOnboardingController {

    @Resource
    private ProductMerchantPayOnboardingService onboardingService;

    @PostMapping("/enqueue")
    @Operation(summary = "为现有已批准商户创建或获取待补料任务")
    @PreAuthorize("@ss.hasPermission('pay:fund:merchant:update')")
    public CommonResult<Long> enqueue(@RequestParam("merchantId") Long merchantId) {
        return success(onboardingService.enqueueApprovedMerchant(merchantId));
    }

    @PostMapping("/private-file")
    @Operation(summary = "上传支付入网私有文件，返回文件编号")
    @PreAuthorize("@ss.hasPermission('pay:fund:merchant:update')")
    @ApiAccessLog(requestEnable = false, responseEnable = false)
    public CommonResult<Long> uploadPrivateFile(@RequestParam("onboardingId") Long onboardingId,
                                                @RequestPart("file") MultipartFile file) {
        try {
            return success(onboardingService.uploadPrivateFile(onboardingId, file));
        } catch (RuntimeException ex) {
            return CommonResult.error(MERCHANT_PAY_MATERIALS_INVALID);
        }
    }

    @PostMapping("/materials")
    @Operation(summary = "提交已批准商户的支付入网资料")
    @PreAuthorize("@ss.hasPermission('pay:fund:merchant:update')")
    @ApiAccessLog(requestEnable = false, responseEnable = false)
    public CommonResult<Boolean> saveMaterials(@Valid @RequestBody ProductMerchantPayMaterialsReqVO reqVO) {
        try {
            onboardingService.saveMaterials(reqVO);
            return success(true);
        } catch (RuntimeException ex) {
            // 不让 SDK、RPC 或持久化异常进入记录原始 body 的全局错误日志。
            return CommonResult.error(MERCHANT_PAY_MATERIALS_INVALID);
        }
    }

    @GetMapping("/get")
    @Operation(summary = "查询支付入网状态、缺料清单及签约待办")
    @PreAuthorize("@ss.hasPermission('pay:fund:merchant:query')")
    @ApiAccessLog(responseEnable = false)
    public CommonResult<ProductMerchantPayOnboardingRespVO> get(@RequestParam("onboardingId") Long onboardingId) {
        return success(onboardingService.get(onboardingId));
    }

    @GetMapping("/page")
    @Operation(summary = "分页查询支付入网任务，不回显敏感资料或签约链接")
    @PreAuthorize("@ss.hasPermission('pay:fund:merchant:query')")
    public CommonResult<PageResult<ProductMerchantPayOnboardingRespVO>> getPage(
            @Valid ProductMerchantPayOnboardingPageReqVO reqVO) {
        return success(onboardingService.getPage(reqVO));
    }

    @ExceptionHandler({HttpMessageNotReadableException.class, MethodArgumentNotValidException.class,
            ConstraintViolationException.class, MaxUploadSizeExceededException.class, MissingServletRequestPartException.class})
    public CommonResult<?> invalidMaterialBody(Exception ex) {
        // 反序列化/校验发生在方法调用前，默认异常处理会记录 rejectedValue/原始请求。
        return CommonResult.error(MERCHANT_PAY_MATERIALS_INVALID);
    }
}
