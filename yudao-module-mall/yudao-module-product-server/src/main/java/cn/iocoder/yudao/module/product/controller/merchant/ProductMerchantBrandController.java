package cn.iocoder.yudao.module.product.controller.merchant;

import cn.iocoder.yudao.framework.common.enums.CommonStatusEnum;
import cn.iocoder.yudao.framework.common.pojo.CommonResult;
import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.module.product.controller.admin.brand.vo.ProductBrandCreateReqVO;
import cn.iocoder.yudao.module.product.controller.admin.brand.vo.ProductBrandListReqVO;
import cn.iocoder.yudao.module.product.controller.admin.brand.vo.ProductBrandPageReqVO;
import cn.iocoder.yudao.module.product.controller.admin.brand.vo.ProductBrandRespVO;
import cn.iocoder.yudao.module.product.controller.admin.brand.vo.ProductBrandSimpleRespVO;
import cn.iocoder.yudao.module.product.controller.admin.brand.vo.ProductBrandUpdateReqVO;
import cn.iocoder.yudao.module.product.convert.brand.ProductBrandConvert;
import cn.iocoder.yudao.module.product.dal.dataobject.brand.ProductBrandDO;
import cn.iocoder.yudao.module.product.dal.dataobject.merchant.ProductMerchantDO;
import cn.iocoder.yudao.module.product.framework.merchant.MerchantSecurityService;
import cn.iocoder.yudao.module.product.service.brand.ProductBrandService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Resource;
import jakarta.validation.Valid;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

import static cn.iocoder.yudao.framework.common.pojo.CommonResult.success;

@Tag(name = "商家端 - 品牌")
@RestController
@RequestMapping("/product/merchant/brand")
@Validated
public class ProductMerchantBrandController {

    @Resource
    private ProductBrandService brandService;
    @Resource
    private MerchantSecurityService merchantSecurityService;

    @PostMapping("/create")
    @Operation(summary = "创建品牌")
    public CommonResult<Long> create(@Valid @RequestBody ProductBrandCreateReqVO reqVO) {
        ProductMerchantDO merchant = merchantSecurityService.requireOperatingMerchant();
        reqVO.setMerchantId(merchant.getId());
        return success(brandService.createBrand(reqVO));
    }

    @PutMapping("/update")
    @Operation(summary = "更新品牌")
    public CommonResult<Boolean> update(@Valid @RequestBody ProductBrandUpdateReqVO reqVO) {
        ProductMerchantDO merchant = merchantSecurityService.requireOperatingMerchant();
        requireOwnedBrand(reqVO.getId(), merchant.getId());
        reqVO.setMerchantId(merchant.getId());
        brandService.updateBrand(reqVO);
        return success(true);
    }

    @DeleteMapping("/delete")
    @Operation(summary = "删除品牌")
    public CommonResult<Boolean> delete(@RequestParam("id") Long id) {
        ProductMerchantDO merchant = merchantSecurityService.requireOperatingMerchant();
        requireOwnedBrand(id, merchant.getId());
        brandService.deleteBrand(id);
        return success(true);
    }

    @GetMapping("/get")
    @Operation(summary = "获得品牌")
    public CommonResult<ProductBrandRespVO> get(@RequestParam("id") Long id) {
        ProductMerchantDO merchant = merchantSecurityService.requireOperatingMerchant();
        ProductBrandDO brand = requireOwnedBrand(id, merchant.getId());
        return success(ProductBrandConvert.INSTANCE.convert(brand));
    }

    @GetMapping("/page")
    @Operation(summary = "获得我的品牌分页")
    public CommonResult<PageResult<ProductBrandRespVO>> getPage(@Valid ProductBrandPageReqVO pageVO) {
        ProductMerchantDO merchant = merchantSecurityService.requireOperatingMerchant();
        pageVO.setMerchantId(merchant.getId());
        PageResult<ProductBrandDO> pageResult = brandService.getBrandPage(pageVO);
        return success(ProductBrandConvert.INSTANCE.convertPage(pageResult));
    }

    @GetMapping("/list-all-simple")
    @Operation(summary = "获得我的启用品牌")
    public CommonResult<List<ProductBrandSimpleRespVO>> getSimpleList() {
        ProductMerchantDO merchant = merchantSecurityService.requireOperatingMerchant();
        List<ProductBrandDO> list = brandService.getBrandListByStatusAndMerchantId(
                CommonStatusEnum.ENABLE.getStatus(), merchant.getId());
        List<ProductBrandSimpleRespVO> result = ProductBrandConvert.INSTANCE.convertList1(list);
        return success(result);
    }

    @GetMapping("/list")
    @Operation(summary = "获得我的品牌列表")
    public CommonResult<List<ProductBrandRespVO>> getList(@Valid ProductBrandListReqVO listVO) {
        ProductMerchantDO merchant = merchantSecurityService.requireOperatingMerchant();
        List<ProductBrandDO> list = brandService.getBrandList(listVO, merchant.getId());
        return success(ProductBrandConvert.INSTANCE.convertList(list));
    }

    private ProductBrandDO requireOwnedBrand(Long brandId, Long merchantId) {
        ProductBrandDO brand = brandService.getBrand(brandId);
        if (brand == null || !merchantId.equals(brand.getMerchantId())) {
            throw new AccessDeniedException("无权访问该品牌");
        }
        return brand;
    }

}
