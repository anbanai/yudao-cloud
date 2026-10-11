package cn.iocoder.yudao.module.product.controller.merchant;

import cn.iocoder.yudao.framework.common.pojo.CommonResult;
import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.framework.common.util.object.BeanUtils;
import cn.iocoder.yudao.module.product.controller.admin.spu.vo.ProductSpuPageReqVO;
import cn.iocoder.yudao.module.product.controller.admin.spu.vo.ProductSpuRespVO;
import cn.iocoder.yudao.module.product.controller.admin.spu.vo.ProductSpuSaveReqVO;
import cn.iocoder.yudao.module.product.controller.admin.spu.vo.ProductSpuSimpleRespVO;
import cn.iocoder.yudao.module.product.controller.admin.spu.vo.ProductSpuUpdateStatusReqVO;
import cn.iocoder.yudao.module.product.controller.app.category.vo.AppCategoryRespVO;
import cn.iocoder.yudao.module.product.convert.spu.ProductSpuConvert;
import cn.iocoder.yudao.module.product.dal.dataobject.category.ProductCategoryDO;
import cn.iocoder.yudao.module.product.dal.dataobject.merchant.ProductMerchantDO;
import cn.iocoder.yudao.module.product.dal.dataobject.sku.ProductSkuDO;
import cn.iocoder.yudao.module.product.dal.dataobject.spu.ProductSpuDO;
import cn.iocoder.yudao.module.product.enums.spu.ProductSpuStatusEnum;
import cn.iocoder.yudao.module.product.framework.merchant.MerchantSecurityService;
import cn.iocoder.yudao.module.product.service.group.ProductGroupService;
import cn.iocoder.yudao.module.product.service.category.ProductCategoryService;
import cn.iocoder.yudao.module.product.service.sku.ProductSkuService;
import cn.iocoder.yudao.module.product.service.spu.ProductSpuService;
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

import java.util.Comparator;
import java.util.List;

import static cn.iocoder.yudao.framework.common.pojo.CommonResult.success;

@Tag(name = "商家端 - 商品")
@RestController
@RequestMapping("/product/merchant/spu")
@Validated
public class ProductMerchantSpuController {

    @Resource
    private ProductSpuService productSpuService;
    @Resource
    private ProductSkuService productSkuService;
    @Resource
    private ProductGroupService productGroupService;
    @Resource
    private ProductCategoryService productCategoryService;
    @Resource
    private MerchantSecurityService merchantSecurityService;

    @GetMapping("/category-list")
    @Operation(summary = "获得可用于商品的启用分类")
    public CommonResult<List<AppCategoryRespVO>> getCategoryList() {
        merchantSecurityService.requireMerchantAccountId();
        List<ProductCategoryDO> categories = productCategoryService.getEnableCategoryList().stream()
                .filter(category -> productCategoryService.getCategoryLevel(category.getId())
                        >= ProductCategoryDO.CATEGORY_LEVEL)
                .sorted(Comparator.comparing(ProductCategoryDO::getSort))
                .toList();
        return success(BeanUtils.toBean(categories, AppCategoryRespVO.class));
    }

    @PostMapping("/create")
    @Operation(summary = "创建商品")
    public CommonResult<Long> create(@Valid @RequestBody ProductSpuSaveReqVO reqVO) {
        ProductMerchantDO merchant = merchantSecurityService.requireOperatingMerchant();
        reqVO.setMerchantId(merchant.getId());
        return success(productSpuService.createSpu(reqVO));
    }

    @PutMapping("/update")
    @Operation(summary = "更新商品")
    public CommonResult<Boolean> update(@Valid @RequestBody ProductSpuSaveReqVO reqVO) {
        ProductMerchantDO merchant = merchantSecurityService.requireOperatingMerchant();
        requireOwnedSpu(reqVO.getId(), merchant.getId());
        reqVO.setMerchantId(merchant.getId());
        productSpuService.updateSpu(reqVO);
        return success(true);
    }

    @PutMapping("/update-status")
    @Operation(summary = "更新商品状态")
    public CommonResult<Boolean> updateStatus(@Valid @RequestBody ProductSpuUpdateStatusReqVO reqVO) {
        ProductMerchantDO merchant = merchantSecurityService.requireOperatingMerchant();
        requireOwnedSpu(reqVO.getId(), merchant.getId());
        productSpuService.updateSpuStatus(reqVO);
        return success(true);
    }

    @DeleteMapping("/delete")
    @Operation(summary = "删除商品")
    public CommonResult<Boolean> delete(@RequestParam("id") Long id) {
        ProductMerchantDO merchant = merchantSecurityService.requireOperatingMerchant();
        requireOwnedSpu(id, merchant.getId());
        productSpuService.deleteSpu(id);
        return success(true);
    }

    @GetMapping("/get-detail")
    @Operation(summary = "获得商品详情")
    public CommonResult<ProductSpuRespVO> getDetail(@RequestParam("id") Long id) {
        ProductMerchantDO merchant = merchantSecurityService.requireOperatingMerchant();
        ProductSpuDO spu = requireOwnedSpu(id, merchant.getId());
        List<ProductSkuDO> skus = productSkuService.getSkuListBySpuId(spu.getId());
        ProductSpuRespVO result = ProductSpuConvert.INSTANCE.convert(spu, skus);
        result.setGroupIds(productGroupService.getGroupIdsBySpuId(spu.getId()));
        result.setMerchantName(merchant.getStoreName() != null ? merchant.getStoreName() : merchant.getName());
        return success(result);
    }

    @GetMapping("/page")
    @Operation(summary = "获得我的商品分页")
    public CommonResult<PageResult<ProductSpuRespVO>> getPage(@Valid ProductSpuPageReqVO pageVO) {
        ProductMerchantDO merchant = merchantSecurityService.requireOperatingMerchant();
        pageVO.setMerchantId(merchant.getId());
        PageResult<ProductSpuDO> pageResult = productSpuService.getSpuPage(pageVO);
        PageResult<ProductSpuRespVO> result = BeanUtils.toBean(pageResult, ProductSpuRespVO.class);
        String merchantName = merchant.getStoreName() != null ? merchant.getStoreName() : merchant.getName();
        result.getList().forEach(item -> item.setMerchantName(merchantName));
        return success(result);
    }

    @GetMapping("/list-all-simple")
    @Operation(summary = "获得我的商品精简列表")
    public CommonResult<List<ProductSpuSimpleRespVO>> getSimpleList() {
        ProductMerchantDO merchant = merchantSecurityService.requireOperatingMerchant();
        List<ProductSpuDO> list = productSpuService.getSpuListByStatusAndMerchantId(
                ProductSpuStatusEnum.ENABLE.getStatus(), merchant.getId());
        return success(BeanUtils.toBean(list, ProductSpuSimpleRespVO.class));
    }

    private ProductSpuDO requireOwnedSpu(Long spuId, Long merchantId) {
        ProductSpuDO spu = productSpuService.getSpu(spuId);
        if (spu == null || !merchantId.equals(spu.getMerchantId())) {
            throw new AccessDeniedException("无权访问该商品");
        }
        return spu;
    }

}
