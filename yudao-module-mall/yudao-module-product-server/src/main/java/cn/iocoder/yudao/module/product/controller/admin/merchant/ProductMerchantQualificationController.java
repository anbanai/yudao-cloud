package cn.iocoder.yudao.module.product.controller.admin.merchant;

import cn.iocoder.yudao.framework.common.pojo.CommonResult;
import cn.iocoder.yudao.module.product.controller.admin.merchant.vo.*;
import cn.iocoder.yudao.module.product.convert.merchant.ProductMerchantQualificationConvert;
import cn.iocoder.yudao.module.product.service.merchant.ProductMerchantQualificationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Resource;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import static cn.iocoder.yudao.framework.common.pojo.CommonResult.success;

@Tag(name = "管理后台 - 商家资质")
@RestController
@RequestMapping("/product/merchant-qualification")
@Validated
public class ProductMerchantQualificationController {

    @Resource
    private ProductMerchantQualificationService qualificationService;

    @PostMapping("/create")
    @Operation(summary = "创建商家资质")
    @PreAuthorize("@ss.hasPermission('product:merchant-qualification:create')")
    public CommonResult<Long> create(@Valid @RequestBody ProductMerchantQualificationSaveReqVO reqVO) {
        return success(qualificationService.createQualification(reqVO));
    }

    @PutMapping("/update")
    @Operation(summary = "更新商家资质")
    @PreAuthorize("@ss.hasPermission('product:merchant-qualification:update')")
    public CommonResult<Boolean> update(@Valid @RequestBody ProductMerchantQualificationSaveReqVO reqVO) {
        qualificationService.updateQualification(reqVO);
        return success(true);
    }

    @DeleteMapping("/delete")
    @Operation(summary = "删除商家资质")
    @PreAuthorize("@ss.hasPermission('product:merchant-qualification:delete')")
    public CommonResult<Boolean> delete(@RequestParam("id") Long id) {
        qualificationService.deleteQualification(id);
        return success(true);
    }

    @GetMapping("/list")
    @Operation(summary = "获得商家资质列表")
    @PreAuthorize("@ss.hasPermission('product:merchant-qualification:query')")
    public CommonResult<java.util.List<ProductMerchantQualificationRespVO>> getList(@RequestParam("merchantId") Long merchantId) {
        return success(ProductMerchantQualificationConvert.INSTANCE.convertList(qualificationService.getList(merchantId)));
    }

    @PostMapping("/approve")
    @Operation(summary = "审核通过商家资质")
    @PreAuthorize("@ss.hasPermission('product:merchant:review')")
    public CommonResult<Boolean> approve(@RequestParam("id") Long id) {
        qualificationService.approveQualification(id, getLoginUserId());
        return success(true);
    }

    @PostMapping("/reject")
    @Operation(summary = "驳回商家资质")
    @PreAuthorize("@ss.hasPermission('product:merchant:review')")
    public CommonResult<Boolean> reject(@RequestParam("id") Long id, @RequestParam(value = "reason", required = false) String reason) {
        qualificationService.rejectQualification(id, reason, getLoginUserId());
        return success(true);
    }

    private Long getLoginUserId() {
        return cn.iocoder.yudao.framework.security.core.util.SecurityFrameworkUtils.getLoginUserId();
    }
}
