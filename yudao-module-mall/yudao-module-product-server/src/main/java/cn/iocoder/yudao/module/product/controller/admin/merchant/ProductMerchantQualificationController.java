package cn.iocoder.yudao.module.product.controller.admin.merchant;

import cn.iocoder.yudao.framework.common.pojo.CommonResult;
import cn.iocoder.yudao.module.product.controller.admin.merchant.vo.ProductMerchantQualificationRespVO;
import cn.iocoder.yudao.module.product.convert.merchant.ProductMerchantQualificationConvert;
import cn.iocoder.yudao.module.product.service.merchant.ProductMerchantQualificationService;
import cn.iocoder.yudao.module.infra.api.file.FileApi;
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
    @Resource
    private FileApi fileApi;

    @GetMapping("/list")
    @Operation(summary = "查询商家当前有效资质")
    @PreAuthorize("@ss.hasPermission('product:merchant-qualification:query')")
    public CommonResult<java.util.List<ProductMerchantQualificationRespVO>> getList(@RequestParam("merchantId") Long merchantId) {
        var qualifications = ProductMerchantQualificationConvert.INSTANCE.convertList(qualificationService.getList(merchantId));
        for (ProductMerchantQualificationRespVO qualification : qualifications) {
            if (qualification.getFileId() != null) {
                qualification.setPreviewUrl(fileApi.presignGetUrl(qualification.getFileId(), 900).getCheckedData());
            }
        }
        return success(qualifications);
    }

}
