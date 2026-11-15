package cn.iocoder.yudao.module.promotion.controller.app.coupon;
import cn.iocoder.yudao.framework.common.pojo.CommonResult;
import cn.iocoder.yudao.module.promotion.dal.dataobject.coupon.CouponPromotionDO;
import cn.iocoder.yudao.module.promotion.service.coupon.CouponPromotionService;
import org.springframework.web.bind.annotation.*;
import jakarta.annotation.Resource;
import jakarta.annotation.security.PermitAll;
import java.util.List;
import static cn.iocoder.yudao.framework.common.pojo.CommonResult.success;
import static cn.iocoder.yudao.framework.security.core.util.SecurityFrameworkUtils.getLoginUserId;
@RestController
@RequestMapping("/promotion/coupon-promotion")
public class AppCouponPromotionController {
    @Resource private CouponPromotionService service;
    @GetMapping("/resolve") @PermitAll
    public CommonResult<CouponPromotionDO> resolve(@RequestParam("code") String code) { return success(service.resolve(code)); }
    @PostMapping("/take")
    public CommonResult<List<Long>> take(@RequestParam("code") String code) { return success(service.claim(code, getLoginUserId())); }
}
