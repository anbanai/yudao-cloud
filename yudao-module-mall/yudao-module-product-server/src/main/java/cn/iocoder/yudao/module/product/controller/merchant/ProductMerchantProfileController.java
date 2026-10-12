package cn.iocoder.yudao.module.product.controller.merchant;

import cn.iocoder.yudao.framework.common.pojo.CommonResult;
import cn.iocoder.yudao.framework.common.util.object.BeanUtils;
import cn.iocoder.yudao.module.product.controller.merchant.vo.ProductMerchantStoreProfileReqVO;
import cn.iocoder.yudao.module.product.controller.merchant.vo.ProductMerchantStoreProfileRespVO;
import cn.iocoder.yudao.module.product.dal.dataobject.merchant.ProductMerchantDO;
import cn.iocoder.yudao.module.product.framework.merchant.MerchantSecurityService;
import cn.iocoder.yudao.module.product.service.merchant.ProductMerchantService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Resource;
import jakarta.validation.Valid;
import org.springframework.validation.annotation.Validated;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import static cn.iocoder.yudao.framework.common.pojo.CommonResult.success;

@Tag(name = "商家端 - 店铺资料")
@RestController
@RequestMapping("/product/merchant/profile")
@PreAuthorize("denyAll()")
@Validated
public class ProductMerchantProfileController {

    @Resource
    private ProductMerchantService merchantService;
    @Resource
    private MerchantSecurityService merchantSecurityService;

    @GetMapping("/get")
    @Operation(summary = "获取店铺展示资料")
    public CommonResult<ProductMerchantStoreProfileRespVO> getProfile() {
        ProductMerchantDO merchant = merchantSecurityService.requireBoundMerchant();
        ProductMerchantStoreProfileRespVO resp = BeanUtils.toBean(merchant, ProductMerchantStoreProfileRespVO.class);
        resp.setMerchantId(merchant.getId());
        return success(resp);
    }

    @PutMapping("/update")
    @Operation(summary = "更新店铺展示资料")
    public CommonResult<Boolean> updateProfile(@Valid @RequestBody ProductMerchantStoreProfileReqVO reqVO) {
        ProductMerchantDO merchant = merchantSecurityService.requireBoundMerchant();
        merchantService.updateStoreProfile(merchant.getId(), reqVO.getStoreName(), reqVO.getLogoUrl(),
                reqVO.getDescription());
        return success(true);
    }
}
