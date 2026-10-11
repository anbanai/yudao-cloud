package cn.iocoder.yudao.module.product.controller.merchant;

import cn.iocoder.yudao.framework.common.pojo.CommonResult;
import cn.iocoder.yudao.module.product.controller.admin.merchant.vo.ProductMerchantBusinessTypeRespVO;
import cn.iocoder.yudao.module.product.framework.merchant.MerchantSecurityService;
import cn.iocoder.yudao.module.product.service.merchant.ProductMerchantBusinessTypeService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Resource;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

import static cn.iocoder.yudao.framework.common.pojo.CommonResult.success;

@Tag(name = "商家端 - 经营类型")
@RestController("merchantProductMerchantBusinessTypeController")
@RequestMapping("/product/merchant/business-type")
@Validated
public class ProductMerchantBusinessTypeController {

    @Resource
    private ProductMerchantBusinessTypeService businessTypeService;
    @Resource
    private MerchantSecurityService merchantSecurityService;

    @GetMapping("/list")
    @Operation(summary = "获取可申请经营类型")
    public CommonResult<List<ProductMerchantBusinessTypeRespVO>> getList() {
        merchantSecurityService.requireMerchantAccountId();
        return success(businessTypeService.getList(true));
    }
}
