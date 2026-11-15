package cn.iocoder.yudao.module.promotion.controller.admin.coupon;
import cn.iocoder.yudao.framework.common.pojo.CommonResult;
import cn.iocoder.yudao.module.promotion.controller.admin.coupon.vo.CouponPromotionCreateReqVO;
import cn.iocoder.yudao.module.promotion.dal.dataobject.coupon.CouponPromotionDO;
import cn.iocoder.yudao.module.promotion.service.coupon.CouponPromotionService;
import org.springframework.web.bind.annotation.*;
import org.springframework.security.access.prepost.PreAuthorize;
import jakarta.annotation.Resource;
import jakarta.validation.Valid;
import static cn.iocoder.yudao.framework.common.pojo.CommonResult.success;
@RestController
@RequestMapping("/promotion/coupon-promotion")
public class CouponPromotionController {
    @Resource private CouponPromotionService service;
    @PostMapping("/create")
    @PreAuthorize("@ss.hasPermission('promotion:coupon-template:query')")
    public CommonResult<CouponPromotionDO> create(@Valid @RequestBody CouponPromotionCreateReqVO req) { return success(service.create(req)); }
}
