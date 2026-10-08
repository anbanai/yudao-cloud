package cn.iocoder.yudao.module.product.controller.admin.brand;

import cn.iocoder.yudao.framework.common.enums.CommonStatusEnum;
import cn.iocoder.yudao.framework.common.pojo.CommonResult;
import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.module.product.controller.admin.brand.vo.*;
import cn.iocoder.yudao.module.product.convert.brand.ProductBrandConvert;
import cn.iocoder.yudao.module.product.dal.dataobject.brand.ProductBrandDO;
import cn.iocoder.yudao.module.product.dal.dataobject.merchant.ProductMerchantDO;
import cn.iocoder.yudao.module.product.service.brand.ProductBrandService;
import cn.iocoder.yudao.module.product.service.merchant.ProductMerchantService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Resource;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.Comparator;
import java.util.List;

import static cn.iocoder.yudao.framework.common.pojo.CommonResult.success;

@Tag(name = "管理后台 - 商品品牌")
@RestController
@RequestMapping("/product/brand")
@Validated
public class ProductBrandController {

    @Resource
    private ProductBrandService brandService;

    @Resource
    private ProductMerchantService merchantService;

    @PostMapping("/create")
    @Operation(summary = "创建品牌")
    @PreAuthorize("@ss.hasPermission('product:brand:create')")
    public CommonResult<Long> createBrand(@Valid @RequestBody ProductBrandCreateReqVO createReqVO) {
        return success(brandService.createBrand(createReqVO));
    }

    @PutMapping("/update")
    @Operation(summary = "更新品牌")
    @PreAuthorize("@ss.hasPermission('product:brand:update')")
    public CommonResult<Boolean> updateBrand(@Valid @RequestBody ProductBrandUpdateReqVO updateReqVO) {
        brandService.updateBrand(updateReqVO);
        return success(true);
    }

    @DeleteMapping("/delete")
    @Operation(summary = "删除品牌")
    @Parameter(name = "id", description = "编号", required = true, example = "1024")
    @PreAuthorize("@ss.hasPermission('product:brand:delete')")
    public CommonResult<Boolean> deleteBrand(@RequestParam("id") Long id) {
        brandService.deleteBrand(id);
        return success(true);
    }

    @GetMapping("/get")
    @Operation(summary = "获得品牌")
    @Parameter(name = "id", description = "编号", required = true, example = "1024")
    @PreAuthorize("@ss.hasPermission('product:brand:query')")
    public CommonResult<ProductBrandRespVO> getBrand(@RequestParam("id") Long id) {
        ProductBrandDO brand = brandService.getBrand(id);
        ProductBrandRespVO result = ProductBrandConvert.INSTANCE.convert(brand);
        if (result != null) {
            fillMerchantName(result);
        }
        return success(result);
    }

    @GetMapping("/list-all-simple")
    @Operation(summary = "获取品牌精简信息列表", description = "主要用于前端的下拉选项")
    public CommonResult<List<ProductBrandSimpleRespVO>> getSimpleBrandList(
            @RequestParam(value = "merchantId", required = false) Long merchantId) {
        // 获取品牌列表，只要开启状态的
        List<ProductBrandDO> list = brandService.getBrandListByStatus(CommonStatusEnum.ENABLE.getStatus());
        if (merchantId != null) {
            list.removeIf(brand -> !merchantId.equals(brand.getMerchantId()));
        }
        // 排序后，返回给前端
        List<ProductBrandSimpleRespVO> result = ProductBrandConvert.INSTANCE.convertList1(list);
        result.forEach(item -> {
            ProductMerchantDO merchant = item.getMerchantId() == null ? null : merchantService.getMerchant(item.getMerchantId());
            item.setMerchantName(merchant == null ? null : merchant.getStoreName() != null ? merchant.getStoreName() : merchant.getName());
        });
        return success(result);
    }

    @GetMapping("/page")
    @Operation(summary = "获得品牌分页")
    @PreAuthorize("@ss.hasPermission('product:brand:query')")
    public CommonResult<PageResult<ProductBrandRespVO>> getBrandPage(@Valid ProductBrandPageReqVO pageVO) {
        PageResult<ProductBrandDO> pageResult = brandService.getBrandPage(pageVO);
        PageResult<ProductBrandRespVO> result = ProductBrandConvert.INSTANCE.convertPage(pageResult);
        result.getList().forEach(this::fillMerchantName);
        return success(result);
    }

    @GetMapping("/list")
    @Operation(summary = "获得品牌列表")
    @PreAuthorize("@ss.hasPermission('product:brand:query')")
    public CommonResult<List<ProductBrandRespVO>> getBrandList(@Valid ProductBrandListReqVO listVO) {
        List<ProductBrandDO> list = brandService.getBrandList(listVO);
        list.sort(Comparator.comparing(ProductBrandDO::getSort));
        List<ProductBrandRespVO> result = ProductBrandConvert.INSTANCE.convertList(list);
        result.forEach(this::fillMerchantName);
        return success(result);
    }

    private void fillMerchantName(ProductBrandRespVO brand) {
        if (brand.getMerchantId() == null) {
            return;
        }
        ProductMerchantDO merchant = merchantService.getMerchant(brand.getMerchantId());
        if (merchant != null) {
            brand.setMerchantName(merchant.getStoreName() != null ? merchant.getStoreName() : merchant.getName());
        }
    }

}
