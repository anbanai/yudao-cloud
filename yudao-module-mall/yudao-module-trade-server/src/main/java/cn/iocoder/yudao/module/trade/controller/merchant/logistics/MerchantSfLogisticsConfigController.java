package cn.iocoder.yudao.module.trade.controller.merchant.logistics;

import cn.iocoder.yudao.framework.common.pojo.CommonResult;
import cn.iocoder.yudao.module.trade.controller.merchant.logistics.vo.MerchantSfLogisticsConfigRespVO;
import cn.iocoder.yudao.module.trade.controller.merchant.logistics.vo.MerchantSfLogisticsConfigSaveReqVO;
import cn.iocoder.yudao.module.trade.framework.merchant.MerchantSecurityService;
import cn.iocoder.yudao.module.trade.service.logistics.TradeMerchantLogisticsConfigService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Resource;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import static cn.iocoder.yudao.framework.common.pojo.CommonResult.success;

@Tag(name = "商家端 - 顺丰月结卡")
@RestController
@RequestMapping("/trade/merchant/logistics/sf-config")
@PreAuthorize("denyAll()")
@Validated
public class MerchantSfLogisticsConfigController {

    @Resource
    private TradeMerchantLogisticsConfigService configService;
    @Resource
    private MerchantSecurityService merchantSecurityService;

    @GetMapping
    @Operation(summary = "查询当前商家顺丰月结卡")
    public CommonResult<MerchantSfLogisticsConfigRespVO> get() {
        return success(configService.getForMerchant(merchantSecurityService.requireOperatingMerchantId()));
    }

    @PutMapping
    @Operation(summary = "更新当前商家顺丰月结卡")
    public CommonResult<Boolean> save(@Valid @RequestBody MerchantSfLogisticsConfigSaveReqVO request) {
        configService.saveForMerchant(merchantSecurityService.requireOperatingMerchantId(), request);
        return success(true);
    }
}
