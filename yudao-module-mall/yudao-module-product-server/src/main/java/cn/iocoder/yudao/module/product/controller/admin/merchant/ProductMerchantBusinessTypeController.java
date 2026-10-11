package cn.iocoder.yudao.module.product.controller.admin.merchant;

import cn.iocoder.yudao.framework.common.pojo.CommonResult;
import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.module.product.controller.admin.merchant.vo.ProductMerchantBusinessTypePageReqVO;
import cn.iocoder.yudao.module.product.controller.admin.merchant.vo.ProductMerchantBusinessTypeRespVO;
import cn.iocoder.yudao.module.product.controller.admin.merchant.vo.ProductMerchantBusinessTypeSaveReqVO;
import cn.iocoder.yudao.module.product.service.merchant.ProductMerchantBusinessTypeService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Resource;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import static cn.iocoder.yudao.framework.common.pojo.CommonResult.success;

@Tag(name = "管理后台 - 商家经营类型")
@RestController("adminProductMerchantBusinessTypeController")
@RequestMapping("/product/merchant/business-type")
@Validated
public class ProductMerchantBusinessTypeController {

    @Resource
    private ProductMerchantBusinessTypeService businessTypeService;

    @GetMapping("/page")
    @Operation(summary = "获取经营类型分页")
    @PreAuthorize("@ss.hasPermission('product:merchant:update')")
    public CommonResult<PageResult<ProductMerchantBusinessTypeRespVO>> getPage(@Valid ProductMerchantBusinessTypePageReqVO reqVO) {
        return success(businessTypeService.getPage(reqVO));
    }

    @PostMapping("/save")
    @Operation(summary = "保存经营类型及资质要求")
    @PreAuthorize("@ss.hasPermission('product:merchant:update')")
    public CommonResult<Long> save(@Valid @RequestBody ProductMerchantBusinessTypeSaveReqVO reqVO) {
        return success(businessTypeService.save(reqVO));
    }

    @DeleteMapping("/delete")
    @Operation(summary = "删除经营类型")
    @PreAuthorize("@ss.hasPermission('product:merchant:update')")
    public CommonResult<Boolean> delete(@RequestParam("id") Long id) {
        businessTypeService.delete(id);
        return success(true);
    }
}
