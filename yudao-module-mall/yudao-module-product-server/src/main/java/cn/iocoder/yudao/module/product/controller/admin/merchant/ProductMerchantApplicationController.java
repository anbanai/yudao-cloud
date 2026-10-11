package cn.iocoder.yudao.module.product.controller.admin.merchant;

import cn.iocoder.yudao.framework.common.pojo.CommonResult;
import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.framework.security.core.util.SecurityFrameworkUtils;
import cn.iocoder.yudao.module.product.controller.admin.merchant.vo.ProductMerchantApplicationDetailRespVO;
import cn.iocoder.yudao.module.product.controller.admin.merchant.vo.ProductMerchantLegacyBindReqVO;
import cn.iocoder.yudao.module.product.controller.admin.merchant.vo.ProductMerchantApplicationPageReqVO;
import cn.iocoder.yudao.module.product.controller.admin.merchant.vo.ProductMerchantApplicationRejectReqVO;
import cn.iocoder.yudao.module.product.controller.admin.merchant.vo.ProductMerchantReviewRecordRespVO;
import cn.iocoder.yudao.module.product.service.merchant.ProductMerchantApplicationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Resource;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

import static cn.iocoder.yudao.framework.common.pojo.CommonResult.success;

@Tag(name = "管理后台 - 商家申请审核")
@RestController("adminProductMerchantApplicationController")
@RequestMapping("/product/merchant/application")
@Validated
public class ProductMerchantApplicationController {

    @Resource
    private ProductMerchantApplicationService applicationService;

    @GetMapping("/page")
    @Operation(summary = "获取商家申请分页")
    @PreAuthorize("@ss.hasPermission('product:merchant:query')")
    public CommonResult<PageResult<ProductMerchantApplicationDetailRespVO>> getPage(
            @Valid ProductMerchantApplicationPageReqVO reqVO) {
        return success(applicationService.getPage(reqVO));
    }

    @GetMapping("/get")
    @Operation(summary = "获取商家申请详情")
    @PreAuthorize("@ss.hasPermission('product:merchant:query')")
    public CommonResult<ProductMerchantApplicationDetailRespVO> get(@RequestParam("id") Long id) {
        return success(applicationService.get(id));
    }

    @GetMapping("/history")
    @Operation(summary = "获取商家申请版本列表")
    @PreAuthorize("@ss.hasPermission('product:merchant:query')")
    public CommonResult<List<ProductMerchantApplicationDetailRespVO>> getHistory(@RequestParam("merchantId") Long merchantId) {
        return success(applicationService.getMerchantHistory(merchantId));
    }

    @PostMapping("/bind-legacy-account")
    @Operation(summary = "关联存量商家与自助注册的申请账号")
    @PreAuthorize("@ss.hasPermission('product:merchant:review')")
    public CommonResult<Boolean> bindLegacyAccount(@Valid @RequestBody ProductMerchantLegacyBindReqVO reqVO) {
        applicationService.bindLegacyAccount(reqVO.getMerchantId(), reqVO.getAccountId(), SecurityFrameworkUtils.getLoginUserId());
        return success(true);
    }

    @PostMapping("/approve")
    @Operation(summary = "审核通过商家申请")
    @PreAuthorize("@ss.hasPermission('product:merchant:review')")
    public CommonResult<Boolean> approve(@RequestParam("id") Long id) {
        applicationService.approve(id, SecurityFrameworkUtils.getLoginUserId());
        return success(true);
    }

    @PostMapping("/reject")
    @Operation(summary = "驳回商家申请")
    @PreAuthorize("@ss.hasPermission('product:merchant:review')")
    public CommonResult<Boolean> reject(@Valid @RequestBody ProductMerchantApplicationRejectReqVO reqVO) {
        applicationService.reject(reqVO.getApplicationId(), reqVO.getReason(), SecurityFrameworkUtils.getLoginUserId());
        return success(true);
    }

    @GetMapping("/review-records")
    @Operation(summary = "获取商家审核记录")
    @PreAuthorize("@ss.hasPermission('product:merchant:query')")
    public CommonResult<List<ProductMerchantReviewRecordRespVO>> getReviewRecords(@RequestParam("merchantId") Long merchantId) {
        return success(applicationService.getReviewRecords(merchantId));
    }
}
