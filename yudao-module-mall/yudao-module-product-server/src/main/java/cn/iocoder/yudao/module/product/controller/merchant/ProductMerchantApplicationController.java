package cn.iocoder.yudao.module.product.controller.merchant;

import cn.iocoder.yudao.framework.common.pojo.CommonResult;
import cn.iocoder.yudao.framework.common.util.servlet.ServletUtils;
import cn.iocoder.yudao.module.product.controller.admin.merchant.vo.ProductMerchantApplicationDetailRespVO;
import cn.iocoder.yudao.module.product.controller.admin.merchant.vo.ProductMerchantApplicationDraftReqVO;
import cn.iocoder.yudao.module.product.controller.admin.merchant.vo.ProductMerchantApplicationSubmitReqVO;
import cn.iocoder.yudao.module.product.controller.admin.merchant.vo.ProductMerchantApplicationTermsRespVO;
import cn.iocoder.yudao.module.product.framework.merchant.config.ProductMerchantApplicationProperties;
import cn.iocoder.yudao.module.product.service.merchant.ProductMerchantApplicationService;
import cn.iocoder.yudao.module.product.framework.merchant.MerchantSecurityService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Resource;
import jakarta.validation.Valid;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

import static cn.iocoder.yudao.framework.common.pojo.CommonResult.success;

@Tag(name = "商家端 - 入驻申请")
@RestController
@RequestMapping("/product/merchant/application")
@Validated
public class ProductMerchantApplicationController {

    @Resource
    private ProductMerchantApplicationService applicationService;
    @Resource
    private MerchantSecurityService merchantSecurityService;
    @Resource
    private ProductMerchantApplicationProperties applicationProperties;

    @GetMapping("/terms")
    @Operation(summary = "获取当前商家入驻协议")
    public CommonResult<ProductMerchantApplicationTermsRespVO> getTerms() {
        return success(new ProductMerchantApplicationTermsRespVO()
                .setAvailable(applicationProperties.isConfigured())
                .setAgreementVersion(applicationProperties.getAgreementVersion())
                .setAgreementUrl(applicationProperties.getAgreementUrl())
                .setPrivacyPolicyVersion(applicationProperties.getPrivacyPolicyVersion())
                .setPrivacyPolicyUrl(applicationProperties.getPrivacyPolicyUrl()));
    }

    @GetMapping("/mine")
    @Operation(summary = "获取我的入驻申请")
    public CommonResult<ProductMerchantApplicationDetailRespVO> getMine() {
        return success(applicationService.getMine(merchantSecurityService.requireMerchantAccountId()));
    }

    @PostMapping("/draft")
    @Operation(summary = "保存入驻申请草稿")
    public CommonResult<Long> saveDraft(@Valid @RequestBody ProductMerchantApplicationDraftReqVO reqVO) {
        return success(applicationService.saveDraft(merchantSecurityService.requireMerchantAccountId(), reqVO));
    }

    @PostMapping("/submit")
    @Operation(summary = "提交入驻申请")
    public CommonResult<Boolean> submit(@Valid @RequestBody ProductMerchantApplicationSubmitReqVO reqVO,
                                        HttpServletRequest request) {
        applicationService.submit(merchantSecurityService.requireMerchantAccountId(), reqVO,
                ServletUtils.getClientIP(request));
        return success(true);
    }

    @GetMapping("/history")
    @Operation(summary = "获取申请历史版本")
    public CommonResult<List<ProductMerchantApplicationDetailRespVO>> getHistory() {
        return success(applicationService.getHistory(merchantSecurityService.requireMerchantAccountId()));
    }
}
