package cn.iocoder.yudao.module.trade.controller.admin.logistics;

import cn.iocoder.yudao.framework.common.pojo.CommonResult;
import cn.iocoder.yudao.module.trade.controller.admin.logistics.vo.TradeMerchantLogisticsConfigRespVO;
import cn.iocoder.yudao.module.trade.service.logistics.TradeMerchantLogisticsConfigService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Resource;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import static cn.iocoder.yudao.framework.common.pojo.CommonResult.success;

@Tag(name = "管理后台 - 商家顺丰发货设置状态")
@RestController
@RequestMapping("/trade/logistics/sf/merchant-config")
public class TradeMerchantLogisticsConfigController {

    @Resource
    private TradeMerchantLogisticsConfigService configService;

    @GetMapping("/get")
    @Operation(summary = "查询商家顺丰发货设置状态")
    @PreAuthorize("@ss.hasPermission('trade:logistics:merchant-config:query')")
    public CommonResult<TradeMerchantLogisticsConfigRespVO> get(@RequestParam Long merchantId) {
        return success(configService.get(merchantId));
    }
}
