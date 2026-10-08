package cn.iocoder.yudao.module.product.controller.admin.merchant;

import cn.iocoder.yudao.framework.common.pojo.CommonResult;
import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.module.product.controller.admin.merchant.vo.*;
import cn.iocoder.yudao.module.product.convert.merchant.ProductMerchantConvert;
import cn.iocoder.yudao.module.product.dal.dataobject.merchant.ProductMerchantDO;
import cn.iocoder.yudao.module.product.service.merchant.ProductMerchantService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Resource;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import static cn.iocoder.yudao.framework.common.pojo.CommonResult.success;

@Tag(name = "管理后台 - 商家")
@RestController
@RequestMapping("/product/merchant")
@Validated
public class ProductMerchantController {

    @Resource
    private ProductMerchantService merchantService;

    @PostMapping("/create")
    @Operation(summary = "创建商家")
    @PreAuthorize("@ss.hasPermission('product:merchant:create')")
    public CommonResult<Long> createMerchant(@Valid @RequestBody ProductMerchantSaveReqVO reqVO) {
        return success(merchantService.createMerchant(reqVO));
    }

    @PutMapping("/update")
    @Operation(summary = "更新商家")
    @PreAuthorize("@ss.hasPermission('product:merchant:update')")
    public CommonResult<Boolean> updateMerchant(@Valid @RequestBody ProductMerchantSaveReqVO reqVO) {
        merchantService.updateMerchant(reqVO);
        return success(true);
    }

    @GetMapping("/get")
    @Operation(summary = "获得商家")
    @PreAuthorize("@ss.hasPermission('product:merchant:query')")
    public CommonResult<ProductMerchantRespVO> getMerchant(@RequestParam("id") Long id) {
        return success(ProductMerchantConvert.INSTANCE.convert(merchantService.getMerchant(id)));
    }

    @GetMapping("/page")
    @Operation(summary = "获得商家分页")
    @PreAuthorize("@ss.hasPermission('product:merchant:query')")
    public CommonResult<PageResult<ProductMerchantRespVO>> getMerchantPage(@Valid ProductMerchantPageReqVO reqVO) {
        return success(ProductMerchantConvert.INSTANCE.convertPage(merchantService.getMerchantPage(reqVO)));
    }

    @PostMapping("/submit-review")
    @Operation(summary = "提交商家审核")
    @PreAuthorize("@ss.hasPermission('product:merchant:update')")
    public CommonResult<Boolean> submitReview(@RequestParam("id") Long id) {
        merchantService.submitReview(id);
        return success(true);
    }

    @PostMapping("/approve")
    @Operation(summary = "审核通过商家")
    @PreAuthorize("@ss.hasPermission('product:merchant:review')")
    public CommonResult<Boolean> approve(@RequestParam("id") Long id) {
        merchantService.approveMerchant(id, getLoginUserId());
        return success(true);
    }

    @PostMapping("/reject")
    @Operation(summary = "驳回商家")
    @PreAuthorize("@ss.hasPermission('product:merchant:review')")
    public CommonResult<Boolean> reject(@Valid @RequestBody ProductMerchantReviewReqVO reqVO) {
        merchantService.rejectMerchant(reqVO, getLoginUserId());
        return success(true);
    }

    @PostMapping("/freeze")
    @Operation(summary = "冻结商家")
    @PreAuthorize("@ss.hasPermission('product:merchant:review')")
    public CommonResult<Boolean> freeze(@RequestParam("id") Long id) {
        merchantService.freezeMerchant(id, getLoginUserId());
        return success(true);
    }

    @PostMapping("/unfreeze")
    @Operation(summary = "解冻商家")
    @PreAuthorize("@ss.hasPermission('product:merchant:review')")
    public CommonResult<Boolean> unfreeze(@RequestParam("id") Long id) {
        merchantService.unfreezeMerchant(id, getLoginUserId());
        return success(true);
    }

    private Long getLoginUserId() {
        return cn.iocoder.yudao.framework.security.core.util.SecurityFrameworkUtils.getLoginUserId();
    }
}
